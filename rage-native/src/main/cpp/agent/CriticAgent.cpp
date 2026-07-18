#include "agent/CriticAgent.h"

#include "core/Blackboard.h"

#include <android/log.h>
#include <sstream>
#include <cctype>

namespace rage::native {

namespace {
constexpr const char* TAG = "RageCritic";

// Returns true if the first non-empty line of `response` starts with "PASS"
// (case-insensitive) and does NOT contain "FAIL".
bool parseCriticVerdict(const std::string& response) {
    std::istringstream iss(response);
    std::string line;
    while (std::getline(iss, line)) {
        // Trim whitespace.
        size_t b = line.find_first_not_of(" \t\r\n");
        if (b == std::string::npos) continue;
        size_t e = line.find_last_not_of(" \t\r\n");
        std::string trimmed = line.substr(b, e - b + 1);
        // Uppercase
        std::string upper;
        upper.reserve(trimmed.size());
        for (char c : trimmed) upper.push_back(static_cast<char>(std::toupper(static_cast<unsigned char>(c))));
        if (upper.rfind("PASS", 0) == 0 && upper.find("FAIL") == std::string::npos) {
            return true;
        }
        if (upper.rfind("FAIL", 0) == 0) {
            return false;
        }
        // Some other first line — fall through to next line.
    }
    return false;
}

std::string extractFeedback(const std::string& response) {
    std::istringstream iss(response);
    std::string line;
    std::ostringstream out;
    bool skippedFirst = false;
    while (std::getline(iss, line)) {
        if (!skippedFirst) {
            size_t b = line.find_first_not_of(" \t\r\n");
            if (b == std::string::npos) continue;  // skip blank lines before first content
            skippedFirst = true;
            continue;
        }
        out << line << "\n";
    }
    std::string fb = out.str();
    // Trim trailing whitespace.
    while (!fb.empty() && (fb.back() == '\n' || fb.back() == ' ' || fb.back() == '\t')) {
        fb.pop_back();
    }
    if (fb.empty()) fb = "Review did not produce detailed feedback.";
    return fb;
}
} // namespace

AgentResult CriticAgent::execute(const NativeSubtask& subtask,
                                 Blackboard& blackboard,
                                 const AgentInvoker& invoker) {
    const std::string systemPrompt =
        "You are a quality critic (Critic). Review the implementation against the "
        "original task and subtask list.\n"
        "Output format: the FIRST non-empty line must be exactly \"PASS\" or "
        "\"FAIL\". Subsequent lines contain detailed feedback.\n"
        "PASS means the implementation satisfies the task. FAIL means it does not; "
        "explain what to fix.";

    auto taskOpt = blackboard.get("task");
    std::string taskDesc = taskOpt.value_or("");
    auto subsOpt = blackboard.get("subtasks_joined");
    std::string subsJoined = subsOpt.value_or("(subtasks not recorded)");

    std::string userPrompt;
    userPrompt += "## Original task\n" + taskDesc + "\n\n";
    userPrompt += "## Subtask list\n" + subsJoined + "\n\n";
    userPrompt += "## Implementation\n";
    userPrompt += (subtask.description.size() > 4000
                       ? subtask.description.substr(0, 4000)
                       : subtask.description);
    userPrompt += "\n\n## Review (first line: PASS or FAIL):";

    NativeEvent req;
    req.type             = EventType::LLM_REQUEST;
    req.taskId           = subtask.id;
    req.agentId          = id();
    req.agentName        = name();
    req.action           = "review";
    req.llmSystemPrompt  = systemPrompt;
    req.llmPrompt        = userPrompt;

    NativeEvent resp = invoker(req);
    std::string llmResponse = resp.message;
    if (llmResponse.empty()) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
                            "LLM call failed for critic review of %s",
                            subtask.id.c_str());
        AgentResult r;
        r.action       = "review_failed";
        r.success      = false;  // conservatively treat as fail
        r.errorMessage = "LLM call failed";
        r.output       = "FAIL: critic LLM call failed";
        r.thought      = "Critic LLM call failed";
        return r;
    }

    bool passed = parseCriticVerdict(llmResponse);
    std::string feedback = extractFeedback(llmResponse);

    AgentResult r;
    r.action  = passed ? "review_pass" : "review_fail";
    r.success = passed;
    r.output  = passed ? "PASS" : ("FAIL: " + feedback);
    r.thought = passed ? "Critic approved" : "Critic requested revisions";
    return r;
}

} // namespace rage::native
