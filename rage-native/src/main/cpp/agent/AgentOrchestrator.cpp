#include "agent/AgentOrchestrator.h"

#include "agent/CriticAgent.h"
#include "agent/ExecutorAgent.h"
#include "agent/PlannerAgent.h"
#include "agent/SearcherAgent.h"
#include "core/Blackboard.h"
#include "core/MetricsCollector.h"
#include "core/ParallelScheduler.h"
#include "core/TaskStateMachine.h"

#include <android/log.h>
#include <chrono>
#include <future>
#include <sstream>
#include <vector>
#include <cctype>

namespace rage::native {

namespace {
constexpr const char* TAG = "RageOrch";

std::string nowMillis() {
    using namespace std::chrono;
    return std::to_string(
        duration_cast<milliseconds>(system_clock::now().time_since_epoch()).count());
}

// Strip leading "1. ", "2) ", "- ", "* " prefixes from a line.
std::string stripBullet(const std::string& line) {
    size_t i = 0;
    while (i < line.size() && (line[i] == ' ' || line[i] == '\t')) ++i;
    if (i >= line.size()) return "";
    // numbered: "1." / "1)" / "12." / "12)"
    if (i < line.size() && std::isdigit(static_cast<unsigned char>(line[i]))) {
        size_t j = i;
        while (j < line.size() && std::isdigit(static_cast<unsigned char>(line[j]))) ++j;
        if (j < line.size() && (line[j] == '.' || line[j] == ')')) {
            ++j;
            while (j < line.size() && (line[j] == ' ' || line[j] == '\t')) ++j;
            return line.substr(j);
        }
    }
    // "-" or "*" bullet
    if ((line[i] == '-' || line[i] == '*') &&
        i + 1 < line.size() && (line[i + 1] == ' ' || line[i + 1] == '\t')) {
        i += 2;
        while (i < line.size() && (line[i] == ' ' || line[i] == '\t')) ++i;
        return line.substr(i);
    }
    return line.substr(i);
}
} // namespace

AgentOrchestrator::AgentOrchestrator(ParallelScheduler& scheduler,
                                     Blackboard& blackboard,
                                     MetricsCollector& metrics,
                                     TaskStateMachine& fsm)
    : scheduler_(scheduler)
    , blackboard_(blackboard)
    , metrics_(metrics)
    , fsm_(fsm) {}

AgentOrchestrator::~AgentOrchestrator() = default;

void AgentOrchestrator::requestCancel(const std::string& taskId) {
    std::lock_guard<std::mutex> lk(cancelMutex_);
    auto it = cancelFlags_.find(taskId);
    if (it == cancelFlags_.end()) return;
    it->second.store(true, std::memory_order_release);
}

bool AgentOrchestrator::isCancelled(const std::string& taskId) const {
    std::lock_guard<std::mutex> lk(cancelMutex_);
    auto it = cancelFlags_.find(taskId);
    if (it == cancelFlags_.end()) return false;
    return it->second.load(std::memory_order_acquire);
}

void AgentOrchestrator::emitEvent(const NativeEvent& ev, const AgentInvoker& invoker) {
    try {
        invoker(ev);
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
                            "event callback threw (continuing): %s", e.what());
    } catch (...) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
                            "event callback threw unknown exception (continuing)");
    }
}

std::vector<NativeSubtask> AgentOrchestrator::parseSubtasks(const std::string& response,
                                                            const std::string& taskIdPrefix) {
    std::vector<NativeSubtask> out;
    std::istringstream iss(response);
    std::string line;
    int idx = 1;
    while (std::getline(iss, line)) {
        std::string stripped = stripBullet(line);
        // Trim trailing whitespace.
        while (!stripped.empty() &&
               (stripped.back() == '\r' || stripped.back() == '\n' ||
                stripped.back() == ' '  || stripped.back() == '\t')) {
            stripped.pop_back();
        }
        if (stripped.empty()) continue;
        if (stripped.size() > 500) stripped.resize(500);
        NativeSubtask st;
        st.id          = taskIdPrefix + "-sub-" + std::to_string(idx);
        st.description = stripped;
        st.status      = "pending";
        st.output      = "";
        out.push_back(std::move(st));
        ++idx;
        if (out.size() >= 7) break;  // cap at 7 per Planner prompt contract
    }
    if (out.empty()) {
        // Fallback: treat the whole response as a single subtask.
        NativeSubtask st;
        st.id          = taskIdPrefix + "-sub-1";
        st.description = response.empty() ? "(no plan)" : response.substr(0, 200);
        st.status      = "pending";
        out.push_back(std::move(st));
    }
    return out;
}

NativeExecutionResult AgentOrchestrator::executeTask(const NativeTask& task,
                                                     const NativeRageConfig& config,
                                                     const AgentInvoker& invoker) {
    const auto t0 = std::chrono::steady_clock::now();
    const std::string& taskId = task.id;

    // Register cancellation flag for this task.
    {
        std::lock_guard<std::mutex> lk(cancelMutex_);
        cancelFlags_[taskId] = false;
    }

    metrics_.recordTaskStart();
    fsm_.transition(taskId, TaskStatus::RUNNING);

    NativeExecutionResult result;
    result.taskId = taskId;

    blackboard_.clear();
    blackboard_.put("task",   task.description);
    blackboard_.put("preset", task.preset);

    // TASK_STARTED event.
    {
        NativeEvent ev;
        ev.type    = EventType::TASK_STARTED;
        ev.taskId  = taskId;
        ev.message = task.description;
        emitEvent(ev, invoker);
    }

    if (isCancelled(taskId)) {
        fsm_.transition(taskId, TaskStatus::CANCELLED);
        metrics_.recordTaskCancel();
        result.success      = false;
        result.errorMessage = "cancelled before planner";
        return result;
    }

    // ----- 1. Planner ----------------------------------------------------
    PlannerAgent planner;
    {
        NativeEvent ev;
        ev.type      = EventType::TASK_PROGRESS;
        ev.taskId    = taskId;
        ev.progress  = 0.1f;
        ev.message   = "Planner decomposing task";
        ev.agentId   = planner.id();
        ev.agentName = planner.name();
        ev.action    = "create_task_plan(dag)";
        emitEvent(ev, invoker);
    }
    NativeSubtask plannerInput;
    plannerInput.id          = taskId + "-planner";
    plannerInput.description = task.description;
    AgentResult planResult = planner.execute(plannerInput, blackboard_, invoker);
    {
        NativeEvent ev;
        ev.type      = EventType::AGENT_STEP;
        ev.taskId    = taskId;
        ev.agentId   = planner.id();
        ev.agentName = planner.name();
        ev.action    = planResult.action;
        ev.success   = planResult.success;
        ev.message   = planResult.thought;
        emitEvent(ev, invoker);
    }

    std::vector<NativeSubtask> subtasks = parseSubtasks(planResult.output, taskId);

    // Persist subtask list to blackboard for the Critic.
    {
        std::ostringstream oss;
        for (size_t i = 0; i < subtasks.size(); ++i) {
            oss << (i + 1) << ". " << subtasks[i].description << "\n";
        }
        blackboard_.put("subtasks_joined", oss.str());
    }

    if (isCancelled(taskId)) {
        fsm_.transition(taskId, TaskStatus::CANCELLED);
        metrics_.recordTaskCancel();
        result.success      = false;
        result.errorMessage = "cancelled after planner";
        return result;
    }

    // ----- 2. Searcher (best-effort, single aggregate query) -------------
    {
        NativeEvent ev;
        ev.type      = EventType::TASK_PROGRESS;
        ev.taskId    = taskId;
        ev.progress  = 0.3f;
        ev.message   = "Searcher retrieving context";
        ev.agentId   = "searcher";
        ev.agentName = "Searcher";
        ev.action    = "rag_search";
        emitEvent(ev, invoker);
    }
    SearcherAgent searcher;
    NativeSubtask searchInput;
    searchInput.id          = taskId + "-searcher";
    searchInput.description = task.description;
    try {
        AgentResult sr = searcher.execute(searchInput, blackboard_, invoker);
        NativeEvent ev;
        ev.type      = EventType::AGENT_STEP;
        ev.taskId    = taskId;
        ev.agentId   = searcher.id();
        ev.agentName = searcher.name();
        ev.action    = sr.action;
        ev.success   = sr.success;
        ev.message   = sr.thought;
        emitEvent(ev, invoker);
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
                            "searcher threw (continuing): %s", e.what());
    }

    // ----- 3 + 4. Executor + Critic loop ---------------------------------
    ExecutorAgent executor;
    CriticAgent   critic;
    int  retryCount = 0;
    bool passed     = false;
    std::vector<std::string> implementations;
    std::string lastFeedback;

    while (!passed && retryCount < config.maxRetries) {
        if (isCancelled(taskId)) break;
        ++retryCount;
        {
            NativeEvent ev;
            ev.type    = EventType::TASK_PROGRESS;
            ev.taskId  = taskId;
            ev.progress = 0.4f + 0.1f * static_cast<float>(retryCount);
            ev.message  = "Executor implementing (attempt "
                        + std::to_string(retryCount) + "/"
                        + std::to_string(config.maxRetries) + ")";
            ev.agentId  = executor.id();
            ev.agentName = executor.name();
            ev.action    = "edit_file(diff)";
            emitEvent(ev, invoker);
        }

        // Seed critic feedback into blackboard for the executor.
        blackboard_.put("critic_feedback", lastFeedback);

        // Submit each subtask to the scheduler; gather futures.
        implementations.clear();
        implementations.reserve(subtasks.size());
        std::vector<std::future<AgentResult>> futures;
        futures.reserve(subtasks.size());
        for (size_t i = 0; i < subtasks.size(); ++i) {
            NativeSubtask st = subtasks[i];
            st.status = "running";
            subtasks[i].status = "running";
            // Priority: smaller index = slightly higher priority (FIFO-ish),
            // but still parallelizable.
            int prio = static_cast<int>(subtasks.size() - i);
            futures.push_back(scheduler_.submit(prio, [this, st, &executor, &invoker]() {
                return executor.execute(st, blackboard_, invoker);
            }));
            metrics_.recordConcurrency(scheduler_.activeCount());
        }
        for (size_t i = 0; i < futures.size(); ++i) {
            AgentResult er;
            try {
                er = futures[i].get();
            } catch (const std::exception& e) {
                er.success      = false;
                er.errorMessage = e.what();
                er.output       = "<executor future threw>";
                er.action       = "implement_failed";
            }
            subtasks[i].status = er.success ? "done" : "failed";
            subtasks[i].output = er.output;
            implementations.push_back(er.output);
            NativeEvent ev;
            ev.type      = EventType::AGENT_STEP;
            ev.taskId    = taskId;
            ev.agentId   = executor.id();
            ev.agentName = executor.name();
            ev.action    = er.action;
            ev.success   = er.success;
            ev.message   = er.thought + " (subtask " + std::to_string(i + 1) + "/"
                         + std::to_string(subtasks.size()) + ")";
            emitEvent(ev, invoker);
            {
                NativeEvent pev;
                pev.type    = EventType::TASK_PROGRESS;
                pev.taskId  = taskId;
                pev.progress = 0.5f + 0.05f * static_cast<float>(i + 1)
                             + 0.1f * static_cast<float>(retryCount - 1);
                pev.message = "Subtask " + std::to_string(i + 1) + "/"
                            + std::to_string(subtasks.size()) + " done";
                emitEvent(pev, invoker);
            }
        }

        if (isCancelled(taskId)) break;

        // Critic: review combined implementation.
        std::string combined;
        combined.reserve(1024);
        for (size_t i = 0; i < implementations.size(); ++i) {
            combined += "### Subtask " + std::to_string(i + 1) + ": "
                     +  subtasks[i].description + "\n";
            combined += implementations[i];
            combined += "\n\n";
        }
        NativeSubtask criticInput;
        criticInput.id          = taskId + "-critic-v" + std::to_string(retryCount);
        criticInput.description = combined;
        {
            NativeEvent ev;
            ev.type    = EventType::TASK_PROGRESS;
            ev.taskId  = taskId;
            ev.progress = 0.8f;
            ev.message  = "Critic reviewing (attempt "
                        + std::to_string(retryCount) + ")";
            ev.agentId  = critic.id();
            ev.agentName = critic.name();
            ev.action    = "review";
            emitEvent(ev, invoker);
        }
        AgentResult cr = critic.execute(criticInput, blackboard_, invoker);
        {
            NativeEvent ev;
            ev.type      = EventType::AGENT_STEP;
            ev.taskId    = taskId;
            ev.agentId   = critic.id();
            ev.agentName = critic.name();
            ev.action    = cr.action;
            ev.success   = cr.success;
            ev.message   = cr.thought;
            emitEvent(ev, invoker);
        }
        if (cr.success) {
            passed = true;
        } else {
            lastFeedback = cr.output;
            __android_log_print(ANDROID_LOG_INFO, TAG,
                                "critic rejected attempt %d — will retry",
                                retryCount);
        }
    }

    // ----- Build final result --------------------------------------------
    int agentInvocations = 1 /*planner*/ + 1 /*searcher*/
                         + retryCount * static_cast<int>(subtasks.size()) /*executor*/
                         + retryCount /*critic*/;

    result.subtasks          = subtasks;
    result.agentInvocations  = agentInvocations;
    result.retryCount        = retryCount;
    result.success           = passed;

    if (passed) {
        std::string finalOut;
        for (size_t i = 0; i < implementations.size(); ++i) {
            finalOut += "### Subtask " + std::to_string(i + 1) + ": "
                     +  subtasks[i].description + "\n";
            finalOut += implementations[i];
            finalOut += "\n\n";
        }
        result.finalOutput = finalOut;
    } else {
        result.errorMessage = isCancelled(taskId)
            ? "cancelled"
            : "max retries (" + std::to_string(config.maxRetries) + ") exhausted";
    }

    const auto t1 = std::chrono::steady_clock::now();
    result.durationMs = std::chrono::duration_cast<std::chrono::milliseconds>(t1 - t0).count();
    metrics_.recordDuration(result.durationMs);

    // Final state transition + event.
    if (isCancelled(taskId)) {
        fsm_.transition(taskId, TaskStatus::CANCELLED);
        metrics_.recordTaskCancel();
    } else if (passed) {
        fsm_.transition(taskId, TaskStatus::COMPLETED);
        metrics_.recordTaskComplete(true);
    } else {
        fsm_.transition(taskId, TaskStatus::FAILED);
        metrics_.recordTaskComplete(false);
    }

    {
        NativeEvent ev;
        ev.type     = passed ? EventType::TASK_COMPLETED : EventType::TASK_FAILED;
        ev.taskId   = taskId;
        ev.progress = passed ? 1.0f : 0.0f;
        ev.success  = passed;
        ev.message  = passed
            ? ("Task completed in " + std::to_string(result.durationMs) + " ms")
            : ("Task failed: " + result.errorMessage);
        emitEvent(ev, invoker);
    }

    // Unregister cancellation flag (best-effort; safe to leave).
    {
        std::lock_guard<std::mutex> lk(cancelMutex_);
        cancelFlags_.erase(taskId);
    }
    return result;
}

} // namespace rage::native
