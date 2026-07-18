#include "agent/ExecutorAgent.h"

#include "core/Blackboard.h"

#include <android/log.h>

namespace rage::native {

namespace {
constexpr const char* TAG = "RageExec";
}

AgentResult ExecutorAgent::execute(const NativeSubtask& subtask,
                                   Blackboard& blackboard,
                                   const AgentInvoker& invoker) {
    const std::string systemPrompt =
        "You are a code executor (Executor). Given a subtask and the shared "
        "blackboard context, produce a concrete, executable implementation.\n"
        "Prefer outputting a unified diff / patch or specific code changes. "
        "Do not include lengthy explanations.";

    std::string userPrompt = "## Subtask\n" + subtask.description + "\n\n";

    userPrompt += "## Blackboard state\n";
    auto snap = blackboard.snapshot();
    int shown = 0;
    for (const auto& [k, v] : snap) {
        if (shown++ >= 20) break;
        std::string val = v.size() > 200 ? v.substr(0, 200) : v;
        userPrompt += "- " + k + ": " + val + "\n";
    }

    auto fb = blackboard.get("critic_feedback");
    if (fb && !fb->empty()) {
        userPrompt += "\n## Previous critic feedback (please address)\n" + *fb + "\n";
    }
    userPrompt += "\n## Output the implementation (diff or code):";

    NativeEvent req;
    req.type             = EventType::LLM_REQUEST;
    req.taskId           = subtask.id;
    req.agentId          = id();
    req.agentName        = name();
    req.action           = "edit_file(diff)";
    req.llmSystemPrompt  = systemPrompt;
    req.llmPrompt        = userPrompt;

    NativeEvent resp = invoker(req);
    std::string llmResponse = resp.message;
    if (llmResponse.empty()) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
                            "LLM call failed for subtask %s", subtask.id.c_str());
        AgentResult r;
        r.action       = "implement_failed";
        r.success      = false;
        r.errorMessage = "LLM call failed";
        r.output       = "<llm failure — no implementation produced>";
        r.thought      = "Executor LLM call failed";
        return r;
    }

    blackboard.put("patch_" + subtask.id, llmResponse);

    AgentResult r;
    r.action  = "edit_file(diff)";
    r.success = true;
    r.output  = llmResponse;
    r.thought = "Executor produced an implementation";
    return r;
}

} // namespace rage::native
