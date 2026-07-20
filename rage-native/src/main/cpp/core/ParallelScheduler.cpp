#include "core/ParallelScheduler.h"

#include <android/log.h>
#include <algorithm>

namespace rage::native {

namespace {
constexpr const char* TAG = "RageSched";
}

ParallelScheduler::ParallelScheduler(int maxConcurrency,
                                     std::function<void()> threadInit)
    : maxConcurrency_(std::max(1, maxConcurrency)),
      threadInit_(std::move(threadInit)) {
    for (int i = 0; i < maxConcurrency_; ++i) {
        workers_.emplace_back([this] { workerLoop(); });
    }
    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "scheduler started with %d workers", maxConcurrency_);
}

ParallelScheduler::~ParallelScheduler() {
    shutdown();
}

void ParallelScheduler::shutdown() {
    bool expected = false;
    if (!shutdown_.compare_exchange_strong(expected, true)) return;
    cv_.notify_all();
    for (auto& w : workers_) {
        if (w.joinable()) w.join();
    }
    workers_.clear();
    __android_log_print(ANDROID_LOG_INFO, TAG, "scheduler shut down");
}

int ParallelScheduler::activeCount() const {
    return activeCount_.load(std::memory_order_relaxed);
}

int ParallelScheduler::queuedCount() const {
    return queuedCount_.load(std::memory_order_relaxed);
}

void ParallelScheduler::workerLoop() {
    // PERF-43: invoke the optional per-thread init callback BEFORE pulling
    // any tasks. rage_jni.cpp uses this to AttachCurrentThread + cache the
    // JNIEnv* in thread_local storage, so the first JNI callback issued
    // from this worker doesn't pay the (50-100us) attach latency on the
    // hot path. Errors in the callback are swallowed (best-effort).
    if (threadInit_) {
        try {
            threadInit_();
        } catch (const std::exception& e) {
            __android_log_print(ANDROID_LOG_WARN, TAG,
                                "threadInit threw: %s", e.what());
        } catch (...) {
            __android_log_print(ANDROID_LOG_WARN, TAG,
                                "threadInit threw unknown exception");
        }
    }
    for (;;) {
        QueueEntry entry;
        {
            std::unique_lock<std::mutex> lk(mutex_);
            cv_.wait(lk, [this] { return shutdown_.load() || !queue_.empty(); });
            if (queue_.empty()) {
                if (shutdown_.load()) return;
                continue;
            }
            entry = std::move(const_cast<QueueEntry&>(queue_.top()));
            queue_.pop();
            --queuedCount_;
            ++activeCount_;
        }
        try {
            if (entry.fn) entry.fn();
        } catch (const std::exception& e) {
            __android_log_print(ANDROID_LOG_ERROR, TAG,
                                "worker caught exception: %s", e.what());
        } catch (...) {
            __android_log_print(ANDROID_LOG_ERROR, TAG,
                                "worker caught unknown exception");
        }
        --activeCount_;
    }
}

} // namespace rage::native
