// Aggressive LRU + TTL cache.
//
// "Aggressive" = (a) survives until TTL even under pressure, (b) prefetcher
// pre-populates entries before they're requested, (c) LRU eviction only kicks
// in when capacity is exceeded. Tuned for repeated LLM completions and search
// results during a single rage task.
//
// Thread-safe (std::mutex — modest contention expected; the cache is hot but
// not extreme-fan-out).
#pragma once

#include <cstdint>
#include <list>
#include <mutex>
#include <optional>
#include <string>
#include <unordered_map>

namespace rage::native {

class AggressiveCache {
public:
    AggressiveCache(size_t maxEntries, int64_t ttlMs);
    ~AggressiveCache() = default;

    AggressiveCache(const AggressiveCache&) = delete;
    AggressiveCache& operator=(const AggressiveCache&) = delete;

    void put(const std::string& key, const std::string& value);
    std::optional<std::string> get(const std::string& key);
    void evictExpired();
    void clear();
    size_t size() const;

private:
    struct Entry {
        std::string value;
        int64_t     createdAtMs = 0;
        std::list<std::string>::iterator lruIter;
    };

    mutable std::mutex                                  mutex_;
    const size_t                                        maxEntries_;
    const int64_t                                       ttlMs_;
    std::unordered_map<std::string, Entry>              map_;
    std::list<std::string>                              lru_;  // front = most-recent

    int64_t nowMs() const;
};

} // namespace rage::native
