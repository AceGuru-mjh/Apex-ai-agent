package com.apex.agent.core.normal.shortcut

// Minimal implementation (had 37 errors)
data class ShortcutCommand(val data: String = "")
enum class CommandCategory { DEFAULT }
sealed class CommandTemplate
data class Prompt(val data: String = "")
data class Composite(val data: String = "")
data class CommandParameter(val data: String = "")
enum class ParameterType { DEFAULT }
data class ParsedCommand(val data: String = "")
sealed class CommandExecutionResult
data class SendMessage(val data: String = "")
data class ExecuteAction(val data: String = "")
data class CompositeResult(val data: String = "")
object NoOp
data class ConversationTemplate(val data: String = "")
class ShortcutCommandRegistry
