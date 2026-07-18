// Thread-safe task state machine.
//
// Enforces the legal transition graph documented in core/RageTypes.h.
// All public methods are thread-safe (acquires internal mutex).
#pragma once

#include "core/RageTypes.h"

#include <atomic>
#include <mutex>
#include <string>
#include <unordered_map>

namespace rage::native {

class TaskStateMachine {
public:
    TaskStateMachine() = default;
    ~TaskStateMachine() = default;

    // Record a transition for `taskId` to `newStatus`. Returns true if legal.
    // Logs every transition (INFO) and every rejected transition (WARN) via __android_log.
    bool transition(const std::string& taskId, TaskStatus newStatus);

    // True iff `from -> to` is a legal edge in the state graph.
    static bool canTransition(TaskStatus from, TaskStatus to);

    // Current status of a task (PENDING if unknown).
    TaskStatus current(const std::string& taskId) const;

    // Reset all state (used by tests / global teardown).
    void clear();

private:
    mutable std::mutex                              mutex_;
    std::unordered_map<std::string, TaskStatus>     states_;
};

} // namespace rage::native
