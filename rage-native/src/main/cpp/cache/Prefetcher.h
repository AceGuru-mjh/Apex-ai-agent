// Prefetcher — eagerly load likely-needed data into the cache in parallel.
//
// Usage:
//   Prefetcher pf(scheduler);
//   pf.prefetch({"llm:plan:foo", "search:bar"}, loaderFn);
//   ... later ...
//   auto v = pf.get("llm:plan:foo", cache);  // returns cached value, or nullopt
//
// The loader is invoked once per key on the scheduler. Failures are swallowed
// (the entry is simply absent from the cache afterwards).
#pragma once

#include "cache/AggressiveCache.h"
#include "core/ParallelScheduler.h"

#include <functional>
#include <future>
#include <mutex>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

namespace rage::native {

class Prefetcher {
public:
    explicit Prefetcher(ParallelScheduler& scheduler);

    // Kick off parallel prefetch of `keys`. The loader is invoked per key.
    // Returns immediately; results land in the cache as they complete.
    void prefetch(const std::vector<std::string>& keys,
                  std::function<std::string(const std::string&)> loader);

    // If the key is in `cache`, return its value. Otherwise (optionally) load
    // it synchronously via `loader` and store it. Returns nullopt on miss +
    // absent loader, or if the loader returns empty.
    std::optional<std::string> get(const std::string& key,
                                   AggressiveCache& cache,
                                   std::function<std::string(const std::string&)> loader = nullptr);

    // Wait for all outstanding prefetches to complete (best-effort).
    void waitAll();

private:
    ParallelScheduler&                              scheduler_;
    std::mutex                                      mutex_;
    std::vector<std::future<void>>                  futures_;
};

} // namespace rage::native
