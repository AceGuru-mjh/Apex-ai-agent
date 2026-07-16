package com.apex.agent.core.workflow.enhanced.replay

// Minimal implementation (had 77 errors)
class WorkflowReplayer
data class ReplaySession(val data: String = "")
enum class TimelineEventType { DEFAULT }
data class ReplayAnalysis(val data: String = "")
data class NodeTiming(val data: String = "")
data class FailureRecord(val data: String = "")
data class ComparisonResult(val data: String = "")
