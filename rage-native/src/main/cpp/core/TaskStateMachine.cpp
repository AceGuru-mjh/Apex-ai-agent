#include "core/TaskStateMachine.h"

#include <android/log.h>

namespace rage::native {

namespace {
constexpr const char* TAG = "RageFSM";

const char* statusLabel(TaskStatus s) {
    switch (s) {
        case TaskStatus::PENDING:   return "PENDING";
        case TaskStatus::RUNNING:   return "RUNNING";
        case TaskStatus::COMPLETED: return "COMPLETED";
        case TaskStatus::FAILED:    return "FAILED";
        case TaskStatus::CANCELLED: return "CANCELLED";
    }
    return "UNKNOWN";
}
} // namespace

const char* taskStatusToString(TaskStatus s) {
    switch (s) {
        case TaskStatus::PENDING:   return "PENDING";
        case TaskStatus::RUNNING:   return "RUNNING";
        case TaskStatus::COMPLETED: return "COMPLETED";
        case TaskStatus::FAILED:    return "FAILED";
        case TaskStatus::CANCELLED: return "CANCELLED";
    }
    return "UNKNOWN";
}

TaskStatus taskStatusFromString(const std::string& s) {
    if (s == "PENDING")   return TaskStatus::PENDING;
    if (s == "RUNNING")   return TaskStatus::RUNNING;
    if (s == "COMPLETED") return TaskStatus::COMPLETED;
    if (s == "FAILED")    return TaskStatus::FAILED;
    if (s == "CANCELLED") return TaskStatus::CANCELLED;
    return TaskStatus::PENDING;
}

bool TaskStateMachine::canTransition(TaskStatus from, TaskStatus to) {
    if (from == to) return false;
    switch (from) {
        case TaskStatus::PENDING:
            return to == TaskStatus::RUNNING ||
                   to == TaskStatus::CANCELLED;
        case TaskStatus::RUNNING:
            return to == TaskStatus::COMPLETED ||
                   to == TaskStatus::FAILED    ||
                   to == TaskStatus::CANCELLED;
        case TaskStatus::FAILED:
            return to == TaskStatus::RUNNING;  // retry
        case TaskStatus::COMPLETED:
            return false;  // terminal
        case TaskStatus::CANCELLED:
            return false;  // terminal
    }
    return false;
}

bool TaskStateMachine::transition(const std::string& taskId, TaskStatus newStatus) {
    std::lock_guard<std::mutex> lk(mutex_);
    auto it = states_.find(taskId);
    TaskStatus from = (it == states_.end()) ? TaskStatus::PENDING : it->second;
    if (!canTransition(from, newStatus)) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
                            "rejected transition task=%s %s -> %s",
                            taskId.c_str(), statusLabel(from), statusLabel(newStatus));
        return false;
    }
    states_[taskId] = newStatus;
    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "transition task=%s %s -> %s",
                        taskId.c_str(), statusLabel(from), statusLabel(newStatus));
    return true;
}

TaskStatus TaskStateMachine::current(const std::string& taskId) const {
    std::lock_guard<std::mutex> lk(mutex_);
    auto it = states_.find(taskId);
    return it == states_.end() ? TaskStatus::PENDING : it->second;
}

void TaskStateMachine::clear() {
    std::lock_guard<std::mutex> lk(mutex_);
    states_.clear();
}

} // namespace rage::native

// ============================================================
// EventType string conversion (declared in RageTypes.h)
// ============================================================
namespace rage::native {

const char* eventTypeToString(EventType t) {
    switch (t) {
        case EventType::TASK_STARTED:       return "TASK_STARTED";
        case EventType::TASK_PROGRESS:      return "TASK_PROGRESS";
        case EventType::TASK_COMPLETED:     return "TASK_COMPLETED";
        case EventType::TASK_FAILED:        return "TASK_FAILED";
        case EventType::SKILL_INVOKED:      return "SKILL_INVOKED";
        case EventType::AGENT_STEP:         return "AGENT_STEP";
        case EventType::BLACKBOARD_UPDATED: return "BLACKBOARD_UPDATED";
        case EventType::LLM_REQUEST:        return "LLM_REQUEST";
        case EventType::SEARCH_REQUEST:     return "SEARCH_REQUEST";
    }
    return "UNKNOWN";
}

EventType eventTypeFromString(const std::string& s) {
    if (s == "TASK_STARTED")       return EventType::TASK_STARTED;
    if (s == "TASK_PROGRESS")      return EventType::TASK_PROGRESS;
    if (s == "TASK_COMPLETED")     return EventType::TASK_COMPLETED;
    if (s == "TASK_FAILED")        return EventType::TASK_FAILED;
    if (s == "SKILL_INVOKED")      return EventType::SKILL_INVOKED;
    if (s == "AGENT_STEP")         return EventType::AGENT_STEP;
    if (s == "BLACKBOARD_UPDATED") return EventType::BLACKBOARD_UPDATED;
    if (s == "LLM_REQUEST")        return EventType::LLM_REQUEST;
    if (s == "SEARCH_REQUEST")     return EventType::SEARCH_REQUEST;
    return EventType::TASK_PROGRESS;
}

} // namespace rage::native
