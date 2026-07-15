package com.apex.agent.infrastructure.eventbus

// STUBBED: had 15 errors
interface DispatchingStrategy
object Sequential
data class Parallel(val placeholder: String = "")
data class Ordered(val placeholder: String = "")
data class Priority(val placeholder: String = "")
interface BackpressureStrategy
object Drop
data class Buffer(val placeholder: String = "")
object Backpressure
interface ErrorStrategy
object FailFast
object ContinueOnError
data class RetryOnError(val placeholder: String = "")
class EventDispatcher
