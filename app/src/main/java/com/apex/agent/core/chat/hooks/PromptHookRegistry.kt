package com.apex.core.chat.hooks

// Minimal implementation (had 1 errors)
data class PromptHookContext(val data: String = "")
data class PromptHookMutation(val data: String = "")
interface PromptInputHook
interface PromptHistoryHook
interface PromptEstimateHistoryHook
interface SystemPromptComposeHook
interface ToolPromptComposeHook
interface PromptFinalizeHook
interface PromptEstimateFinalizeHook
object PromptHookRegistry
