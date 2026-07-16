package com.apex.agent.domain.event

// Minimal implementation (had 1 errors)
data class SystemStartedEvent(val data: String = "")
data class SystemShutdownEvent(val data: String = "")
data class SystemErrorEvent(val data: String = "")
enum class ErrorSeverity { DEFAULT }
data class SystemConfigChangedEvent(val data: String = "")
data class SystemHealthCheckEvent(val data: String = "")
data class SystemResourceWarningEvent(val data: String = "")
