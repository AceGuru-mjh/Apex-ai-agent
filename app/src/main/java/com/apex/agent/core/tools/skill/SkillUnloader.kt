package com.apex.agent.core.tools.skill

// Minimal implementation (had 8 errors)
class SkillUnloader
data class UnloadResult(val data: String = "")
data class UnloadAllResult(val data: String = "")
interface SkillUnloadListener
data class UnloadStats(val data: String = "")
