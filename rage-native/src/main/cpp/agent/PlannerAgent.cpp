#include "agent/PlannerAgent.h"

#include "core/Blackboard.h"

#include <android/log.h>

namespace rage::native {

namespace {
constexpr const char* TAG = "RagePlanner";
}

AgentResult PlannerAgent::execute(const NativeSubtask& subtask,
                                  Blackboard& blackboard,
                                  const AgentInvoker& invoker) {
    const std::string& taskDescription = subtask.description;

    const std::string systemPrompt =
        "You are a task planner (Planner). Decompose the user's task into 3 to 7 "
        "independently-executable subtasks.\n"
        "Output format: one subtask per line, each starting with \"1. \", \"2. \", "
        "etc. Be concise. Do not include explanations or headings.\n"
        "Example:\n"
        "1. Search for relevant code\n"
        "2. Modify the entry function\n"
        "3. Write unit tests\n";

    const std::string userPrompt =
        "Task: " + taskDescription + "\n\nOutput the subtask list:";

    NativeEvent req;
    req.type             = EventType::LLM_REQUEST;
    req.taskId           = subtask.id;
    req.agentId          = id();
    req.agentName        = name();
    req.action           = "create_task_plan(dag)";
    req.llmSystemPrompt  = systemPrompt;
    req.llmPrompt        = userPrompt;

    NativeEvent resp = invoker(req);
    std::string llmResponse = resp.message;
    if (llmResponse.empty()) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
                            "LLM call failed for task %s — falling back to single subtask",
                            subtask.id.c_str());
        AgentResult r;
        r.action       = "plan_fallback";
        r.success      = false;
        r.errorMessage = "LLM call failed";
        r.output       = "1. " + taskDescription;
        r.thought      = "Planner LLM call failed; emitting single fallback subtask";
        return r;
    }

    // Persist plan metadata to the blackboard for downstream agents.
    blackboard.put("task_plan", "planner_completed");
    blackboard.put("task",      taskDescription);

    AgentResult r;
    r.output  = llmResponse;
    r.action  = "create_task_plan(dag)";
    r.success = true;
    r.thought = "Planner produced a subtask list";
    return r;
}

} // namespace rage::native
