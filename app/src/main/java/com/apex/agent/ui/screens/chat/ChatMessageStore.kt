package com.apex.agent.ui.screens.chat

// Minimal implementation (had 2 errors)
class ChatMessageStore
data class PersistedMessage(val data: String = "")
sealed class PersistedBubble
