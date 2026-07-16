package com.apex.agent.core.tools.skill

// Minimal implementation (had 9 errors)
class DevServerConfig
data class ServerSettings(val data: String = "")
data class HotReloadSettings(val data: String = "")
data class EditorSettings(val data: String = "")
data class PreviewSettings(val data: String = "")
interface ConfigListener
