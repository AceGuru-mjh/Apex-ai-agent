package com.apex.agent.core.patterns

// Minimal implementation (had 45 errors)
interface Prototype
data class AgentConfigPrototype(val data: String = "")
data class TaskPrototype(val data: String = "")
class PrototypeRegistry
