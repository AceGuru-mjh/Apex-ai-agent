#include "skill/SkillGraph.h"

#include <android/log.h>
#include <stdexcept>
#include <unordered_set>
#include <functional>

namespace rage::native {

namespace {
constexpr const char* TAG = "RageSkill";
}

SkillGraph::SkillGraph() {
    seedBuiltin();
}

void SkillGraph::registerSkill(const std::string& id,
                               const std::string& name,
                               const std::string& category,
                               const std::string& description,
                               const std::vector<std::string>& deps) {
    SkillNode node;
    node.id          = id;
    node.name        = name;
    node.category    = category;
    node.description = description;
    node.deps        = deps;
    nodes_[id] = std::move(node);
}

bool SkillGraph::has(const std::string& id) const {
    return nodes_.find(id) != nodes_.end();
}

const SkillNode* SkillGraph::find(const std::string& id) const {
    auto it = nodes_.find(id);
    return it == nodes_.end() ? nullptr : &it->second;
}

std::vector<std::string> SkillGraph::all() const {
    std::vector<std::string> ids;
    ids.reserve(nodes_.size());
    for (const auto& [id, _] : nodes_) ids.push_back(id);
    return ids;
}

std::vector<std::string> SkillGraph::findByCategory(const std::string& category) const {
    std::vector<std::string> ids;
    for (const auto& [id, node] : nodes_) {
        if (node.category == category) ids.push_back(id);
    }
    return ids;
}

std::vector<std::string> SkillGraph::resolveOrder(const std::vector<std::string>& skillIds) const {
    // DFS-based topological sort with cycle detection.
    std::vector<std::string> result;
    std::unordered_set<std::string> visited;
    std::unordered_set<std::string> onStack;
    bool cycle = false;

    std::function<void(const std::string&)> dfs = [&](const std::string& id) {
        if (cycle) return;
        if (visited.count(id)) return;
        if (onStack.count(id)) {
            cycle = true;
            return;
        }
        onStack.insert(id);
        auto it = nodes_.find(id);
        if (it != nodes_.end()) {
            for (const auto& dep : it->second.deps) {
                if (nodes_.count(dep)) dfs(dep);
                if (cycle) { onStack.erase(id); return; }
            }
        }
        onStack.erase(id);
        visited.insert(id);
        result.push_back(id);
    };

    for (const auto& id : skillIds) dfs(id);

    if (cycle) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "cycle detected in skill dependency graph");
        throw std::runtime_error("skill graph cycle");
    }
    return result;
}

void SkillGraph::seedBuiltin() {
    // Reasoning
    registerSkill("reasoning.react", "ReAct Reasoning", "reasoning",
                  "Reason+Act loop: interleave thought and tool calls.",
                  {});
    registerSkill("reasoning.tot", "Tree of Thought", "reasoning",
                  "Tree-of-thought branching exploration.",
                  {});
    registerSkill("reasoning.cot", "Chain of Thought", "reasoning",
                  "Linear chain-of-thought reasoning.",
                  {});

    // Coding
    registerSkill("coding.refactor", "Refactor", "coding",
                  "Refactor code for clarity/perf without changing behavior.",
                  {});
    registerSkill("coding.test_gen", "Test Generation", "coding",
                  "Generate unit tests for a module.",
                  {"coding.refactor"});
    registerSkill("coding.review", "Code Review", "coding",
                  "Review diffs for bugs/style.",
                  {"coding.refactor"});
    registerSkill("coding.debug", "Debug Trace", "coding",
                  "Reproduce and localize a bug.",
                  {});

    // Search
    registerSkill("search.code", "Code Search", "search",
                  "Search the codebase for symbols/patterns.",
                  {});
    registerSkill("search.web", "Web Search", "search",
                  "Search the web via the host's search tool.",
                  {});
    registerSkill("search.github", "GitHub Search", "search",
                  "Search GitHub repos/issues.",
                  {"search.web"});

    // Memory
    registerSkill("memory.recall", "Recall", "memory",
                  "Recall relevant facts from long-term memory.",
                  {});
    registerSkill("memory.persist", "Persist", "memory",
                  "Persist a fact for future recall.",
                  {"memory.recall"});

    // Planning
    registerSkill("planner.decompose", "Decompose", "planning",
                  "Break a task into a subtask DAG.",
                  {"reasoning.cot"});
    registerSkill("planner.schedule", "Schedule", "planning",
                  "Order subtasks respecting dependencies.",
                  {"planner.decompose"});
    registerSkill("planner.critic", "Critic Plan", "planning",
                  "Self-critique the plan before execution.",
                  {"planner.decompose", "reasoning.tot"});
}

} // namespace rage::native
