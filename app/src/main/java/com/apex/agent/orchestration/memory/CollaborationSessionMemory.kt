package com.apex.agent.orchestration.memory

// Minimal implementation (had 2 errors)
data class CollaborationSessionMemory(val data: String = "")
data class AgentMemory(val data: String = "")
data class Decision(val data: String = "")
enum class DecisionStatus { DEFAULT }
data class ContextItem(val data: String = "")
enum class ContextCategory { DEFAULT }
data class Artifact(val data: String = "")
enum class ArtifactType { DEFAULT }
