package com.apex.agent.core.workflow.enhanced.model

// Minimal implementation (had 5 errors)
enum class EnhancedNodeType { DEFAULT }
data class EnhancedNode(val data: String = "")
data class NodePositionDef(val data: String = "")
data class EnhancedNodeConfig(val data: String = "")
data class TriggerConfigDef(val data: String = "")
enum class TriggerTypeDef { DEFAULT }
data class ScheduleConfigDef(val data: String = "")
enum class ScheduleTypeDef { DEFAULT }
data class EventTriggerConfigDef(val data: String = "")
data class IntentConfigDef(val data: String = "")
data class SpeechConfigDef(val data: String = "")
data class WebhookConfigDef(val data: String = "")
enum class ExtractModeDef { DEFAULT }
data class FanOutSpecDef(val data: String = "")
data class FanInSpecDef(val data: String = "")
enum class AggregatorTypeDef { DEFAULT }
data class LoopSpecDef(val data: String = "")
enum class LoopTypeDef { DEFAULT }
data class SubWorkflowConfigDef(val data: String = "")
data class HumanInputConfigDef(val data: String = "")
data class RetryPolicyDef(val data: String = "")
sealed class ParameterValueDef
data class StaticValue(val data: String = "")
data class NodeReference(val data: String = "")
data class Expression(val data: String = "")
