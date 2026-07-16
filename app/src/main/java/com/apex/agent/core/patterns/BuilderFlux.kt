package com.apex.agent.core.patterns

// Minimal implementation (had 37 errors)
enum class StageType { DEFAULT }
data class Stage(val data: String = "")
data class Pipeline(val data: String = "")
data class RichNotification(val data: String = "")
class AgentPipelineBuilder
class StageBuilder
class NotificationBuilder
