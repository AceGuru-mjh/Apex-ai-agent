package com.apex.agent.core.kanban

// STUBBED: had 76 errors
class KanbanColumn
sealed class ColumnCondition
data class PriorityCondition(val placeholder: String = "")
data class TagCondition(val placeholder: String = "")
data class TypeCondition(val placeholder: String = "")
data class DescriptionKeywordCondition(val placeholder: String = "")
enum class ConditionOperator { DEFAULT }
