package com.apex.agent.core.workflow.enhanced.subworkflow

// Minimal implementation (had 3 errors)
data class SubWorkflowInvocation(val data: String = "")
enum class ParentChildLink { DEFAULT }
sealed class SubWorkflowResult
data class AsyncStarted(val data: String = "")
interface SubWorkflowExecutor
data class SubWorkflowExecution(val data: String = "")
enum class SubWorkflowStatus { DEFAULT }
class DelegatingSubWorkflowExecutor
