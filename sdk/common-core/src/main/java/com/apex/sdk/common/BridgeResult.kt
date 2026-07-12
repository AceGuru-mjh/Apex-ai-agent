package com.apex.sdk.common

/**
 * 跨 APK 调用的统一结果。
 *
 * 序列化为 AIDL 的 Parcel/return value 时，由各 AIDL 接口具体定义；
 * 但 Kotlin 侧的 API 一律使用本类型，方便业务代码统一处理。
 */
sealed class BridgeResult<out T> {
    data class Success<T>(val value: T) : BridgeResult<T>()
    data class Failure(val error: BridgeError) : BridgeResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): BridgeResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    inline fun onSuccess(block: (T) -> Unit): BridgeResult<T> {
        if (this is Success) block(value)
        return this
    }

    inline fun onFailure(block: (BridgeError) -> Unit): BridgeResult<T> {
        if (this is Failure) block(error)
        return this
    }

    fun getOrNull(): T? = (this as? Success)?.value
    fun errorOrNull(): BridgeError? = (this as? Failure)?.error

    /**
     * 返回成功值，若为失败则抛出包含错误码与错误信息的异常。
     */
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw IllegalStateException("BridgeResult.Failure: ${'$'}{error.code} - ${'$'}{error.message}")
    }
}

inline fun <T> bridgeRun(block: () -> T): BridgeResult<T> = try {
    BridgeResult.Success(block())
} catch (e: Throwable) {
    BridgeResult.Failure(BridgeError.fromThrowable(e))
}
