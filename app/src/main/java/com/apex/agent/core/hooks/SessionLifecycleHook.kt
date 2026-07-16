package com.apex.agent.core.hooks

// Minimal implementation (had 20 errors)
data class SessionContext(val data: String = "")
interface SessionLifecycleHook
object HookRegistry
