package com.apex.agent.core.multiagent

// STUBBED: had 22 errors
class GepaIntegration
sealed class GepaState
object Idle
object Analyzing
object Matching
object UsingDefaultStrategy
data class ReadyToExecute(val placeholder: String = "")
object Executing
data class ExtractionComplete(val placeholder: String = "")
data class Completed(val placeholder: String = "")
data class Error(val placeholder: String = "")
data class ExecutionResult(val placeholder: String = "")
