package com.apex.agent.core.thinking

// Minimal implementation (had 15 errors)
sealed class ThoughtNode
enum class ThoughtType { DEFAULT }
data class ObservationNode(val data: String = "")
data class QuestionNode(val data: String = "")
data class InferenceNode(val data: String = "")
data class DecisionNode(val data: String = "")
data class ActionNode(val data: String = "")
data class ResultNode(val data: String = "")
data class SummaryNode(val data: String = "")
data class UnknownNode(val data: String = "")
data class Edge(val data: String = "")
enum class VisualizationMode { DEFAULT }
