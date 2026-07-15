package com.apex.agent

// STUBBED: had 1 errors
sealed class TaskExecutionState
object Pending
data class Running(val placeholder: String = "")
data class Progress(val placeholder: String = "")
data class Cancelled(val placeholder: String = "")
data class Failed(val placeholder: String = "")
data class Completed(val placeholder: String = "")
data class TaskExecutionConfig(val placeholder: String = "")
class AgentTaskExecutor
data class TaskHandle(val placeholder: String = "")
