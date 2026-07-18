// Skill matcher — heuristic keyword-based ranking of skills for a task.
//
// Real implementation: tokenize the task description, score each skill by
// (keyword hits in skill id + name + description + category), return ranked
// IDs (highest score first). Skills with score 0 are excluded.
#pragma once

#include "skill/SkillGraph.h"

#include <string>
#include <vector>

namespace rage::native {

class SkillMatcher {
public:
    SkillMatcher() = default;

    // Returns ranked skill IDs (best match first). Empty vector if no matches.
    std::vector<std::string> match(const std::string& taskDescription,
                                   const SkillGraph& graph) const;
};

} // namespace rage::native
