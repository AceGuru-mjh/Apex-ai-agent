package com.apex.lib.rage

import kotlinx.serialization.Serializable

/**
 * Architect 数据模型 —— 类型定义保留(公开 API),行为已迁移至 C++ 核心层
 * ([:rage-native] / [:rage-jni])。
 *
 * **历史背景**:本文件原先内嵌在 `RageAgentArchitect.kt` 中(包含 4 核心 Agent
 * 编排逻辑 + 黑板 + 动态扩容 + Critic 重试循环)。ARCH-3 重构后,所有编排
 * 行为下沉到 C++17 核心层,本文件只保留数据类定义,供:
 * - [RageEngine.startTask] 的返回值 [TaskExecutionResult] 引用
 * - Rage UI / 日志 / 测试 / 序列化使用
 * - C++ 核心通过 JNI 序列化为 JSON,Kotlin 侧用 [NativeMappers] 反序列化为这些类型
 *
 * **架构原则**:数据类是稳定的契约(向前/向后兼容);行为在 C++ 中可独立演化。
 */
// ============================================================
// Agent 角色与配置
// ============================================================

/** Agent 角色 —— 4 核心 + 动态扩容。 */
enum class AgentRole { PLANNER, SEARCHER, EXECUTOR, CRITIC, DYNAMIC }

/** 核心 Agent 配置。 */
@Serializable
data class AgentConfig(
    val id: String,
    val displayName: String,
    val roleDisplay: String,
    val role: AgentRole,
    val enabled: Boolean = true,
    val tools: List<String> = emptyList()
)

/** 动态扩容 Agent 信息。 */
@Serializable
data class DynamicAgentInfo(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val tools: List<String>,
    val createdAt: Long,
    val status: String
)

/** Agent 执行结果(内部 —— C++ 侧产生,通过 JSON 回传 Kotlin 反序列化)。 */
data class AgentResult(
    val thought: String = "",
    val action: String = "",
    val output: String = "",
    val blackboardUpdates: Map<String, String> = emptyMap(),
    val success: Boolean = true,
    val errorMessage: String? = null
)

/** Agent 执行步骤记录。 */
@Serializable
data class AgentStepRecord(
    val agentId: String,
    val agentName: String,
    val role: String,
    val action: String,
    val thought: String,
    val output: String,
    val blackboardUpdates: Map<String, String>,
    val success: Boolean,
    val errorMessage: String? = null,
    val durationMs: Long,
    val timestamp: Long
)

/** 黑板条目。 */
@Serializable
data class BlackboardEntry(
    val key: String,
    val value: String,
    val writer: String,
    val timestamp: Long
)

// ============================================================
// 任务执行结果
// ============================================================

/**
 * 任务执行结果 —— [RageEngine.startTask] 的返回值。
 *
 * 由 C++ 核心层填充,通过 [:rage-jni] 的 `NativeExecutionResult` 回传,
 * 由 [NativeMappers] 中的 `toKotlin()` 映射为本类型。
 */
@Serializable
data class TaskExecutionResult(
    val taskId: String,
    val success: Boolean,
    val steps: List<AgentStepRecord>,
    val blackboardSnapshot: Map<String, String>,
    val durationMs: Long,
    val retryCount: Int,
    val agentInvocations: Int,
    val dynamicAgentCount: Int,
    val errorMessage: String? = null
)

/** 执行历史记录(内存 —— 由 [RageTaskStore] 维护,不持久化)。 */
@Serializable
data class ExecutionRecord(
    val taskId: String,
    val taskDescription: String,
    val startTime: Long,
    val endTime: Long,
    val success: Boolean,
    val steps: List<AgentStepRecord>
) {
    val durationMs: Long get() = endTime - startTime
    val stepCount: Int get() = steps.size
}

// ============================================================
// 架构师事件流(向后兼容 —— 现由 NativeEvent 翻译为 RageEvent,但保留类型供历史消费者)
// ============================================================

/**
 * 架构师事件流 —— 历史类型,保留供外部消费者(测试 / 日志 / UI)使用。
 *
 * **注意**:新架构下,C++ 核心通过 `NativeEvent` 回调 Kotlin,经
 * [NativeMappers.toRageEvent] 翻译为 [RageEvent] 后由 [RageEngine.events]
 * 暴露。本 sealed class 不再由 [RageEngine] 直接发射,仅作为类型契约保留。
 */
sealed class ArchitectEvent {
    data class TaskStarted(val taskId: String, val description: String) : ArchitectEvent()
    data class TaskCompleted(val taskId: String, val result: TaskExecutionResult) : ArchitectEvent()
    data class TaskFailed(val taskId: String, val error: String) : ArchitectEvent()
    data class AgentStarted(val agentId: String, val agentName: String, val action: String) : ArchitectEvent()
    data class AgentFinished(
        val agentId: String,
        val agentName: String,
        val step: AgentStepRecord,
        val durationMs: Long
    ) : ArchitectEvent()
    data class AgentSpawned(val taskId: String, val agents: List<DynamicAgentInfo>) : ArchitectEvent()
    data class AgentTerminated(val taskId: String, val agentId: String, val reason: String) : ArchitectEvent()
    data class AgentsCleanedUp(val taskId: String, val agents: List<DynamicAgentInfo>) : ArchitectEvent()
    data class BlackboardUpdated(val taskId: String, val entries: Map<String, BlackboardEntry>) : ArchitectEvent()
}
