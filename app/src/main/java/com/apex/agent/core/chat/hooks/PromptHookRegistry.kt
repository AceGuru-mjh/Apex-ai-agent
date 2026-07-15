package com.apex.core.chat.hooks

// STUBBED: had 1 errors
data class PromptHookContext(val placeholder: String = "")
data class PromptHookMutation(val placeholder: String = "")
interface PromptInputHook
interface PromptHistoryHook
interface PromptEstimateHistoryHook
interface SystemPromptComposeHook
interface ToolPromptComposeHook
interface PromptFinalizeHook
interface PromptEstimateFinalizeHook
object PromptHookRegistry
