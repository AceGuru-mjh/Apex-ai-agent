package com.apex.agent.presentation.multiagent.data

import java.util.UUID

/**
 * 多 Agent 模式数据类型定义。
 *
 * 此文件定义所有多 Agent 协作页面所需的数据类型，
 * 放置在 ai-terminal 模块中以避免循环依赖。
 * app 模块通过依赖 ai-terminal 模块来使用这些类型。
 */

// === Agent 相关 ===

enum class AgentRoleType {
    SUPERVISOR, WORKER, REVIEWER, PLANNER, RESEARCHER, IMPLEMENTER, VALIDATOR, COORDINATOR
}

enum class AgentStatus {
    IDLE, THINKING, EXECUTING, REVIEWING, COMPLETED, FAILED, OFFLINE
}

data class AgentCardData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: AgentRoleType = AgentRoleType.WORKER,
    val status: AgentStatus = AgentStatus.IDLE,
    val progress: Float = 0f,
    val currentTask: String? = null,
    val messageCount: Int = 0,
    val averageResponseTimeMs: Long = 0L,
    val capabilities: List<String> = emptyList(),
    val model: String = "default"
) {
    val isActive: Boolean
        get() = status == AgentStatus.THINKING || status == AgentStatus.EXECUTING || status == AgentStatus.REVIEWING
}

// === 消息相关 ===

enum class AgentMessageType {
    TEXT, TASK_ASSIGNMENT, TASK_RESULT, SYSTEM, FEEDBACK, ERROR, STATUS_UPDATE
}

data class AgentMessageCard(
    val id: String = UUID.randomUUID().toString(),
    val fromAgentId: String,
    val fromAgentName: String,
    val fromAgentRole: AgentRoleType = AgentRoleType.WORKER,
    val toAgentId: String? = null,
    val toAgentName: String? = null,
    val content: String,
    val type: AgentMessageType = AgentMessageType.TEXT,
    val timestamp: Long = System.currentTimeMillis()
)

// === 任务相关 ===

enum class TaskPriority(val weight: Int) {
    LOW(0), NORMAL(1), HIGH(2), URGENT(3), CRITICAL(4)
}

enum class TaskCardStatus {
    PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
}

data class SubtaskCard(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val assignedAgentId: String? = null,
    val status: TaskCardStatus = TaskCardStatus.PENDING,
    val order: Int = 0,
    val progress: Float = 0f
)

data class CollaborationTaskCard(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val priority: TaskPriority = TaskPriority.NORMAL,
    val status: TaskCardStatus = TaskCardStatus.PENDING,
    val assignedAgentIds: List<String> = emptyList(),
    val subtasks: List<SubtaskCard> = emptyList(),
    val progress: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val result: String? = null
)

// === 协作模式 ===

enum class CollaborationMode {
    PARALLEL_EXECUTION, SEQUENTIAL_EXECUTION, PIPELINE, SUPERVISOR, DEBATE, ROUND_ROBIN, BURST
}

data class TopologyNode(
    val id: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val label: String = ""
)

data class TopologyEdge(
    val from: String,
    val to: String,
    val label: String = ""
)

data class CollaborationTopology(
    val mode: CollaborationMode = CollaborationMode.PARALLEL_EXECUTION,
    val nodes: List<TopologyNode> = emptyList(),
    val edges: List<TopologyEdge> = emptyList()
) {
    fun autoLayout(nodeCount: Int): CollaborationTopology {
        val nodes = (0 until nodeCount).map { index ->
            val angle = (2 * Math.PI * index / nodeCount.coerceAtLeast(1))
            TopologyNode(id = "node_$index", x = (Math.cos(angle) * 200).toFloat(), y = (Math.sin(angle) * 200).toFloat())
        }
        val edges = if (nodeCount > 1) {
            (0 until nodeCount).map { i -> TopologyEdge(from = "node_$i", to = "node_${(i + 1) % nodeCount}") }
        } else emptyList()
        return copy(nodes = nodes, edges = edges)
    }
}

data class CollaborationStats(
    val totalAgents: Int = 0,
    val activeAgents: Int = 0,
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val failedTasks: Int = 0,
    val inProgressTasks: Int = 0,
    val totalMessages: Int = 0,
    val averageResponseTimeMs: Long = 0L,
    val overallProgress: Float = 0f,
    val startTime: Long = System.currentTimeMillis(),
    val elapsedTime: Long = 0L
)
