// Planner agent — decomposes a task into 3-7 subtasks via an LLM call.
#pragma once

#include "agent/IAgent.h"

namespace rage::native {

class PlannerAgent : public IAgent {
public:
    std::string id()   const override { return "planner"; }
    std::string name() const override { return "Planner"; }

    // subtask.description is the task to decompose.
    // Returns: AgentResult whose output is the raw LLM response (numbered list).
    // The orchestrator then parses the response into subtask entries.
    AgentResult execute(const NativeSubtask& subtask,
                        Blackboard& blackboard,
                        const AgentInvoker& invoker) override;
};

} // namespace rage::native
