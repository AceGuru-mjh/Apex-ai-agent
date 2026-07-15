package com.apex.core.services

// STUBBED: had 11 errors
class LinkServicesManager
sealed class LinkServiceStatus
object Disconnected
object Connecting
data class Connected(val placeholder: String = "")
data class Error(val placeholder: String = "")
interface LinkServiceCallback
