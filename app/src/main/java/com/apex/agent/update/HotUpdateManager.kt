package com.apex.agent.update

// STUBBED: had 13 errors
class HotUpdateManager
sealed class UpdateState
object Idle
object Checking
data class UpdateAvailable(val placeholder: String = "")
data class Downloading(val placeholder: String = "")
object Downloaded
data class Failed(val placeholder: String = "")
