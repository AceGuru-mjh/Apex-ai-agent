package com.apex.agent.core.tools.system

// STUBBED: had 98 errors
class PermissionConfigBackupManager
sealed class BackupResult
data class Success(val placeholder: String = "")
data class Error(val placeholder: String = "")
sealed class RestoreResult
data class Success(val placeholder: String = "")
data class Error(val placeholder: String = "")
class SmartModeSwitcher
enum class UsageScenario { DEFAULT }
data class SwitchHistoryItem(val placeholder: String = "")
class PermissionModeAdvisor
data class ModeSuggestion(val placeholder: String = "")
enum class SuggestionType { DEFAULT }
data class ModeDetails(val placeholder: String = "")
