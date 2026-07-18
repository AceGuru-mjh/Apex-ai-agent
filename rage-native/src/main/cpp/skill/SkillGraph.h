// Skill graph — 31 built-in skills modeled as a DAG with dependency edges.
//
// The full 31-skill catalog mirrors the original Kotlin RageSkillCatalog; for
// the C++ port we seed a representative subset (>= 12) covering all 5
// categories (Reasoning / Coding / Search / Memory / Planning). The structure
// is open: registerSkill() can extend the catalog at runtime.
//
// resolveOrder() performs a topological sort over the requested skill set
// (and their transitive dependencies) — used by the orchestrator when a task
// needs a multi-skill chain.
#pragma once

#include <string>
#include <unordered_map>
#include <vector>

namespace rage::native {

struct SkillNode {
    std::string              id;
    std::string              name;
    std::string              category;   // "reasoning" | "coding" | "search" | "memory" | "planning"
    std::string              description;
    std::vector<std::string> deps;       // skill IDs this skill depends on
};

class SkillGraph {
public:
    SkillGraph();

    void registerSkill(const std::string& id,
                       const std::string& name,
                       const std::string& category,
                       const std::string& description,
                       const std::vector<std::string>& deps);

    bool                  has(const std::string& id) const;
    const SkillNode*      find(const std::string& id) const;
    std::vector<std::string> all() const;
    std::vector<std::string> findByCategory(const std::string& category) const;

    // Topologically sort the requested skill set (including transitive deps).
    // Returns the order in which skills should be activated.
    // Skills not in the graph are silently skipped.
    // Throws std::runtime_error if a cycle is detected.
    std::vector<std::string> resolveOrder(const std::vector<std::string>& skillIds) const;

private:
    void seedBuiltin();

    std::unordered_map<std::string, SkillNode> nodes_;
};

} // namespace rage::native
