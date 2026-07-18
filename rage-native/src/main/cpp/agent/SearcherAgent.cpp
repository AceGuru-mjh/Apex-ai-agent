#include "agent/SearcherAgent.h"

#include "core/Blackboard.h"

#include <android/log.h>

namespace rage::native {

namespace {
constexpr const char* TAG = "RageSearch";
}

AgentResult SearcherAgent::execute(const NativeSubtask& subtask,
                                   Blackboard& blackboard,
                                   const AgentInvoker& invoker) {
    std::string query = subtask.description;
    if (query.size() > 200) query.resize(200);

    NativeEvent req;
    req.type       = EventType::SEARCH_REQUEST;
    req.taskId     = subtask.id;
    req.agentId    = id();
    req.agentName  = name();
    req.action     = "rag_search";
    req.searchQuery = query;

    NativeEvent resp;
    try {
        resp = invoker(req);
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
                            "search invoker threw (continuing): %s", e.what());
        resp.message.clear();
    } catch (...) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
                            "search invoker threw unknown exception (continuing)");
        resp.message.clear();
    }

    if (resp.message.empty()) {
        // Best-effort — empty result is non-fatal.
        blackboard.put("file_map",     "skipped");
        blackboard.put("dependencies", "unknown");
        AgentResult r;
        r.action  = "rag_search_skipped";
        r.success = true;
        r.output  = "Searcher phase skipped — engine returned empty result.";
        r.thought = "search yielded no results (continuing)";
        return r;
    }

    blackboard.put("file_map",     "rag_search_done");
    blackboard.put("dependencies", "see_search_output");
    blackboard.put("search_result", resp.message);

    AgentResult r;
    r.action  = "rag_search + ast_parse";
    r.success = true;
    r.output  = resp.message;
    r.thought = "Searcher retrieved relevant code";
    return r;
}

} // namespace rage::native
