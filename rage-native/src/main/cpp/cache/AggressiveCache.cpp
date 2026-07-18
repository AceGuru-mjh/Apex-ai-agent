#include "cache/AggressiveCache.h"

#include <android/log.h>
#include <chrono>

namespace rage::native {

namespace {
constexpr const char* TAG = "RageCache";
}

AggressiveCache::AggressiveCache(size_t maxEntries, int64_t ttlMs)
    : maxEntries_(maxEntries == 0 ? 1 : maxEntries)
    , ttlMs_(ttlMs < 0 ? 0 : ttlMs) {}

int64_t AggressiveCache::nowMs() const {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

void AggressiveCache::put(const std::string& key, const std::string& value) {
    std::lock_guard<std::mutex> lk(mutex_);
    auto it = map_.find(key);
    if (it != map_.end()) {
        // Update existing entry; bump to front of LRU.
        it->second.value = value;
        it->second.createdAtMs = nowMs();
        lru_.erase(it->second.lruIter);
        lru_.push_front(key);
        it->second.lruIter = lru_.begin();
        return;
    }
    // New entry — maybe evict.
    while (map_.size() >= maxEntries_) {
        if (lru_.empty()) break;
        const std::string& victim = lru_.back();
        map_.erase(victim);
        lru_.pop_back();
    }
    Entry e;
    e.value = value;
    e.createdAtMs = nowMs();
    lru_.push_front(key);
    e.lruIter = lru_.begin();
    map_.emplace(key, std::move(e));
}

std::optional<std::string> AggressiveCache::get(const std::string& key) {
    std::lock_guard<std::mutex> lk(mutex_);
    auto it = map_.find(key);
    if (it == map_.end()) return std::nullopt;
    // TTL check.
    if (ttlMs_ > 0 && (nowMs() - it->second.createdAtMs) > ttlMs_) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG,
                            "entry %s expired — evicting on read", key.c_str());
        lru_.erase(it->second.lruIter);
        map_.erase(it);
        return std::nullopt;
    }
    // Bump to front of LRU.
    lru_.erase(it->second.lruIter);
    lru_.push_front(key);
    it->second.lruIter = lru_.begin();
    return it->second.value;
}

void AggressiveCache::evictExpired() {
    std::lock_guard<std::mutex> lk(mutex_);
    if (ttlMs_ <= 0) return;
    int64_t now = nowMs();
    for (auto it = map_.begin(); it != map_.end(); ) {
        if ((now - it->second.createdAtMs) > ttlMs_) {
            lru_.erase(it->second.lruIter);
            it = map_.erase(it);
        } else {
            ++it;
        }
    }
}

void AggressiveCache::clear() {
    std::lock_guard<std::mutex> lk(mutex_);
    map_.clear();
    lru_.clear();
}

size_t AggressiveCache::size() const {
    std::lock_guard<std::mutex> lk(mutex_);
    return map_.size();
}

} // namespace rage::native
