package com.ai.assistance.aiterminal.terminal.model

/**
 * 会话状态（对标Native层）
 */
enum class SessionState {
    CREATED, RUNNING, SUSPENDED, CLOSED
}

/**
 * 终端会话实体（对标多会话管理）
 */
data class Session(
    val sessionId: String,
    var state: SessionState = SessionState.CREATED,
    var currentDir: String = "/",
    val commandHistory: MutableList<String> = mutableListOf(),
    val env: MutableMap<String, String> = mutableMapOf(),
    // TERM-FIX-4C / D-3: timestamp bookkeeping used by the TerminalManager
    // reaper coroutine to auto-close idle (>30 min) or long-lived (>24 h)
    // sessions. createdAt is set once at construction; lastActivityAt is
    // bumped on every interactive entry point via touchSession().
    val createdAt: Long = System.currentTimeMillis(),
    var lastActivityAt: Long = System.currentTimeMillis()
)
