// Executor agent — produces an implementation (diff / code) for one subtask.
// Receives the critic's previous feedback (if any) via the blackboard.
#pragma once

#include "agent/IAgent.h"

namespace rage::native {

class ExecutorAgent : public IAgent {
public:
    std::string id()   const override { return "executor"; }
    std::string name() const override { return "Executor"; }

    // Reads blackboard["critic_feedback"] to incorporate retry feedback.
    // subtask.description is the subtask to implement.
    AgentResult execute(const NativeSubtask& subtask,
                        Blackboard& blackboard,
                        const AgentInvoker& invoker) override;
};

} // namespace rage::native
