// Thread-safe metrics collector.
//
// Atomic counters for task totals; mutex-guarded rolling average for execution
// time. snapshot() is cheap (only grabs the average mutex briefly).
#pragma once

#include "core/RageTypes.h"

#include <atomic>
#include <cstdint>
#include <mutex>

namespace rage::native {

class MetricsCollector {
public:
    MetricsCollector() = default;
    ~MetricsCollector() = default;

    MetricsCollector(const MetricsCollector&) = delete;
    MetricsCollector& operator=(const MetricsCollector&) = delete;

    void recordTaskStart();
    void recordTaskComplete(bool success);
    void recordTaskCancel();
    void recordConcurrency(int current);  // observed concurrency for peak tracking
    void recordDuration(int64_t durationMs);  // feeds the average execution time

    NativeMetrics snapshot() const;
    void reset();

private:
    std::atomic<int64_t> totalTasks_{0};
    std::atomic<int64_t> successfulTasks_{0};
    std::atomic<int64_t> failedTasks_{0};
    std::atomic<int64_t> cancelledTasks_{0};
    std::atomic<int>     currentConcurrency_{0};
    std::atomic<int>     peakConcurrency_{0};

    // Rolling average of execution time (only successful + failed counted).
    mutable std::mutex   avgMutex_;
    int64_t              totalTimeMs_{0};
    int64_t              timedTaskCount_{0};
};

} // namespace rage::native
