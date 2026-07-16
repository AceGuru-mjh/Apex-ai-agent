package com.apex.agent.core.normal.privacy

// Minimal implementation (had 2 errors)
enum class PrivacyLevel { DEFAULT }
enum class DataType { DEFAULT }
data class DataPolicy(val data: String = "")
data class PrivacyConfig(val data: String = "")
sealed class DataAccessDecision
data class Allowed(val data: String = "")
data class RequiresConsent(val data: String = "")
class PrivacyManager
data class ConsentRecord(val data: String = "")
enum class ConsentScope { DEFAULT }
data class DataRecord(val data: String = "")
data class DeletionReport(val data: String = "")
data class UserDataExport(val data: String = "")
interface PrivacyEventListener
