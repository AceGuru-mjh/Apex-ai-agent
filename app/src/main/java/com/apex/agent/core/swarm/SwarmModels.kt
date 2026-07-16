package com.apex.agent.core.swarm

// Minimal implementation (had 4 errors)
data class Debate(val data: String = "")
enum class DebateStatus { DEFAULT }
enum class Stance { DEFAULT }
data class Consensus(val data: String = "")
data class VoteResults(val data: String = "")
data class AgentOpinion(val data: String = "")
enum class VoteType { DEFAULT }
data class DebateSummary(val data: String = "")
data class SwarmResult(val data: String = "")
data class Solution(val data: String = "")
data class SwarmTask(val data: String = "")
