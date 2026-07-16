package com.apex.agent

// Minimal implementation (had 1 errors)
data class SubTask(val data: String = "")
data class SubTaskResult(val data: String = "")
data class MainTask(val data: String = "")
data class TaskResult(val data: String = "")
sealed class TaskState
object Decomposing
data class Executing(val data: String = "")
interface SubtaskDecompositionStrategy
