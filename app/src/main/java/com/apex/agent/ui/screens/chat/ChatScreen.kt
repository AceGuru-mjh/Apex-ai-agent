package com.apex.agent.ui.screens.chat

// STUBBED: had 17 errors
sealed class Bubble
data class Thinking(val placeholder: String = "")
data class Text(val placeholder: String = "")
data class Command(val placeholder: String = "")
data class Search(val placeholder: String = "")
enum class CommandStatus { DEFAULT }
data class ChatMessage(val placeholder: String = "")
object CommandSafety
enum class CommandRisk { DEFAULT }
data class SkillItem(val placeholder: String = "")
data class ModelItem(val placeholder: String = "")
