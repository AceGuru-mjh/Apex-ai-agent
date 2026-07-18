// Searcher agent — performs best-effort code-base / GitHub search via the
// SEARCH_REQUEST callback. Failures are logged but not fatal.
#pragma once

#include "agent/IAgent.h"

namespace rage::native {

class SearcherAgent : public IAgent {
public:
    std::string id()   const override { return "searcher"; }
    std::string name() const override { return "Searcher"; }

    // subtask.description is the search query.
    AgentResult execute(const NativeSubtask& subtask,
                        Blackboard& blackboard,
                        const AgentInvoker& invoker) override;
};

} // namespace rage::native
