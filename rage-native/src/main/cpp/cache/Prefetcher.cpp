#include "cache/Prefetcher.h"

#include <android/log.h>

namespace rage::native {

namespace {
constexpr const char* TAG = "RagePrefetch";
}

Prefetcher::Prefetcher(ParallelScheduler& scheduler)
    : scheduler_(scheduler) {}

void Prefetcher::prefetch(const std::vector<std::string>& keys,
                          std::function<std::string(const std::string&)> loader) {
    if (!loader) return;
    std::lock_guard<std::mutex> lk(mutex_);
    for (const auto& k : keys) {
        // Capture by value — the loader must outlive the async call.
        auto capturedLoader = loader;
        std::string key = k;
        futures_.push_back(scheduler_.submit(0, [capturedLoader, key]() {
            try {
                std::string v = capturedLoader(key);
                if (!v.empty()) {
                    // No cache reference here — caller must poll via get()
                    // and supply the cache. We just trigger the load.
                    __android_log_print(ANDROID_LOG_DEBUG, TAG,
                                        "prefetched key=%s valueLen=%zu",
                                        key.c_str(), v.size());
                }
            } catch (const std::exception& e) {
                __android_log_print(ANDROID_LOG_WARN, TAG,
                                    "prefetch for %s threw: %s", key.c_str(), e.what());
            } catch (...) {
                __android_log_print(ANDROID_LOG_WARN, TAG,
                                    "prefetch for %s threw unknown exception", key.c_str());
            }
        }));
    }
}

std::optional<std::string> Prefetcher::get(const std::string& key,
                                           AggressiveCache& cache,
                                           std::function<std::string(const std::string&)> loader) {
    auto cached = cache.get(key);
    if (cached.has_value()) return cached;
    if (!loader) return std::nullopt;
    std::string v;
    try {
        v = loader(key);
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
                            "loader for %s threw: %s", key.c_str(), e.what());
        return std::nullopt;
    } catch (...) {
        return std::nullopt;
    }
    if (v.empty()) return std::nullopt;
    cache.put(key, v);
    return v;
}

void Prefetcher::waitAll() {
    std::vector<std::future<void>> snapshot;
    {
        std::lock_guard<std::mutex> lk(mutex_);
        snapshot = std::move(futures_);
        futures_.clear();
    }
    for (auto& f : snapshot) {
        if (f.valid()) {
            try { f.get(); } catch (...) { /* swallowed */ }
        }
    }
}

} // namespace rage::native
