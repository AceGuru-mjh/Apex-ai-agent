package com.apex.agent.core.workflow.enhanced.serializer

// Minimal implementation (had 4 errors)
class WorkflowSerializer
data class WorkflowPackage(val data: String = "")
data class CompactWorkflow(val data: String = "")
data class CompactNode(val data: String = "")
data class CompactRetry(val data: String = "")
data class CompactConnection(val data: String = "")
data class ImportResult(val data: String = "")
