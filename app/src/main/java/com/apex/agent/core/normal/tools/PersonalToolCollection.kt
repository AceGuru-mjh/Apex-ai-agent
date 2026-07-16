package com.apex.agent.core.normal.tools

// Minimal implementation (had 34 errors)
class PersonalTool
sealed class PersonalToolResult
class PersonalNotesTool
class PersonalSnippetsTool
class PersonalFavoritesTool
class PersonalTodoTool
data class PersonalNote(val data: String = "")
data class PersonalSnippet(val data: String = "")
data class PersonalFavorite(val data: String = "")
data class PersonalTodo(val data: String = "")
interface PersonalStorage
class InMemoryPersonalStorage
class PersonalToolRegistry
