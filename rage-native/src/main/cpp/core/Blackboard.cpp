#include "core/Blackboard.h"

#include <android/log.h>
#include <mutex>

namespace rage::native {

namespace {
constexpr const char* TAG = "RageBoard";
}

void Blackboard::put(const std::string& key, const std::string& value) {
    {
        std::unique_lock<std::shared_mutex> lk(mutex_);
        store_[key] = value;
    }
    __android_log_print(ANDROID_LOG_DEBUG, TAG,
                        "put key=%s valueLen=%zu", key.c_str(), value.size());
}

std::optional<std::string> Blackboard::get(const std::string& key) const {
    std::shared_lock<std::shared_mutex> lk(mutex_);
    auto it = store_.find(key);
    if (it == store_.end()) return std::nullopt;
    return it->second;
}

void Blackboard::remove(const std::string& key) {
    std::unique_lock<std::shared_mutex> lk(mutex_);
    store_.erase(key);
}

bool Blackboard::has(const std::string& key) const {
    std::shared_lock<std::shared_mutex> lk(mutex_);
    return store_.find(key) != store_.end();
}

std::unordered_map<std::string, std::string> Blackboard::snapshot() const {
    std::shared_lock<std::shared_mutex> lk(mutex_);
    return store_;
}

void Blackboard::clear() {
    std::unique_lock<std::shared_mutex> lk(mutex_);
    store_.clear();
}

} // namespace rage::native
