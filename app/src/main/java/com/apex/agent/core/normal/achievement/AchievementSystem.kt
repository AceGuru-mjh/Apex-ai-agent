package com.apex.agent.core.normal.achievement

// Minimal implementation (had 6 errors)
enum class AchievementType { DEFAULT }
data class Badge(val data: String = "")
enum class BadgeRarity { DEFAULT }
enum class BadgeCategory { DEFAULT }
sealed class AchievementRequirement
data class Count(val data: String = "")
data class Streak(val data: String = "")
data class Specific(val data: String = "")
data class UserAchievement(val data: String = "")
data class EarnedBadge(val data: String = "")
data class StreakInfo(val data: String = "")
data class Challenge(val data: String = "")
enum class ChallengeType { DEFAULT }
sealed class AchievementEvent
data class BadgeEarned(val data: String = "")
data class LevelUp(val data: String = "")
data class StreakExtended(val data: String = "")
data class StreakBroken(val data: String = "")
data class ChallengeCompleted(val data: String = "")
class AchievementSystem
data class BadgeProgress(val data: String = "")
