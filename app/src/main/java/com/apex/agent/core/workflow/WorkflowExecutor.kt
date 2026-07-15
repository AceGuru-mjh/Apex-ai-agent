package com.apex.core.workflow

// STUBBED: original file had 406 compilation errors
sealed class NodeExecutionState
object Pending
object Running
data class Success(val placeholder: String = "")
data class Skipped(val placeholder: String = "")
data class Failed(val placeholder: String = "")
data class DependencyGraph(val placeholder: String = "")
data class WorkflowExecutionResult(val placeholder: String = "")
class WorkflowExecutor
class WorkflowRunLogger
