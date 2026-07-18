// Agent orchestrator — the Planner -> Searcher -> Executor -> Critic loop.
//
// This is the compute core of rage. It is invoked synchronously by the JNI
// layer; the calling (Kotlin-side) thread blocks until completion. All LLM
// and search calls go through the `AgentInvoker` callback (which routes via
// JNI to Kotlin's NativeCallbacks).
//
// The orchestrator:
//   1. Plans: asks the Planner to decompose the task.
//   2. Searches: best-effort search via Searcher (failure non-fatal).
//   3. Executes: runs each subtask through Executor (in parallel where the
//      scheduler permits; respects config.maxConcurrency).
//   4. Critiques: asks the Critic to PASS/FAIL the combined implementation.
//   5. Retries: on FAIL, feeds critic feedback back to Executor (up to
//      config.maxRetries).
//
// Emits TASK_STARTED / TASK_PROGRESS / AGENT_STEP / TASK_COMPLETED events.
// Honors cancellation: if the JNI layer sets the cancel flag for this task,
// the orchestrator returns a CANCELLED result at the next safe point.
#pragma once

#include "agent/IAgent.h"
#include "core/RageTypes.h"

#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>

namespace rage::native {

class ParallelScheduler;
class Blackboard;
class MetricsCollector;
class TaskStateMachine;

class AgentOrchestrator {
public:
    AgentOrchestrator(ParallelScheduler& scheduler,
                      Blackboard& blackboard,
                      MetricsCollector& metrics,
                      TaskStateMachine& fsm);
    ~AgentOrchestrator();

    AgentOrchestrator(const AgentOrchestrator&) = delete;
    AgentOrchestrator& operator=(const AgentOrchestrator&) = delete;

    // Synchronous: blocks until completion (or cancellation). The caller (JNI)
    // is expected to be a worker thread.
    NativeExecutionResult executeTask(const NativeTask& task,
                                      const NativeRageConfig& config,
                                      const AgentInvoker& invoker);

    // Cancellation — called from JNI on a different thread.
    void requestCancel(const std::string& taskId);

private:
    // Parse the Planner LLM response into subtask entries (defensive).
    std::vector<NativeSubtask> parseSubtasks(const std::string& response,
                                             const std::string& taskIdPrefix);

    // Emit an observation event via the invoker. Non-fatal if invoker throws.
    void emitEvent(const NativeEvent& ev, const AgentInvoker& invoker);

    // Check cancellation flag for `taskId`.
    bool isCancelled(const std::string& taskId) const;

    ParallelScheduler&                   scheduler_;
    Blackboard&                          blackboard_;
    MetricsCollector&                    metrics_;
    TaskStateMachine&                    fsm_;

    // Cancellation flags (taskId -> cancelled). Set from JNI thread.
    mutable std::mutex                                 cancelMutex_;
    std::unordered_map<std::string, std::atomic<bool>> cancelFlags_;
};

} // namespace rage::native
