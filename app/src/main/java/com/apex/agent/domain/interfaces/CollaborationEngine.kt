package com.apex.agent.domain.interfaces

// Minimal implementation (had 12 errors)
sealed class CollaborationResult
data class CollaborationError(val data: String = "")
data class AgentCapability(val data: String = "")
interface ICollaborationEngine
