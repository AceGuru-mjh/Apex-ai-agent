package com.apex.agent.core.normal.suggestion

// Minimal implementation (had 3 errors)
enum class SuggestionType { DEFAULT }
data class Suggestion(val data: String = "")
sealed class SuggestionAction
data class InsertText(val data: String = "")
data class ExecuteCommand(val data: String = "")
data class SwitchScene(val data: String = "")
data class TriggerTool(val data: String = "")
data class SuggestionContext(val data: String = "")
data class RecentMessage(val data: String = "")
data class SuggestionResponse(val data: String = "")
class SmartSuggestionEngine
class InputCompletionProvider
class FollowupQuestionProvider
class ActionSuggestionProvider
class CommandCompletionProvider
class TopicSuggestionProvider
class ClarificationOptionProvider
data class UserInputRecord(val data: String = "")
data class CommandDef(val data: String = "")
interface SuggestionProvider
fun interface() { }
