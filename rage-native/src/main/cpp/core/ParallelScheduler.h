// Priority thread-pool scheduler.
//
// The compute-intensive core of rage: drives parallel subtask execution,
// background prefetches, and agent-side parallelism. Design:
//
//   - N worker threads (N = maxConcurrency) consume from a priority queue.
//   - Tasks are submitted with an integer priority (higher = sooner).
//   - Same-priority tasks are FIFO (sequence number tie-breaker).
//   - shutdown() drains queued tasks (no new submits allowed) and joins workers.
//
// Thread-safe: submit() may be called from any thread.
#pragma once

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <functional>
#include <future>
#include <memory>
#include <mutex>
#include <queue>
#include <thread>
#include <vector>

namespace rage::native {

class ParallelScheduler {
public:
    explicit ParallelScheduler(int maxConcurrency);
    ~ParallelScheduler();

    ParallelScheduler(const ParallelScheduler&) = delete;
    ParallelScheduler& operator=(const ParallelScheduler&) = delete;

    // Submit a task with a priority (higher = sooner). Returns the future.
    // After shutdown(), submit returns a future containing a default-constructed T
    // (the task is silently dropped).
    template <typename F>
    auto submit(int priority, F&& task) -> std::future<decltype(task())> {
        using R = decltype(task());
        auto packaged = std::make_shared<std::packaged_task<R()>>(std::forward<F>(task));
        std::future<R> fut = packaged->get_future();
        QueueEntry entry;
        entry.priority = priority;
        entry.seq      = nextSeq_.fetch_add(1, std::memory_order_relaxed);
        entry.fn       = [packaged]() { (*packaged)(); };
        {
            std::unique_lock<std::mutex> lk(mutex_);
            if (shutdown_) {
                // Already shutting down — drop the task but produce a default value
                // so the caller's future is satisfied. We do this by detaching a one-shot
                // default-construct via the packaged_task's promise.
                // std::packaged_task<R()>'s promise is broken → future throws std::future_error.
                // To avoid that, run the task synchronously if possible, else break the promise.
                // Simplest correct behavior: run the task inline on a detached thread.
                // (Heavy shutdown is rare; correctness over latency.)
                lk.unlock();
                std::thread([packaged]() { (*packaged)(); }).detach();
                return fut;
            }
            queue_.push(std::move(entry));
            ++queuedCount_;
        }
        cv_.notify_one();
        return fut;
    }

    // Stop accepting new tasks; drain and join workers. Idempotent.
    void shutdown();

    // Number of tasks currently executing (best-effort).
    int activeCount() const;

    // Number of tasks queued (best-effort).
    int queuedCount() const;

    // Max concurrency configured at construction.
    int maxConcurrency() const { return maxConcurrency_; }

private:
    struct QueueEntry {
        int               priority = 0;
        uint64_t          seq      = 0;
        std::function<void()> fn;
    };
    struct Cmp {
        // std::priority_queue is a max-heap — top() returns the greatest.
        // We want highest priority first, then lowest seq (oldest) first.
        bool operator()(const QueueEntry& a, const QueueEntry& b) const {
            if (a.priority != b.priority) return a.priority < b.priority;
            return a.seq > b.seq;
        }
    };

    void workerLoop();

    const int                                          maxConcurrency_;
    std::priority_queue<QueueEntry,
                        std::vector<QueueEntry>,
                        Cmp>                           queue_;
    mutable std::mutex                                 mutex_;
    std::condition_variable                            cv_;
    std::vector<std::thread>                           workers_;
    std::atomic<bool>                                  shutdown_{false};
    std::atomic<uint64_t>                              nextSeq_{0};
    std::atomic<int>                                   activeCount_{0};
    std::atomic<int>                                   queuedCount_{0};
};

} // namespace rage::native
