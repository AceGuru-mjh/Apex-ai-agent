package com.apex.core.chat.plugins

// Minimal implementation (had 1 errors)
data class MessageProcessingHookParams(val data: String = "")
interface MessageProcessingController
data class MessageProcessingExecution(val data: String = "")
interface MessageProcessingPlugin
object MessageProcessingPluginRegistry
