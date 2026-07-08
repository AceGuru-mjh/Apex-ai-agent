package com.ai.assistance.aiterminal.terminal.model

/**
 * Tool prompt for LLM function calling.
 */
data class ToolPrompt(
    val name: String,
    val description: String,
    val parameters: ToolParameterSchema = ToolParameterSchema()
)

data class ToolParameterSchema(
    val type: String = "object",
    val properties: Map<String, Any> = emptyMap(),
    val required: List<String> = emptyList()
)
