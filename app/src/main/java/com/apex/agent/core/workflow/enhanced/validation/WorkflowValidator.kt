package com.apex.agent.core.workflow.enhanced.validation

// Minimal implementation (had 6 errors)
class WorkflowValidator
sealed class ValidationError
object NoStartNode
object NoNodes
data class CycleDetected(val data: String = "")
data class DanglingEdge(val data: String = "")
data class DuplicateNodeId(val data: String = "")
data class MultipleStartNodes(val data: String = "")
data class MissingJoin(val data: String = "")
sealed class ValidationWarning
data class UnreachableNode(val data: String = "")
data class DanglingNode(val data: String = "")
data class MissingFanIn(val data: String = "")
