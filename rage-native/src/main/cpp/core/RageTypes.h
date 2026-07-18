// Copyright (c) Apex AI Agent. Rage Native Core — shared C++ types.
//
// Mirrors the Kotlin data models in lib/rage/src/main/java/com/apex/lib/rage/RageModels.kt.
// All cross-JNI payloads are JSON-serialized forms of these structs (see util/JsonSerializer).
//
// IMPORTANT: this header is included pervasively across the rage-native core/agent/skill/cache/jni
// subsystems — keep it dependency-light (only <string>, <vector>, <cstdint>).
#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace rage::native {

// ============================================================
// Task state machine
// ============================================================
//
// Valid transitions (enforced by core/TaskStateMachine):
//   PENDING   -> RUNNING
//   RUNNING   -> COMPLETED | FAILED | CANCELLED
//   FAILED    -> RUNNING   (retry)
//   COMPLETED -> (terminal)
//   CANCELLED -> (terminal)
enum class TaskStatus : int {
    PENDING    = 0,
    RUNNING    = 1,
    COMPLETED  = 2,
    FAILED     = 3,
    CANCELLED  = 4,
};

const char* taskStatusToString(TaskStatus s);
TaskStatus  taskStatusFromString(const std::string& s);

// ============================================================
// Core entities (mirror Kotlin RageModels.kt)
// ============================================================

struct NativeSubtask {
    std::string id;           // "sub-1", "sub-2", ...
    std::string description;  // human-readable subtask text
    std::string status;       // "pending" | "running" | "done" | "failed" | "skipped"
    std::string output;       // executor-produced implementation text
};

struct NativeTask {
    std::string  id;
    std::string  description;
    std::string  preset;          // "AGGRESSIVE" | "BALANCED" | "CONSERVATIVE" | "DEBUG"
    TaskStatus   status = TaskStatus::PENDING;
    int64_t      createdAt = 0;   // epoch millis
    int64_t      startedAt = 0;
    int64_t      completedAt = 0;
    float        progress = 0.0f; // [0.0, 1.0]
    std::string  result;
    std::string  errorMessage;
    int          agentInvocations = 0;
    int          retryCount = 0;
    int64_t      durationMs = 0;
};

struct NativeExecutionResult {
    bool                              success = false;
    std::string                       errorMessage;
    std::vector<NativeSubtask>        subtasks;
    int                               agentInvocations = 0;
    int                               retryCount = 0;
    int64_t                           durationMs = 0;
    std::string                       finalOutput;
    std::string                       taskId;          // echoed back for correlation
};

struct NativeRageConfig {
    int       maxConcurrency = 4;
    int64_t   defaultTimeoutMs = 60'000;
    int       maxRetries = 3;
    bool      enableAutoExpand = true;
    bool      enableGitBranching = true;
    bool      enableSandboxExec = true;
    bool      enableGithubSearch = false;
    bool      enableCodeRag = true;
};

struct NativeMetrics {
    int64_t   totalTasks = 0;
    int64_t   successfulTasks = 0;
    int64_t   failedTasks = 0;
    int64_t   cancelledTasks = 0;
    double    averageExecutionTimeMs = 0.0;
    double    successRate = 0.0;
    int       currentConcurrency = 0;
    int       peakConcurrency = 0;
};

// ============================================================
// Event flow (C++ -> Kotlin via NativeCallbacks.onEvent)
// ============================================================
//
// EventType selects which optional fields of NativeEvent are populated.
// - LLM_REQUEST:     llmPrompt + llmSystemPrompt populated; callback returns response in .message
// - SEARCH_REQUEST:  searchQuery populated; callback returns results in .message
// - TASK_*:          taskId + progress + message populated (observation only — return value ignored)
// - SKILL_INVOKED:   skillId + skillName populated
// - AGENT_STEP:      agentId + agentName + action + success populated
// - BLACKBOARD_UPDATED: message holds a small JSON snapshot (observation only)
enum class EventType : int {
    TASK_STARTED       = 0,
    TASK_PROGRESS      = 1,
    TASK_COMPLETED     = 2,
    TASK_FAILED        = 3,
    SKILL_INVOKED      = 4,
    AGENT_STEP         = 5,
    BLACKBOARD_UPDATED = 6,
    LLM_REQUEST        = 7,
    SEARCH_REQUEST     = 8,
};

const char* eventTypeToString(EventType t);
EventType   eventTypeFromString(const std::string& s);

struct NativeEvent {
    EventType   type = EventType::TASK_PROGRESS;
    std::string taskId;
    float       progress = 0.0f;
    std::string message;
    std::string agentId;
    std::string agentName;
    std::string action;
    bool        success = false;
    std::string skillId;
    std::string skillName;
    std::string llmPrompt;
    std::string llmSystemPrompt;
    std::string searchQuery;
};

} // namespace rage::native
