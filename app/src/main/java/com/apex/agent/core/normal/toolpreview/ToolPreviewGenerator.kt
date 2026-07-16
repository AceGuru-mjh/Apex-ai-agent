package com.apex.agent.core.normal.toolpreview

// Minimal implementation (had 3 errors)
data class ToolPreview(val data: String = "")
enum class RiskLevel { DEFAULT }
sealed class ConfirmationResult
data class Approved(val data: String = "")
data class Rejected(val data: String = "")
object TimedOut
enum class ApprovalScope { DEFAULT }
class ToolPreviewGenerator
data class ToolMetadata(val data: String = "")
class ToolConfirmationGateway
data class PendingConfirmation(val data: String = "")
