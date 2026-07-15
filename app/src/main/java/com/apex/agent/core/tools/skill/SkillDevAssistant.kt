package com.apex.agent.core.tools.skill

// STUBBED: original file had 133 compilation errors
class SkillDevAssistant
data class EditorDocument(val placeholder: String = "")
data class CompletionItem(val placeholder: String = "")
enum class CompletionKind { DEFAULT }
data class Diagnostic(val placeholder: String = "")
enum class DiagnosticSeverity { DEFAULT }
data class SyntaxToken(val placeholder: String = "")
enum class TokenType { DEFAULT }
interface AssistantListener
data class SkillStructure(val placeholder: String = "")
data class FileNode(val placeholder: String = "")
data class PreviewResult(val placeholder: String = "")
enum class PreviewType { DEFAULT }
data class TokenPattern(val placeholder: String = "")
class LanguageServer
class JSLanguageServer
class TypeScriptLanguageServer
class MarkdownLanguageServer
