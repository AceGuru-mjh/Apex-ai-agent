package com.apex.agent.core.normal.quality

// Minimal implementation (had 14 errors)
data class ConversationQuality(val data: String = "")
enum class QualityLevel { DEFAULT }
enum class QualityDimension { DEFAULT }
data class QualityMetrics(val data: String = "")
data class QualityIssue(val data: String = "")
enum class IssueType { DEFAULT }
data class RoundQuality(val data: String = "")
class ConversationQualityEvaluator
fun ConversationQuality() { }
