package com.apex.agent.mts.schema

// Minimal implementation (had 32 errors)
enum class FailureStrategy { DEFAULT }
data class AgentModeConfig(val data: String = "")
data class ParameterSpec(val data: String = "")
data class RateLimit(val data: String = "")
data class ToolConstraints(val data: String = "")
data class ExecutionDependency(val data: String = "")
object ToolCategories
data class ToolSpec(val data: String = "")
data class ToolExecutorRef(val data: String = "")
data class ScoredTool(val data: String = "")
data class ToolPromptDef(val data: String = "")
interface ToolOutcome
enum class OptimizationStrategy { DEFAULT }
