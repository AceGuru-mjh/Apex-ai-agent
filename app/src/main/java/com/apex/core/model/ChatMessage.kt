package com.apex.core.model

/**
 * 聊天消息 — 核心数据模型。
 *
 * 支持工具调用循环所需的额外字段：
 *  - [toolCallId] / [toolName]：role="tool" 的工具结果消息上承载，
 *    用于回灌 OpenAI function-calling 协议要求的 `tool_call_id`。
 *  - [toolCallsJson]：role="assistant" 且本回合触发了工具调用时承载，
 *    是 OpenAI 协议 `tool_calls` 字段的 JSON 数组字符串，
 *    形如 `[{"id":"call_xxx","type":"function","function":{"name":"...","arguments":"..."}}]`。
 *    非 null 时由 [com.apex.engine.chat.OpenAICompatProvider] 序列化进请求体。
 */
data class ChatMessage(
    val role: String,           // "user" / "assistant" / "system" / "tool"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolCallsJson: String? = null,
    val error: String? = null
) {
    val isUser: Boolean get() = role == "user"
    val isAssistant: Boolean get() = role == "assistant"
    val isSystem: Boolean get() = role == "system"
    val isTool: Boolean get() = role == "tool"
    val isError: Boolean get() = error != null
}
