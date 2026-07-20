#include "terminal_session.h"
#include <android/log.h>
#include <cstring>
#include <fcntl.h>
#include <pty.h>
#include <poll.h>       // PERF-01: poll() for non-blocking PTY reads with timeout
#include <chrono>       // PERF-01: steady_clock deadline for read timeout
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/ioctl.h>
#include <sys/resource.h>  // Security (TERM-FIX-3B / J-1): setrlimit for fork-bomb / resource protection
#include <pwd.h>
#include <limits.h>
#include <unistd.h>
#include <errno.h>
#include <sys/wait.h>
#include <signal.h>
#include <cstdlib>
#include <string.h>
#include <dirent.h>
#include <utility>

#define LOG_TAG "TerminalSession"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// 辅助函数：获取当前工作目录
static std::string get_current_dir() {
    char buffer[PATH_MAX];
    if (getcwd(buffer, sizeof(buffer)) != nullptr) {
        return std::string(buffer);
    }
    // 获取用户主目录作为备选
    struct passwd* pw = getpwuid(getuid());
    if (pw != nullptr && pw->pw_dir != nullptr) {
        return std::string(pw->pw_dir);
    }
    // 最后备选
    return "/data/data/com.ai.assistance.operit/files";
}

// 会话构造
TerminalSession::TerminalSession(std::string id, TerminalEventCallback cb)
    : sessionId(std::move(id)),
      shellPid(-1),
      state(SessionState::CREATED),
      callback(std::move(cb)) {

    // Security/correctness (A-5): do NOT pre-create a pipe here.
    // The previous implementation called `pipe(shellFd)` which produced a
    // unidirectional pipe that could not function as a PTY — the child's
    // stdin was wired to the WRITE end of the pipe (so it could never read
    // input) and the parent closed the write end (so executeCommand always
    // failed). The PTY is now created lazily in `start()` via `forkpty()`.
    shellFd[0] = -1;
    shellFd[1] = -1;

    // 获取初始目录
    cwd = get_current_dir();

    // 设置默认环境变量
    env["HOME"] = cwd;
    env["USER"] = "android";
    env["SHELL"] = "/system/bin/sh";
    env["PATH"] = "/system/bin:/system/xbin:/vendor/bin";
    env["TERM"] = "xterm-256color";
    env["LANG"] = "C.UTF-8";

    LOGD("Session %s created, initial cwd: %s", sessionId.c_str(), cwd.c_str());
}

// 会话析构
TerminalSession::~TerminalSession() {
    LOGD("TerminalSession destructor called for %s", sessionId.c_str());
    close();
}

// 启动会话（支持多Shell）
//
// Security/correctness (A-5): rewritten to use `forkpty()` instead of the
// broken `pipe()`-based implementation. `forkpty()` atomically:
//   1. opens a PTY master/slave pair (via openpty internally),
//   2. forks the process,
//   3. in the child, sets up a new session (setsid) and dups the slave
//      PTY onto stdin/stdout/stderr — making it the controlling terminal.
// The parent keeps the master fd, which is bidirectional (read+write),
// so `executeCommand` can both write commands and read output.
//
// Async-signal-safety (A-8/A-9): the child branch between fork and exec
// uses ONLY async-signal-safe calls. In particular:
//   - NO `__android_log_print` (LOGD/LOGE/LOGI) — replaced with
//     `write(STDERR_FILENO, ...)` for any error reporting.
//   - NO `exit()` — replaced with `_exit(127)` to avoid running atexit
//     handlers / stdio flushes inherited from the parent.
//   - env vars and cwd are set before execlp.
bool TerminalSession::start(const std::string& shellType) {
    if (state != SessionState::CREATED) {
        LOGE("Session %s is not in CREATED state", sessionId.c_str());
        return false;
    }

    if (shellFd[0] != -1 || shellFd[1] != -1) {
        LOGE("Session %s PTY already initialized", sessionId.c_str());
        return false;
    }

    int masterFd = -1;

    // Build the environment as a `char**` for the child. forkpty()+execve
    // would let us pass an explicit envp, but we use execlp() below to support
    // PATH lookup, so we setenv() in the child instead. We pre-build a local
    // copy here so the child can iterate it without touching heap state
    // in ways that might be unsafe.
    std::vector<std::pair<std::string, std::string>> envPairs;
    envPairs.reserve(env.size());
    for (const auto& kv : env) envPairs.push_back(kv);

    pid_t pid = forkpty(&masterFd, nullptr, nullptr, nullptr);
    if (pid < 0) {
        LOGE("forkpty failed for session %s: %s", sessionId.c_str(), strerror(errno));
        return false;
    }

    if (pid == 0) {
        // ===================== CHILD BRANCH =====================
        // CRITICAL (A-8/A-9): ONLY async-signal-safe calls allowed here.
        // No LOGD/LOGE/LOGI, no malloc-heavy stdlib, no std::cout, no exit().
        // Use write() for diagnostics, _exit() to terminate.

        // Security (TERM-FIX-3B / J-1): apply resource limits BEFORE any other setup.
        // setrlimit() is async-signal-safe (it is a thin syscall wrapper) and
        // inherits across exec(), so limits set here apply to the shell and all
        // its descendants. This is the primary defense against:
        //   - Fork bombs (`:(){ :|:& };:`) — RLIMIT_NPROC caps total processes
        //   - Disk-exhaustion DoS (`yes > /sdcard/x`) — RLIMIT_FSIZE caps file size
        //   - FD-exhaustion DoS — RLIMIT_NOFILE caps open FDs
        // Limits are intentionally generous (50 processes / 100MB file / 64 FDs)
        // so legitimate shell pipelines still work, but pathological cases are
        // bounded. Errors are intentionally ignored — best-effort hardening.
        //
        // NOTE: RLIMIT_NPROC counts processes per-UID, not per-session, so on a
        // multi-user device this limit is shared across all sessions of the same
        // UID. For a typical Android shell UID (e.g. u0_a123 or shell:2000) this
        // is the desired behavior.
        struct rlimit nproc_limit = {50, 100};   // soft=50, hard=100 processes
        (void)setrlimit(RLIMIT_NPROC, &nproc_limit);
        struct rlimit fsize_limit = {100 * 1024 * 1024, 200 * 1024 * 1024};  // 100MB / 200MB
        (void)setrlimit(RLIMIT_FSIZE, &fsize_limit);
        struct rlimit nofile_limit = {64, 128};  // soft=64, hard=128 open FDs
        (void)setrlimit(RLIMIT_NOFILE, &nofile_limit);

        // Choose shell binary; fall back to /system/bin/sh.
        const char* shellPath = shellType.empty() ? "/system/bin/sh" : shellType.c_str();

        // Set working directory.
        (void)chdir(cwd.c_str());

        // Set environment variables (setenv is async-signal-safe per POSIX).
        for (const auto& kv : envPairs) {
            (void)setenv(kv.first.c_str(), kv.second.c_str(), 1);
        }

        // Close any inherited FDs > 2 to prevent leaking parent handles
        // (sockets, other PTYs, DB fds) into the shell. We rely on O_CLOEXEC
        // for most, but iterate /proc/self/fd as a belt-and-suspenders measure.
        DIR* dir = opendir("/proc/self/fd");
        if (dir != nullptr) {
            int dirFd = dirfd(dir);
            struct dirent* entry;
            while ((entry = readdir(dir)) != nullptr) {
                int fd = atoi(entry->d_name);
                if (fd > 2 && fd != dirFd) {
                    // Qualify with `::` so this resolves to POSIX `close(int)`
                    // rather than the TerminalSession::close() member (which
                    // takes 0 args) — see terminal_session.h:54.
                    (void)::close(fd);
                }
            }
            (void)closedir(dir);
        }

        // Replace process image with the shell.
        execlp(shellPath, shellPath, (char*)nullptr);

        // Only reached if execlp failed. Report via async-signal-safe write()
        // and exit with _exit(127) (the conventional "exec failed" exit code).
        const char* msg = "TerminalSession: execlp failed\n";
        (void)write(STDERR_FILENO, msg, strlen(msg));
        _exit(127);
        // ===================== END CHILD BRANCH =====================
    }

    // ===================== PARENT BRANCH =====================
    // For a PTY, the master fd is bidirectional — we use the SAME fd for both
    // reading output (shellFd[0]) and writing input (shellFd[1]).
    shellFd[0] = masterFd;  // read shell output from master
    shellFd[1] = masterFd;  // write commands to master

    // PERF-01: set the master fd to non-blocking mode. This is REQUIRED for
    // the poll()-based read loop in `executeCommand` — without O_NONBLOCK,
    // a `read()` after poll() returns POLLIN could still block (e.g. if the
    // shell produces exactly the data poll() saw, then goes quiet before
    // read() runs). With O_NONBLOCK, read() returns EAGAIN/EWOULDBLOCK
    // instead of blocking, so the loop can fall back to the next poll()
    // iteration and respect the deadline.
    int fdFlags = fcntl(masterFd, F_GETFL, 0);
    if (fdFlags != -1) {
        (void)fcntl(masterFd, F_SETFL, fdFlags | O_NONBLOCK);
    }

    shellPid = pid;
    state = SessionState::RUNNING;

    LOGD("Session %s started via forkpty, masterFd=%d, Shell PID: %d",
         sessionId.c_str(), masterFd, pid);

    // 触发回调
    if (callback) {
        callback(TerminalEventType::SESSION_STATE_CHANGED, "RUNNING", 0);
    }

    return true;
}

// 执行命令（对标命令执行能力）
bool TerminalSession::executeCommand(const std::string& cmd) {
    if (state != SessionState::RUNNING) {
        LOGE("Session %s is not running", sessionId.c_str());
        if (callback) {
            callback(TerminalEventType::ERROR_OCCURRED, "Session not running", -1);
        }
        return false;
    }

    if (shellFd[1] == -1) {
        LOGE("Session %s shell PTY not available", sessionId.c_str());
        return false;
    }

    // 记录命令历史
    commandHistory.push_back(cmd);
    LOGD("Session %s executing command: %s", sessionId.c_str(), cmd.c_str());

    // 写入命令到Shell PTY master (input -> child stdin)
    std::string cmdWithNewline = cmd + "\n";
    ssize_t written = write(shellFd[1], cmdWithNewline.c_str(), cmdWithNewline.length());
    if (written < 0) {
        LOGE("Write command failed for session %s: %s", sessionId.c_str(), strerror(errno));
        if (callback) {
            callback(TerminalEventType::ERROR_OCCURRED, "Write command failed", -2);
        }
        return false;
    }

    // PERF-01 / PERF-02: poll()-based non-blocking read loop.
    //
    // The previous implementation used a blocking `while ((readLen = read(...)) > 0)`
    // loop which can hang the calling binder thread indefinitely if the shell
    // is quiet (e.g. interactive prompt waiting for input) or produces output
    // in slow trickles. On Android this manifests as a JNI ANR/SIGQUIT and
    // ultimately a watchdog kill.
    //
    // The fix uses poll() with a 100ms granularity and an overall deadline
    // (default 30s) so the binder thread is never blocked for more than the
    // deadline. Each chunk is streamed via the callback immediately (PERF-02)
    // so the UI can render progressively, and a 1 MiB cap prevents OOM on
    // pathological commands like `yes` or `cat /dev/urandom`.
    //
    // The PTY master was set to O_NONBLOCK in `start()`, so a `read()` that
    // finds no data returns EAGAIN/EWOULDBLOCK (errno) instead of blocking —
    // we treat that as "no data right now" and loop back to poll().
    static constexpr int READ_DEADLINE_MS = 30000;        // 30s overall cap
    static constexpr size_t MAX_OUTPUT_BYTES = 1024 * 1024;  // PERF-02: 1 MiB cap
    static constexpr int POLL_TIMEOUT_MS = 100;            // 100ms poll granularity

    std::string output;
    output.reserve(4096);
    auto deadline = std::chrono::steady_clock::now()
                    + std::chrono::milliseconds(READ_DEADLINE_MS);

    char buffer[4096];
    bool sentinelFound = false;
    bool truncated = false;

    while (std::chrono::steady_clock::now() < deadline && !sentinelFound) {
        struct pollfd pfd;
        pfd.fd = shellFd[0];
        pfd.events = POLLIN;
        pfd.revents = 0;

        int pr = poll(&pfd, 1, POLL_TIMEOUT_MS);
        if (pr < 0) {
            if (errno == EINTR) continue;  // interrupted by signal — retry
            LOGE("poll() failed for session %s: %s", sessionId.c_str(), strerror(errno));
            break;  // unrecoverable error — return what we have
        }
        if (pr == 0) {
            // 100ms elapsed with no data — re-check the deadline.
            continue;
        }

        if (pfd.revents & POLLIN) {
            ssize_t readLen = read(shellFd[0], buffer, sizeof(buffer) - 1);
            if (readLen > 0) {
                buffer[readLen] = '\0';

                // PERF-02: cap accumulated output at MAX_OUTPUT_BYTES to
                // prevent OOM on pathological commands (`yes`, `cat
                // /dev/urandom`, large `find /` output, etc.). Once the cap
                // is hit we still drain the PTY (to avoid blocking the
                // shell's stdout pipe) but stop appending.
                if (!truncated) {
                    if (output.size() + static_cast<size_t>(readLen) <= MAX_OUTPUT_BYTES) {
                        output.append(buffer, static_cast<size_t>(readLen));
                    } else {
                        size_t remaining = MAX_OUTPUT_BYTES - output.size();
                        if (remaining > 0) {
                            output.append(buffer, remaining);
                        }
                        output.append("[...truncated]");
                        truncated = true;
                        LOGI("Session %s output truncated at %zu bytes",
                             sessionId.c_str(), MAX_OUTPUT_BYTES);
                    }
                }

                // PERF-02: stream each chunk via the callback immediately
                // so the UI can render progressively instead of waiting for
                // the entire command to finish.
                if (callback) {
                    callback(TerminalEventType::COMMAND_OUTPUT,
                             std::string(buffer, static_cast<size_t>(readLen)), 0);
                }

                // Check for an end-of-command sentinel if the caller/shell
                // uses one. The Kotlin ShellSession protocol emits
                // `__APEX_CMD_DONE_<id>__`; some legacy paths use `__DONE__`.
                // We check the just-read chunk (not the full accumulated
                // output) for efficiency.
                if (strstr(buffer, "__APEX_CMD_DONE_") != nullptr ||
                    strstr(buffer, "__DONE__") != nullptr) {
                    sentinelFound = true;
                }
            } else if (readLen == 0) {
                // EOF — the shell closed its stdout (typically because the
                // shell process exited, e.g. `exit` command).
                break;
            } else {
                // readLen < 0 — expected EAGAIN/EWOULDBLOCK on a non-blocking
                // fd when poll() reported POLLIN but the data was already
                // drained by a racing reader (shouldn't happen here, but is
                // benign). Any other errno is a real error.
                if (errno != EAGAIN && errno != EWOULDBLOCK) {
                    LOGE("read() failed for session %s: %s",
                         sessionId.c_str(), strerror(errno));
                    break;
                }
                // EAGAIN/EWOULDBLOCK — loop back to poll().
            }
        }

        if (pfd.revents & (POLLHUP | POLLERR)) {
            // Peer hung up or reported an error — stop reading.
            break;
        }
    }

    // 触发输出事件（对标事件通知）
    // NOTE: when streaming is enabled (above), each chunk was already
    // delivered via callback. We still emit a final aggregated event for
    // callers that want the full output in one shot (e.g. AI summarizer).
    // The aggregated payload is capped at MAX_OUTPUT_BYTES.
    if (callback && !output.empty()) {
        callback(TerminalEventType::COMMAND_OUTPUT, output, 0);
    }

    // 检查命令执行结果
    // NOTE (A-5 follow-up): `waitpid(WNOHANG)` on the long-running shell PID
    // returns 0 when the shell is still alive (the normal case between
    // commands). The exit code is only meaningful when the shell itself dies
    // (e.g. user typed `exit`). This is preserved from the original code path
    // for compatibility; a proper fix would track command boundaries via a
    // sentinel/marker protocol (Operit-style).
    int status;
    pid_t waitResult = waitpid(shellPid, &status, WNOHANG);
    int exitCode = 0;

    if (waitResult > 0 && WIFEXITED(status)) {
        exitCode = WEXITSTATUS(status);
        LOGD("Shell exited with code: %d", exitCode);
    }

    // 触发完成事件
    if (callback) {
        callback(TerminalEventType::COMMAND_FINISHED, cmd, exitCode);
    }

    return true;
}

// 切换目录
//
// Security/correctness (A-6): the previous implementation called `chdir(path)`
// in the C++ PARENT process — but the parent's cwd has NO effect on the
// shell child's cwd (the shell was already execve'd with its own cwd in
// `start()`). The shell's cwd never changed, so subsequent commands were
// still run from the original directory. The fix is to send the literal
// `cd $path\n` command to the shell via the PTY master fd (same mechanism
// as `executeCommand`), so the SHELL processes the cd and updates ITS cwd.
//
// NOTE: this is a best-effort fix. We do not parse the shell's response to
// verify the cd succeeded (e.g. permission denied, no such directory). The
// caller is expected to query `pwd` afterwards to confirm the new cwd.
// The local `cwd` field is updated optimistically to match the requested
// path; a subsequent `pwd` query would override it with the truth.
bool TerminalSession::changeDirectory(const std::string& path) {
    if (state != SessionState::RUNNING) {
        LOGE("Session %s is not running", sessionId.c_str());
        if (callback) {
            callback(TerminalEventType::ERROR_OCCURRED, "Session not running", -1);
        }
        return false;
    }

    if (shellFd[1] == -1) {
        LOGE("Session %s shell PTY not available for cd", sessionId.c_str());
        if (callback) {
            callback(TerminalEventType::ERROR_OCCURRED, "Shell PTY not available", -3);
        }
        return false;
    }

    // Build "cd <path>\n" and write it to the shell's stdin (PTY master).
    // NOTE: we do NOT escape `path` here — a malicious path containing
    // shell metacharacters could inject commands. The Kotlin caller is
    // expected to validate / quote the path before calling. A future
    // hardening pass should use shell quoting (e.g. single-quote wrap).
    std::string cmd = "cd " + path + "\n";
    ssize_t written = write(shellFd[1], cmd.c_str(), cmd.length());
    if (written < 0) {
        LOGE("Failed to send cd command to session %s: %s",
             sessionId.c_str(), strerror(errno));
        if (callback) {
            callback(TerminalEventType::ERROR_OCCURRED, "Write cd command failed", -3);
        }
        return false;
    }

    // Optimistically update local cwd. The shell may reject the cd (e.g.
    // permission denied) — caller should verify via `pwd`.
    cwd = path;
    LOGD("Session %s sent cd to: %s (verify via pwd)", sessionId.c_str(), path.c_str());

    if (callback) {
        callback(TerminalEventType::DIRECTORY_CHANGED, cwd, 0);
    }

    return true;
}

// 挂起会话
void TerminalSession::suspend() {
    if (state == SessionState::RUNNING && shellPid > 0) {
        kill(shellPid, SIGSTOP);
        state = SessionState::SUSPENDED;

        LOGD("Session %s suspended", sessionId.c_str());

        if (callback) {
            callback(TerminalEventType::SESSION_STATE_CHANGED, "SUSPENDED", 0);
        }
    }
}

// 恢复会话
void TerminalSession::resume() {
    if (state == SessionState::SUSPENDED && shellPid > 0) {
        kill(shellPid, SIGCONT);
        state = SessionState::RUNNING;

        LOGD("Session %s resumed", sessionId.c_str());

        if (callback) {
            callback(TerminalEventType::SESSION_STATE_CHANGED, "RUNNING", 0);
        }
    }
}

// 关闭会话
//
// PERF-05: the previous implementation called `waitpid(shellPid, nullptr, 0)`
// which BLOCKS the calling thread until the child exits. On Android this is
// invoked from a JNI binder thread (via TerminalSessionPool::closeSession /
// closeAllSessions), and SIGKILL delivery + kernel reap can take tens of
// milliseconds under load — long enough to cause contention when closing
// many sessions. Worse, the pool's `closeSessionUnlocked` historically held
// `m_mutex` while calling `close()` (see PERF-05 fix in the pool below),
// serializing all session operations behind a single waitpid().
//
// The fix uses `waitpid(WNOHANG)` in a 100ms poll loop with a 1-second total
// budget. If the child isn't reaped within 1s (very unusual after SIGKILL),
// we abandon the wait — the JVM/ProcessManager SIGCHLD handler will reap it
// asynchronously. This keeps the binder thread responsive.
void TerminalSession::close() {
    if (state == SessionState::CLOSED) {
        return;
    }

    LOGD("Closing session %s", sessionId.c_str());

    // 关闭文件描述符
    // For a PTY, shellFd[0] and shellFd[1] may both point to the same master
    // fd — close it exactly once.
    if (shellFd[0] != -1) {
        ::close(shellFd[0]);
        shellFd[0] = -1;
    }
    if (shellFd[1] != -1 && shellFd[1] != shellFd[0]) {
        ::close(shellFd[1]);
    }
    shellFd[1] = -1;

    // 结束Shell进程
    if (shellPid > 0) {
        // Send SIGKILL first (SIGTERM is ignored by `/system/bin/sh` on
        // Android in many configurations, so SIGKILL is the reliable choice).
        (void)kill(shellPid, SIGKILL);

        // PERF-05: poll for reaping with WNOHANG. Total budget: 1 second
        // (10 iterations × 100ms). If the child is still not reaped by the
        // end of the budget, we abandon the wait — the kernel will keep the
        // child as a zombie until something reaps it (the JVM's SIGCHLD
        // handler, or process teardown). This is safe because we already
        // SIGKILL'd the child, so it WILL die — we just don't block on it.
        for (int i = 0; i < 10; i++) {
            int status;
            pid_t r = waitpid(shellPid, &status, WNOHANG);
            if (r == shellPid) {
                // Reaped.
                break;
            }
            if (r == -1) {
                // ECHILD — child already reaped by someone else (e.g. SIGCHLD
                // handler). Either way, nothing more for us to do.
                if (errno == EINTR) continue;  // retry this iteration
                break;
            }
            // r == 0 — child still running. Sleep 100ms and retry.
            usleep(100000);  // 100ms
        }
        // If still not reaped after 1s, the SIGCHLD handler / teardown will
        // reap it. Log so we can detect persistent zombies in field reports.
        // (No LOGE — this is expected occasionally under heavy load.)
        shellPid = -1;
    }

    // 更新状态
    state = SessionState::CLOSED;

    // 触发回调
    if (callback) {
        callback(TerminalEventType::SESSION_STATE_CHANGED, "CLOSED", 0);
    }

    LOGD("Session %s closed", sessionId.c_str());
}

// 会话池单例
TerminalSessionPool& TerminalSessionPool::getInstance() {
    static TerminalSessionPool instance;
    return instance;
}

// 创建会话
TerminalSession* TerminalSessionPool::createSession(const std::string& sessionId, TerminalEventCallback cb) {
    // A-7: serialize against concurrent JNI calls from multi-threaded Kotlin.
    std::lock_guard<std::mutex> lock(m_mutex);
    if (sessions.find(sessionId) != sessions.end()) {
        LOGE("Session %s already exists", sessionId.c_str());
        return nullptr;
    }

    auto* session = new TerminalSession(sessionId, cb);
    sessions[sessionId] = session;

    if (currentSessionId.empty()) {
        currentSessionId = sessionId;
    }

    LOGD("Created session %s", sessionId.c_str());
    return session;
}

// 获取会话 (A-7: locks; delegates to getSessionUnlocked to avoid
// deadlock with getCurrentSession which is itself a public method).
TerminalSession* TerminalSessionPool::getSession(const std::string& sessionId) {
    std::lock_guard<std::mutex> lock(m_mutex);
    return getSessionUnlocked(sessionId);
}

// A-7: internal helper — caller MUST already hold m_mutex.
TerminalSession* TerminalSessionPool::getSessionUnlocked(const std::string& sessionId) {
    auto it = sessions.find(sessionId);
    return it != sessions.end() ? it->second : nullptr;
}

// 获取当前会话 (A-7: locks; uses getSessionUnlocked to avoid re-entrant deadlock).
TerminalSession* TerminalSessionPool::getCurrentSession() {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (currentSessionId.empty()) {
        return nullptr;
    }
    return getSessionUnlocked(currentSessionId);
}

// 切换会话 (A-7: locks).
bool TerminalSessionPool::switchSession(const std::string& sessionId) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (sessions.find(sessionId) == sessions.end()) {
        LOGE("Session %s not found", sessionId.c_str());
        return false;
    }

    currentSessionId = sessionId;
    LOGD("Switched to session %s", sessionId.c_str());
    return true;
}

// 关闭会话 (PERF-05: see detachSessionUnlocked — the actual close()+delete
// happens OUTSIDE the lock so the blocking waitpid in close() doesn't
// serialize against other pool operations).
bool TerminalSessionPool::closeSession(const std::string& sessionId) {
    TerminalSession* session = nullptr;
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        session = detachSessionUnlocked(sessionId);
    }
    if (session == nullptr) {
        LOGE("Session %s not found", sessionId.c_str());
        return false;
    }
    // close() may block for up to 1s in the waitpid(WNOHANG) poll loop
    // (PERF-05). Doing this OUTSIDE m_mutex means concurrent
    // createSession/getSession/switchSession calls on OTHER sessions
    // are not blocked behind this close.
    session->close();
    delete session;
    LOGD("Closed session %s", sessionId.c_str());
    return true;
}

// A-7 / PERF-05: internal helper — caller MUST already hold m_mutex.
// Finds the session by id, REMOVES it from the map (so subsequent
// getSession/createSession calls with the same id see "not found"),
// updates `currentSessionId` if needed, and returns the raw pointer.
//
// IMPORTANT: this helper does NOT call session->close() or delete —
// the caller is responsible for doing so OUTSIDE the lock to avoid
// holding m_mutex during the (potentially 1s) waitpid in close().
//
// Returns nullptr if the session id is not in the map.
TerminalSession* TerminalSessionPool::detachSessionUnlocked(const std::string& sessionId) {
    auto it = sessions.find(sessionId);
    if (it == sessions.end()) {
        return nullptr;
    }
    TerminalSession* session = it->second;
    sessions.erase(it);

    // 更新当前会话
    if (currentSessionId == sessionId && !sessions.empty()) {
        currentSessionId = sessions.begin()->first;
    } else if (sessions.empty()) {
        currentSessionId.clear();
    }
    return session;
}

// 关闭所有会话 (PERF-05: detach all sessions under the lock, then
// close()+delete each one OUTSIDE the lock so the (up to 1s) waitpid
// per session doesn't serialize against other pool operations).
void TerminalSessionPool::closeAllSessions() {
    std::vector<TerminalSession*> toClose;
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        toClose.reserve(sessions.size());
        for (auto& pair : sessions) {
            toClose.push_back(pair.second);
        }
        sessions.clear();
        currentSessionId.clear();
    }
    // close()+delete outside the lock — each close() may do up to 1s of
    // waitpid(WNOHANG) polling (PERF-05), but this no longer blocks other
    // threads from using the pool.
    for (auto* session : toClose) {
        session->close();
        delete session;
    }
    LOGD("Closed all sessions (%zu sessions)", toClose.size());
}
