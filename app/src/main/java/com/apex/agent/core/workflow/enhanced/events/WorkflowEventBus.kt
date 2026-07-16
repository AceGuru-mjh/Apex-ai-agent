package com.apex.agent.core.workflow.enhanced.events

// Minimal implementation (had 7 errors)
sealed class WorkflowEvent
data class External(val data: String = "")
data class NodeOutput(val data: String = "")
class WorkflowEventBus
object EventBusHolder
