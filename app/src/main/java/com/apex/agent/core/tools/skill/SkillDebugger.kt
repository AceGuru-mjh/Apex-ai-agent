package com.apex.agent.core.tools.skill

// STUBBED: had 10 errors
class SkillDebugger
enum class DebugState { DEFAULT }
enum class BreakpointType { DEFAULT }
data class Breakpoint(val placeholder: String = "")
data class ExecutionContext(val placeholder: String = "")
data class StackFrame(val placeholder: String = "")
data class ToolCall(val placeholder: String = "")
data class DebugSession(val placeholder: String = "")
enum class PauseReason { DEFAULT }
interface DebugListener
