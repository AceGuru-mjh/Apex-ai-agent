// Critic agent — evaluates the combined implementation; emits PASS/FAIL.
#pragma once

#include "agent/IAgent.h"

#include <string>
#include <vector>

namespace rage::native {

class CriticAgent : public IAgent {
public:
    std::string id()   const override { return "critic"; }
    std::string name() const override { return "Critic"; }

    // subtask.description is the combined implementation text (orchestrator-joined).
    // The orchestrator seeds blackboard["task"] + blackboard["subtasks_joined"]
    // before invoking the critic.
    // On success, returns AgentResult with output="PASS" or "FAIL: <feedback>".
    // success=true means "review passed".
    AgentResult execute(const NativeSubtask& subtask,
                        Blackboard& blackboard,
                        const AgentInvoker& invoker) override;
};

} // namespace rage::native
