// Cross-agent shared key-value store.
//
// Implementations: std::shared_mutex for read-write lock (multiple readers,
// single writer). The blackboard is the central communication bus between
// the four core agents (Planner/Searcher/Executor/Critic) — it holds the
// evolving task context (task plan, search results, patch tags, etc.).
#pragma once

#include <optional>
#include <shared_mutex>
#include <string>
#include <unordered_map>

namespace rage::native {

class Blackboard {
public:
    Blackboard() = default;
    ~Blackboard() = default;

    Blackboard(const Blackboard&) = delete;
    Blackboard& operator=(const Blackboard&) = delete;

    void put(const std::string& key, const std::string& value);
    std::optional<std::string> get(const std::string& key) const;
    void remove(const std::string& key);
    bool has(const std::string& key) const;
    std::unordered_map<std::string, std::string> snapshot() const;
    void clear();

private:
    mutable std::shared_mutex                        mutex_;
    std::unordered_map<std::string, std::string>     store_;
};

} // namespace rage::native
