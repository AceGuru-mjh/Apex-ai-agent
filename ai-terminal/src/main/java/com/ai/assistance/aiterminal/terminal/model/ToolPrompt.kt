package com.ai.assistance.aiterminal.terminal.model

/**
 * Tool prompt for LLM function calling.
 * Matches the original ToolPrompt interface used by the app module.
 */
data class ToolPrompt(
    val name: String,
    val description: String,
    val parametersStructured: List<ToolParameterSchema> = emptyList()
)

/**
 * Tool parameter schema for LLM function calling.
 */
data class ToolParameterSchema(
    val name: String,
    val type: String,
    val description: String = "",
    val required: Boolean = false
)
