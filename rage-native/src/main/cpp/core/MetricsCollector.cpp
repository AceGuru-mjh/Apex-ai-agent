#include "core/MetricsCollector.h"

#include <algorithm>

namespace rage::native {

void MetricsCollector::recordTaskStart() {
    totalTasks_.fetch_add(1, std::memory_order_relaxed);
}

void MetricsCollector::recordTaskComplete(bool success) {
    if (success) successfulTasks_.fetch_add(1, std::memory_order_relaxed);
    else         failedTasks_.fetch_add(1, std::memory_order_relaxed);
}

void MetricsCollector::recordTaskCancel() {
    cancelledTasks_.fetch_add(1, std::memory_order_relaxed);
}

void MetricsCollector::recordConcurrency(int current) {
    current = std::max(0, current);
    currentConcurrency_.store(current, std::memory_order_relaxed);
    int prevPeak = peakConcurrency_.load(std::memory_order_relaxed);
    while (current > prevPeak &&
           !peakConcurrency_.compare_exchange_weak(prevPeak, current,
                                                    std::memory_order_relaxed)) {
        // retry
    }
}

void MetricsCollector::recordDuration(int64_t durationMs) {
    if (durationMs < 0) return;
    std::lock_guard<std::mutex> lk(avgMutex_);
    totalTimeMs_ += durationMs;
    ++timedTaskCount_;
}

NativeMetrics MetricsCollector::snapshot() const {
    NativeMetrics m;
    m.totalTasks       = totalTasks_.load(std::memory_order_relaxed);
    m.successfulTasks  = successfulTasks_.load(std::memory_order_relaxed);
    m.failedTasks      = failedTasks_.load(std::memory_order_relaxed);
    m.cancelledTasks   = cancelledTasks_.load(std::memory_order_relaxed);
    m.currentConcurrency = currentConcurrency_.load(std::memory_order_relaxed);
    m.peakConcurrency    = peakConcurrency_.load(std::memory_order_relaxed);
    {
        std::lock_guard<std::mutex> lk(avgMutex_);
        m.averageExecutionTimeMs = timedTaskCount_ > 0
            ? static_cast<double>(totalTimeMs_) / static_cast<double>(timedTaskCount_)
            : 0.0;
    }
    int64_t denom = m.successfulTasks + m.failedTasks;
    m.successRate = denom > 0
        ? static_cast<double>(m.successfulTasks) / static_cast<double>(denom)
        : 0.0;
    return m;
}

void MetricsCollector::reset() {
    totalTasks_.store(0, std::memory_order_relaxed);
    successfulTasks_.store(0, std::memory_order_relaxed);
    failedTasks_.store(0, std::memory_order_relaxed);
    cancelledTasks_.store(0, std::memory_order_relaxed);
    currentConcurrency_.store(0, std::memory_order_relaxed);
    peakConcurrency_.store(0, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lk(avgMutex_);
    totalTimeMs_ = 0;
    timedTaskCount_ = 0;
}

} // namespace rage::native
