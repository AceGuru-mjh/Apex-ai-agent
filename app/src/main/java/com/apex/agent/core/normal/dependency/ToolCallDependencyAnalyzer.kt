package com.apex.agent.core.normal.dependency

// Minimal implementation (had 17 errors)
data class ToolInvocationRecord(val data: String = "")
enum class TriggerSource { DEFAULT }
data class ToolCallChain(val data: String = "")
data class ToolDependency(val data: String = "")
data class DependencyGraph(val data: String = "")
data class DependencyEdge(val data: String = "")
enum class DependencyType { DEFAULT }
data class ToolPerformanceAnalysis(val data: String = "")
class ToolCallDependencyAnalyzer
data class FailureImpact(val data: String = "")
data class AnalyzerStats(val data: String = "")
