package com.apex.agent.presentation.multiagent.data

/**
 * Multi-agent data stubs.
 * Local stubs to avoid circular dependency on the app module.
 */
data class AgentInfo(
    val id: String,
    val name: String,
    val role: String = "",
    val status: String = "idle"
)

data class CollaborationResult(
    val agentId: String,
    val output: String = "",
    val success: Boolean = true
)

data class AgentMessage(
    val fromAgent: String,
    val toAgent: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
