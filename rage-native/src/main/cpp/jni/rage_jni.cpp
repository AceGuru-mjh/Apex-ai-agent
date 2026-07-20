// JNI entry point for rage-native.
//
// Mirrors the established pattern in ai-terminal/src/main/cpp/terminal_jni.cpp:
//   - JavaVM* gJvm global, saved in JNI_OnLoad
//   - jobject gCallbackObj global ref (Kotlin NativeCallbacks instance)
//   - jmethodID cached for onEvent / onLlmRequest / onSearchRequest
//   - AttachCurrentThread before any callback from a non-JVM thread
//
// All native methods are SYNCHRONOUS from Kotlin's perspective. The Kotlin
// side (rage-jni/RageNativeBridge) is responsible for dispatching off the
// main thread.
//
// JNI symbol naming (must EXACTLY match Kotlin `external fun`):
//   Package: com.apex.rage.nativelib
//   Class:   RageNative
//   Symbol:  Java_com_apex_rage_nativelib_RageNative_<methodName>
//
// Note: the package was renamed because the previous final package segment
// was the Java reserved word "native", which AGP rejects as a namespace.
// The internal C++ rage::native namespace used below is unrelated to the
// Kotlin package name and is unchanged.

#include <jni.h>

#include <android/log.h>
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <pthread.h>  // PERF-43: pthread_key_create for thread-exit cleanup
#include <string>
#include <unordered_map>

#include "agent/AgentOrchestrator.h"
#include "cache/AggressiveCache.h"
#include "core/Blackboard.h"
#include "core/MetricsCollector.h"
#include "core/ParallelScheduler.h"
#include "core/RageTypes.h"
#include "core/TaskStateMachine.h"
#include "skill/SkillGraph.h"
#include "skill/SkillMatcher.h"
#include "util/JsonSerializer.h"

#define LOG_TAG "RageJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// ============================================================
// Globals — callback registry
// ============================================================
JavaVM*  gJvm              = nullptr;
jobject  gCallbackObj      = nullptr;
jmethodID gOnEventMethod   = nullptr;  // void onEvent(String)
jmethodID gLlmInvokeMethod = nullptr;  // String onLlmRequest(String, String)
jmethodID gSearchMethod    = nullptr;  // String onSearchRequest(String)

// ============================================================
// Long-lived core singletons (created in nativeInit, destroyed in nativeDestroy)
// ============================================================
std::mutex                                             gCoreMutex;
std::unique_ptr<rage::native::ParallelScheduler>       gScheduler;
std::unique_ptr<rage::native::Blackboard>              gBlackboard;
std::unique_ptr<rage::native::MetricsCollector>        gMetrics;
std::unique_ptr<rage::native::TaskStateMachine>        gFsm;
std::unique_ptr<rage::native::AgentOrchestrator>       gOrchestrator;
std::unique_ptr<rage::native::SkillGraph>              gSkillGraph;
std::unique_ptr<rage::native::SkillMatcher>            gSkillMatcher;
std::unique_ptr<rage::native::AggressiveCache>         gCache;
// PERF-42: track the concurrency of the currently-live scheduler so we
// can skip the (expensive) shutdown+recreate cycle in nativeStartTask
// when the requested concurrency matches. Creating a ParallelScheduler
// spawns N worker threads (~1-5ms each), so avoiding this on every task
// is a significant hot-path win for burst-mode agents that submit many
// short tasks back-to-back.
std::atomic<int>                                       gCurrentConcurrency{0};

// ============================================================
// String helpers
// ============================================================

std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return std::string();
    const char* cstr = env->GetStringUTFChars(s, nullptr);
    if (!cstr) return std::string();
    std::string out(cstr);
    env->ReleaseStringUTFChars(s, cstr);
    return out;
}

jstring toJstr(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

// ============================================================
// PERF-43: thread-local JNIEnv* cache (replaces per-callback
// AttachCurrentThread + DetachCurrentThread).
//
// Previously every emitEventToKotlin / callLlmOnKotlin / callSearchOnKotlin
// call did `attachCurrentThread()` + (at end) `detachIfAttached()`. On a
// hot path (e.g. an agent emitting hundreds of step/observation events per
// second across N worker threads), this added ~50-100us of JVM thread-list
// lock contention per call AND prevented the JVM from caching the
// JNIEnv* (which it does for threads that stay attached).
//
// The fix: cache the JNIEnv* in `thread_local` storage, attach the FIRST
// time a thread needs it, and NEVER detach during normal operation. To
// avoid leaking the attachment when the thread exits (e.g. a
// ParallelScheduler worker shutting down), we register a pthread_key_t
// destructor that calls DetachCurrentThread on thread exit.
//
// ParallelScheduler workers are pre-attached via the `threadInit` callback
// passed to the scheduler constructor (see nativeInit / nativeStartTask
// below), so the first task a worker picks up doesn't pay the attach cost.
// ============================================================

namespace {
thread_local JNIEnv* t_env      = nullptr;
thread_local bool    t_attached = false;
pthread_key_t        gJvmTlsKey;
std::once_flag       gJvmTlsOnce;

// Called by pthread when a thread exits and has a non-NULL value
// associated with gJvmTlsKey. We set the value to a non-NULL sentinel
// on first attach (see getThreadEnv) so this destructor fires for any
// thread that was attached via getThreadEnv().
void jvmThreadExitCleanup(void* /*arg*/) {
    if (gJvm) {
        gJvm->DetachCurrentThread();
    }
    // Reset thread_local so a subsequent re-attach (e.g. thread reused
    // by a thread pool) re-attaches cleanly. (Note: this write happens
    // on the exiting thread, which is the same thread that owns t_env —
    // safe.)
    t_env = nullptr;
    t_attached = false;
}

void ensureJvmTlsKey() {
    std::call_once(gJvmTlsOnce, []() {
        int rc = pthread_key_create(&gJvmTlsKey, jvmThreadExitCleanup);
        if (rc != 0) {
            LOGW("pthread_key_create failed rc=%d — thread-exit detach disabled", rc);
        }
    });
}
}  // namespace

// Returns the cached JNIEnv* for the current thread, attaching if needed.
// The thread stays attached for its lifetime (no per-call detach).
// On thread exit, the pthread_key destructor above calls DetachCurrentThread.
//
// Forward-declared here so it can be referenced by the `threadInit` lambda
// passed to ParallelScheduler (which is defined before getThreadEnv in
// the file order — but C++ name lookup in the same TU handles this).
JNIEnv* getThreadEnv();

JNIEnv* getThreadEnv() {
    if (t_env != nullptr) return t_env;
    if (!gJvm) return nullptr;

    JNIEnv* env = nullptr;
    jint rc = gJvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (rc == JNI_OK) {
        // Already attached — this is a JVM-created thread (e.g. the main
        // thread, or a thread spawned by Kotlin). No need to attach or
        // register a destructor.
        t_env = env;
        t_attached = false;
        return t_env;
    }
    if (rc != JNI_EDETACHED) {
        LOGW("getThreadEnv: GetEnv returned %d", rc);
        return nullptr;
    }

    // Attach for the lifetime of this thread.
    JavaVMAttachArgs args;
    args.version = JNI_VERSION_1_6;
    args.name    = const_cast<char*>("RageNativeCb");
    args.group   = nullptr;
    rc = gJvm->AttachCurrentThread(&env, &args);
    if (rc != JNI_OK) {
        LOGE("getThreadEnv: AttachCurrentThread failed rc=%d", rc);
        return nullptr;
    }
    t_env = env;
    t_attached = true;

    // Register the pthread key destructor so the thread auto-detaches on
    // exit. We pass a non-NULL value so the destructor fires.
    ensureJvmTlsKey();
    if (gJvmTlsKey != 0) {
        pthread_setspecific(gJvmTlsKey, reinterpret_cast<void*>(1));
    }
    return t_env;
}

// ============================================================
// JNI callback: emit an event to Kotlin
// (Calls NativeCallbacks.onEvent(eventJson: String))
//
// PERF-43: uses getThreadEnv() — the thread_local-cached JNIEnv* —
// instead of per-call AttachCurrentThread/DetachCurrentThread.
// ============================================================
void emitEventToKotlin(const rage::native::NativeEvent& ev) {
    if (!gCallbackObj || !gOnEventMethod || !gJvm) return;
    JNIEnv* env = getThreadEnv();
    if (!env) return;
    std::string json = rage::native::serializeEvent(ev);
    jstring jJson = toJstr(env, json);
    env->CallVoidMethod(gCallbackObj, gOnEventMethod, jJson);
    env->DeleteLocalRef(jJson);
    if (env->ExceptionCheck()) {
        LOGW("onEvent threw — clearing exception");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    // PERF-43: do NOT detach — the JNIEnv* is cached in thread_local
    // storage and reused on the next callback. Detach happens only on
    // thread exit (via the pthread_key destructor).
}

// ============================================================
// JNI callback: invoke LLM
// (Calls NativeCallbacks.onLlmRequest(prompt, systemPrompt): String)
// Returns the LLM response text, or empty string on failure.
// ============================================================
std::string callLlmOnKotlin(const std::string& prompt, const std::string& systemPrompt) {
    if (!gCallbackObj || !gLlmInvokeMethod || !gJvm) {
        LOGW("callLlmOnKotlin: callback not initialized");
        return std::string();
    }
    JNIEnv* env = getThreadEnv();  // PERF-43
    if (!env) return std::string();

    jstring jPrompt       = toJstr(env, prompt);
    jstring jSystemPrompt = toJstr(env, systemPrompt);
    jobject jResp         = env->CallObjectMethod(gCallbackObj, gLlmInvokeMethod,
                                                  jPrompt, jSystemPrompt);
    env->DeleteLocalRef(jPrompt);
    env->DeleteLocalRef(jSystemPrompt);

    std::string response;
    if (env->ExceptionCheck()) {
        LOGW("onLlmRequest threw — clearing exception");
        env->ExceptionDescribe();
        env->ExceptionClear();
    } else if (jResp != nullptr) {
        response = jstr(env, static_cast<jstring>(jResp));
        env->DeleteLocalRef(jResp);
    }
    // PERF-43: no detach — thread stays attached for its lifetime.
    return response;
}

// ============================================================
// JNI callback: search
// (Calls NativeCallbacks.onSearchRequest(query): String)
// ============================================================
std::string callSearchOnKotlin(const std::string& query) {
    if (!gCallbackObj || !gSearchMethod || !gJvm) {
        LOGW("callSearchOnKotlin: callback not initialized");
        return std::string();
    }
    JNIEnv* env = getThreadEnv();  // PERF-43
    if (!env) return std::string();

    jstring jQuery = toJstr(env, query);
    jobject jResp  = env->CallObjectMethod(gCallbackObj, gSearchMethod, jQuery);
    env->DeleteLocalRef(jQuery);

    std::string response;
    if (env->ExceptionCheck()) {
        LOGW("onSearchRequest threw — clearing exception");
        env->ExceptionDescribe();
        env->ExceptionClear();
    } else if (jResp != nullptr) {
        response = jstr(env, static_cast<jstring>(jResp));
        env->DeleteLocalRef(jResp);
    }
    // PERF-43: no detach — thread stays attached for its lifetime.
    return response;
}

// ============================================================
// Build the AgentInvoker that routes NativeEvent -> JNI callbacks
// ============================================================
rage::native::AgentInvoker buildInvoker() {
    return [](const rage::native::NativeEvent& req) -> rage::native::NativeEvent {
        rage::native::NativeEvent resp = req;
        switch (req.type) {
            case rage::native::EventType::LLM_REQUEST:
                resp.message = callLlmOnKotlin(req.llmPrompt, req.llmSystemPrompt);
                // Also broadcast the event (non-fatal if it fails).
                emitEventToKotlin(req);
                break;
            case rage::native::EventType::SEARCH_REQUEST:
                resp.message = callSearchOnKotlin(req.searchQuery);
                emitEventToKotlin(req);
                break;
            default:
                // Observation-only event (TASK_STARTED, AGENT_STEP, etc.)
                emitEventToKotlin(req);
                break;
        }
        return resp;
    };
}

} // namespace (anonymous)

// ============================================================
// JNI exported functions
// ============================================================

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    gJvm = vm;
    LOGI("JNI_OnLoad: JavaVM saved");
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_com_apex_rage_nativelib_RageNative_nativeInit(JNIEnv* env, jobject /*thiz*/, jobject callback) {
    if (gCallbackObj != nullptr) {
        env->DeleteGlobalRef(gCallbackObj);
        gCallbackObj = nullptr;
    }
    if (callback != nullptr) {
        gCallbackObj = env->NewGlobalRef(callback);
        jclass cbCls = env->GetObjectClass(callback);
        if (cbCls != nullptr) {
            gOnEventMethod = env->GetMethodID(cbCls, "onEvent",
                                              "(Ljava/lang/String;)V");
            gLlmInvokeMethod = env->GetMethodID(cbCls, "onLlmRequest",
                                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
            gSearchMethod = env->GetMethodID(cbCls, "onSearchRequest",
                                             "(Ljava/lang/String;)Ljava/lang/String;");
            env->DeleteLocalRef(cbCls);
        }
    }
    if (!gOnEventMethod || !gLlmInvokeMethod || !gSearchMethod) {
        LOGE("nativeInit: failed to look up callback methods "
             "(onEvent=%p onLlmRequest=%p onSearchRequest=%p)",
             gOnEventMethod, gLlmInvokeMethod, gSearchMethod);
        return JNI_FALSE;
    }

    // Create core singletons (idempotent — re-init replaces previous).
    std::lock_guard<std::mutex> lk(gCoreMutex);
    // PERF-43: pass a threadInit callback that pre-attaches each worker
    // thread to the JVM on startup, so the first JNI callback issued from
    // that worker doesn't pay the attach latency on the hot path.
    gScheduler    = std::make_unique<rage::native::ParallelScheduler>(
        4, []() { (void)getThreadEnv(); });
    gCurrentConcurrency.store(4, std::memory_order_relaxed);  // PERF-42
    gBlackboard   = std::make_unique<rage::native::Blackboard>();
    gMetrics      = std::make_unique<rage::native::MetricsCollector>();
    gFsm          = std::make_unique<rage::native::TaskStateMachine>();
    gSkillGraph   = std::make_unique<rage::native::SkillGraph>();
    gSkillMatcher = std::make_unique<rage::native::SkillMatcher>();
    gCache        = std::make_unique<rage::native::AggressiveCache>(256, 5 * 60 * 1000);
    gOrchestrator = std::make_unique<rage::native::AgentOrchestrator>(
        *gScheduler, *gBlackboard, *gMetrics, *gFsm);
    LOGI("nativeInit: core singletons created");
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_apex_rage_nativelib_RageNative_nativeStartTask(JNIEnv* env,
                                                     jobject /*thiz*/,
                                                     jstring taskJson,
                                                     jstring configJson) {
    if (!taskJson || !configJson) {
        return toJstr(env, rage::native::serializeResult(
            rage::native::NativeExecutionResult{}));
    }
    std::string taskStr   = jstr(env, taskJson);
    std::string configStr = jstr(env, configJson);

    rage::native::NativeTask        task   = rage::native::deserializeTask(taskStr);
    rage::native::NativeRageConfig  config = rage::native::deserializeConfig(configStr);

    if (task.id.empty()) {
        // Defensive: synthesize a task ID if Kotlin didn't supply one.
        task.id = "rage-" + std::to_string(
            std::chrono::steady_clock::now().time_since_epoch().count());
    }
    if (task.preset.empty()) task.preset = "BALANCED";

    // PERF-42: reuse the scheduler if the requested concurrency matches.
    // Creating a ParallelScheduler spawns N worker threads (~1-5ms each
    // for thread-stack allocation + runtime registration), which is
    // wasted when the caller submits many short tasks back-to-back with
    // the same concurrency. We only recreate when the concurrency changes
    // (or the scheduler is missing entirely).
    int requestedConcurrency = config.maxConcurrency > 0 ? config.maxConcurrency : 4;
    rage::native::AgentOrchestrator* orchestratorPtr = nullptr;
    {
        std::lock_guard<std::mutex> lk(gCoreMutex);
        int currentConcurrency = gCurrentConcurrency.load(std::memory_order_relaxed);
        if (!gScheduler || currentConcurrency != requestedConcurrency) {
            // Concurrency changed (or first call) — rebuild scheduler.
            if (gScheduler) gScheduler->shutdown();
            gScheduler = std::make_unique<rage::native::ParallelScheduler>(
                requestedConcurrency,
                []() { (void)getThreadEnv(); });  // PERF-43: pre-attach worker
            gCurrentConcurrency.store(requestedConcurrency, std::memory_order_relaxed);
            // Orchestrator holds a reference to the scheduler — must rebuild
            // it whenever the scheduler is replaced.
            gOrchestrator = std::make_unique<rage::native::AgentOrchestrator>(
                *gScheduler, *gBlackboard, *gMetrics, *gFsm);
        } else if (!gOrchestrator) {
            // Defensive: scheduler exists but orchestrator was somehow reset
            // (e.g. nativeDestroy ran between nativeInit and nativeStartTask
            // — should not happen in practice, but we handle it gracefully).
            gOrchestrator = std::make_unique<rage::native::AgentOrchestrator>(
                *gScheduler, *gBlackboard, *gMetrics, *gFsm);
        }
        // PERF-41: capture the raw orchestrator pointer while holding the
        // lock, then release the lock BEFORE calling executeTask. This
        // allows nativeCancelTask / nativeGetMetrics to proceed concurrently
        // (critical for cancel-responsiveness — previously a long-running
        // executeTask would block nativeCancelTask behind gCoreMutex,
        // defeating the entire cancel protocol).
        //
        // Safety: gOrchestrator is a unique_ptr that is only reset in
        // nativeDestroy. Kotlin (RageNativeBridge) guarantees that
        // nativeDestroy is NOT called concurrently with nativeStartTask
        // — both are serialized by the same Kotlin-side lock. So the raw
        // pointer captured here remains valid for the duration of
        // executeTask. (If a future caller violates this invariant, the
        // worst case is a use-after-free in executeTask — a bug to fix
        // at the Kotlin API level, not here.)
        orchestratorPtr = gOrchestrator.get();
    }

    rage::native::AgentInvoker invoker = buildInvoker();
    rage::native::NativeExecutionResult result;
    if (!orchestratorPtr) {
        result.success = false;
        result.errorMessage = "nativeInit not called";
    } else {
        // PERF-41: executeTask called WITHOUT holding gCoreMutex. This
        // is the agent's main hot path — it may invoke LLM calls (which
        // can take seconds), spawn subtasks on the scheduler, emit
        // hundreds of events, etc. Holding gCoreMutex during all of
        // that would serialize every JNI call (cancel, metrics, even
        // a concurrent nativeStartTask for a different task) behind
        // this one executeTask.
        result = orchestratorPtr->executeTask(task, config, invoker);
    }
    result.taskId = task.id;
    return toJstr(env, rage::native::serializeResult(result));
}

JNIEXPORT jboolean JNICALL
Java_com_apex_rage_nativelib_RageNative_nativeCancelTask(JNIEnv* env,
                                                      jobject /*thiz*/,
                                                      jstring taskId) {
    if (!taskId) return JNI_FALSE;
    std::string id = jstr(env, taskId);
    std::lock_guard<std::mutex> lk(gCoreMutex);
    if (!gOrchestrator) return JNI_FALSE;
    gOrchestrator->requestCancel(id);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_apex_rage_nativelib_RageNative_nativeGetMetrics(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lk(gCoreMutex);
    rage::native::NativeMetrics m;
    if (gMetrics) m = gMetrics->snapshot();
    return toJstr(env, rage::native::serializeMetrics(m));
}

JNIEXPORT void JNICALL
Java_com_apex_rage_nativelib_RageNative_nativeDestroy(JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lk(gCoreMutex);
    gOrchestrator.reset();
    gScheduler.reset();
    gCurrentConcurrency.store(0, std::memory_order_relaxed);  // PERF-42
    gBlackboard.reset();
    gMetrics.reset();
    gFsm.reset();
    gSkillGraph.reset();
    gSkillMatcher.reset();
    gCache.reset();

    if (gCallbackObj != nullptr && gJvm != nullptr) {
        JNIEnv* env = nullptr;
        if (gJvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->DeleteGlobalRef(gCallbackObj);
        }
        gCallbackObj = nullptr;
    }
    gOnEventMethod   = nullptr;
    gLlmInvokeMethod = nullptr;
    gSearchMethod    = nullptr;
    LOGI("nativeDestroy: core singletons destroyed");
}

} // extern "C"
