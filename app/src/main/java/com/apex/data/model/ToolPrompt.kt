package com.apex.data.model

/**
 * Tool prompt for LLM function calling.
 */
data class ToolPrompt(
    val name: String,
    val description: String,
    val parametersStructured: List<ToolParameterSchema> = emptyList(),
    val default: String? = null
)

/**
 * Tool parameter schema for LLM function calling.
 */
data class ToolParameterSchema(
    val name: String,
    val type: String,
    val description: String = "",
    val required: Boolean = false,
    val default: String? = null
)

/**
 * System tool prompt category.
 */
enum class SystemToolPromptCategory {
    FILE,
    TERMINAL,
    WEB,
    SYSTEM,
    MEMORY,
    WORKFLOW,
    HTTP,
    UI,
    MCP,
    SKILL,
    CHAT,
    CALCULATOR,
    DOCUMENT,
    PACK,
    CONFIG,
    ACCESSIBILITY,
    ROOT,
    DEBUGGER
}
