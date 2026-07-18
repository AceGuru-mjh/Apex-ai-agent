// Agent abstraction.
//
// Each concrete agent (Planner/Searcher/Executor/Critic) wraps the JNI LLM
// callback with a specific prompt template and returns an AgentResult. The
// orchestrator drives the 4-agent loop; agents are stateless beyond their
// prompt templates.
//
// The `Invoker` callback type models the JNI callback contract:
//   - C++ builds a NativeEvent (LLM_REQUEST or SEARCH_REQUEST) with the prompt
//   - Passes it to the invoker (which routes through JNI to Kotlin)
//   - Receives back a NativeEvent whose .message holds the Kotlin response
#pragma once

#include "core/RageTypes.h"

#include <functional>
#include <string>

namespace rage::native {

class Blackboard;

// Invoker: takes a request event, returns a response event.
// For LLM_REQUEST:    response.message = LLM completion text
// For SEARCH_REQUEST: response.message = search results text
// For other events:   response is unchanged (observation-only).
using AgentInvoker = std::function<NativeEvent(const NativeEvent&)>;

// Result returned by an agent after one execution step.
struct AgentResult {
    std::string output;             // raw LLM response (or synthetic message)
    std::string action;             // "create_task_plan(dag)", "rag_search", "edit_file(diff)", "review_pass"/"review_fail"
    bool        success = true;
    std::string errorMessage;
    std::string thought;            // short summary for the event stream
};

class IAgent {
public:
    virtual ~IAgent() = default;
    virtual std::string  id()   const = 0;
    virtual std::string  name() const = 0;
    virtual AgentResult  execute(const NativeSubtask& subtask,
                                 Blackboard& blackboard,
                                 const AgentInvoker& invoker) = 0;
};

} // namespace rage::native
