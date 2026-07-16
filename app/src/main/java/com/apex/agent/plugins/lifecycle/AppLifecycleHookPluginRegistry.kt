package com.apex.plugins.lifecycle

// Minimal implementation (had 1 errors)
enum class AppLifecycleEvent { DEFAULT }
data class AppLifecycleHookParams(val data: String = "")
interface AppLifecycleHookPlugin
object AppLifecycleHookPluginRegistry
