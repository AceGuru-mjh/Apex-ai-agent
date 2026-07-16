package com.apex.agent.core.normal

// Minimal implementation (had 1 errors)
data class NormalAgentConfig(val data: String = "")
enum class ResponseDepth { DEFAULT }
data class NormalAgentContext(val data: String = "")
sealed class NormalAgentResult
data class NeedsClarification(val data: String = "")
data class NeedsToolConfirmation(val data: String = "")
