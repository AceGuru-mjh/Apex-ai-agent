package com.apex.rage.`native`

/**
 * Low-level JNI surface to the rage-native C++ core (`librage_native.so`).
 *
 * All functions are SYNCHRONOUS and may block for long durations
 * (LLM round-trips, parallel subtask execution, etc.). Callers MUST dispatch
 * them off the main thread — see [RageNativeBridge] for a coroutine-safe
 * wrapper.
 *
 * JNI symbol mangling:
 *   package `com.apex.rage.native` → class `RageNative` →
 *   `Java_com_apex_rage_native_RageNative_<methodName>`.
 *
 * The `native` segment is a Java reserved word but is allowed as a Kotlin
 * package identifier — the JVM bytecode treats it as an ordinary name, and
 * JNI mangling only escapes `_`/`;`/`[`, not Java keywords.
 */
object RageNative {

    @Volatile
    private var loaded: Boolean = false

    init {
        try {
            System.loadLibrary("rage_native")
            loaded = true
        } catch (t: Throwable) {
            // Native library unavailable — calls will throw UnsatisfiedLinkError.
            // We swallow here and let callers handle the per-method error.
            loaded = false
        }
    }

    /** True iff `librage_native.so` was successfully loaded. */
    fun isLoaded(): Boolean = loaded

    /**
     * Initialize the C++ core with a [NativeCallbacks] instance. Must be called
     * once before any other native method. Idempotent: re-init replaces the
     * callback registry and core singletons.
     *
     * @return `true` on success, `false` if the callback method lookup failed.
     */
    external fun nativeInit(callback: NativeCallbacks): Boolean

    /**
     * Execute a task synchronously.
     *
     * @param taskJson   JSON-serialized [NativeTask]
     * @param configJson JSON-serialized [NativeRageConfig]
     * @return JSON-serialized [NativeExecutionResult]
     */
    external fun nativeStartTask(taskJson: String, configJson: String): String

    /**
     * Request cancellation of an in-flight task. The orchestrator checks the
     * cancellation flag at safe points (between agents / between subtasks) and
     * returns a CANCELLED result.
     *
     * @return `true` if the cancellation flag was set.
     */
    external fun nativeCancelTask(taskId: String): Boolean

    /**
     * Snapshot the global metrics counter.
     *
     * @return JSON-serialized [NativeMetrics]
     */
    external fun nativeGetMetrics(): String

    /**
     * Tear down the C++ core: stop the scheduler, free singletons, release the
     * global ref to the callback. After this, [nativeInit] must be called again
     * before any other native method.
     */
    external fun nativeDestroy()
}
