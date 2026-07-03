package com.apex.apk.rage

import com.apex.agent.plugins.burst.base.IBurstSkill
import com.apex.sdk.bridge.IApkBridgeInternal
import com.apex.sdk.common.ApexLog
import com.apex.sdk.common.ApexSuite
import com.apex.sdk.common.BridgeResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Rage Mode APK 的 Bridge 实现 — 路由全部 40+ 方法。
 */
class RageBridgeImpl(
    private val facade: RageServiceFacade
) : IApkBridgeInternal {

    private val json = Json { ignoreUnknownKeys = true }

    override fun invoke(method: String, argsJson: String): String {
        ApexLog.d(ApexSuite.ApkId.RAGE, "[RageBridge] invoke: $method")
        val args = try {
            json.parseToJsonElement(argsJson) as? JsonObject ?: JsonObject(emptyMap())
        } catch (_: Throwable) { JsonObject(emptyMap()) }

        return runCatching {
            runBlocking {
                when (method) {
                    // ===== 初始化 =====
                    "rage/initialize" -> {
                        val preset = args["preset"]?.jsonPrimitive?.content?.let {
                            runCatching { RagePreset.valueOf(it) }.getOrDefault(RagePreset.BALANCED)
                        } ?: RagePreset.BALANCED
                        buildResult(facade.initialize(preset = preset)) { JsonObject(emptyMap()) }
                    }

                    // ===== 会话管理 =====
                    "rage/startSession" -> {
                        val task = args["taskDescription"]?.jsonPrimitive?.content ?: ""
                        val skill = args["skillId"]?.jsonPrimitive?.content
                        val preset = args["preset"]?.jsonPrimitive?.content?.let {
                            runCatching { RagePreset.valueOf(it) }.getOrDefault(RagePreset.BALANCED)
                        } ?: RagePreset.BALANCED
                        buildResult(facade.startSession(task, skill, preset)) { JsonPrimitive(it) }
                    }
                    "rage/executeTask" -> {
                        val sessionId = args["sessionId"]?.jsonPrimitive?.content ?: ""
                        buildResult(facade.executeTask(sessionId)) { it.toJson() }
                    }
                    "rage/pauseSession" -> {
                        val sessionId = args["sessionId"]?.jsonPrimitive?.content ?: ""
                        buildResult(facade.pauseSession(sessionId)) { JsonPrimitive(it) }
                    }
                    "rage/resumeSession" -> {
                        val sessionId = args["sessionId"]?.jsonPrimitive?.content ?: ""
                        buildResult(facade.resumeSession(sessionId)) { JsonPrimitive(it) }
                    }
                    "rage/stopSession" -> {
                        val sessionId = args["sessionId"]?.jsonPrimitive?.content ?: ""
                        buildResult(facade.stopSession(sessionId)) { JsonPrimitive(it) }
                    }
                    "rage/listSessions" -> {
                        val list = facade.listSessions()
                        buildJsonObject {
                            put("success", true)
                            put("count", list.size)
                            put("sessions", list.joinToString("\n") {
                                "${it.sessionId}: ${it.taskName} (paused=${it.paused}, completed=${it.completed})"
                            })
                        }.toString()
                    }

                    // ===== 4 种执行模式（P0 增强） =====
                    "rage/executeBatch" -> {
                        val tasks = args["tasks"]?.jsonPrimitive?.content?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
                        val skillId = args["skillId"]?.jsonPrimitive?.content
                        val preset = args["preset"]?.jsonPrimitive?.content?.let {
                            runCatching { RagePreset.valueOf(it) }.getOrDefault(RagePreset.BALANCED)
                        } ?: RagePreset.BALANCED
                        buildResult(facade.executeBatch(tasks, skillId, preset)) { list ->
                            buildJsonObject {
                                put("count", list.size)
                                put("successCount", list.count { it.success })
                                put("results", list.joinToString("\n") {
                                    "${it.sessionId}: success=${it.success} (${it.executionTimeMs}ms)"
                                })
                            }
                        }
                    }
                    "rage/executeWithDependencyGraph" -> {
                        // tasks 格式："taskId1|desc1\ntaskId2|desc2"
                        // dependencies 格式："taskId1|taskId2;taskId2|taskId3"
                        val tasksStr = args["tasks"]?.jsonPrimitive?.content ?: ""
                        val depsStr = args["dependencies"]?.jsonPrimitive?.content ?: ""
                        val strategy = args["strategy"]?.jsonPrimitive?.content ?: "SKIP_ON_FAILURE"
                        val skillId = args["skillId"]?.jsonPrimitive?.content
                        val tasks = tasksStr.split("\n").filter { it.isNotBlank() }.map { line ->
                            val parts = line.split("|", limit = 2)
                            Pair(parts.getOrNull(0) ?: "", parts.getOrNull(1) ?: "")
                        }
                        val deps = depsStr.split(";").filter { it.isNotBlank() }.map { entry ->
                            val parts = entry.split("|", limit = 2)
                            Pair(parts.getOrNull(0) ?: "", parts.getOrNull(1) ?: "")
                        }
                        buildResult(facade.executeWithDependencyGraph(tasks, deps, strategy, skillId)) { list ->
                            buildJsonObject {
                                put("count", list.size)
                                put("successCount", list.count { it.success })
                                put("skippedCount", list.count { it.skipped })
                                put("results", list.joinToString("\n") {
                                    "${it.taskId}: success=${it.success} skipped=${it.skipped}"
                                })
                            }
                        }
                    }
                    "rage/executeWithChain" -> {
                        val initialTask = args["initialTask"]?.jsonPrimitive?.content ?: ""
                        val stepsStr = args["steps"]?.jsonPrimitive?.content ?: ""
                        val skillId = args["skillId"]?.jsonPrimitive?.content
                        val steps = stepsStr.split("\n").filter { it.isNotBlank() }.map { line ->
                            val parts = line.split("|", limit = 2)
                            ChainStepDto(
                                name = parts.getOrNull(0) ?: "step",
                                description = parts.getOrNull(1) ?: "",
                                skillId = skillId
                            )
                        }
                        buildResult(facade.executeWithChain(initialTask, steps, skillId)) { it.toJson() }
                    }
                    "rage/executeAsync" -> {
                        val taskDesc = args["taskDescription"]?.jsonPrimitive?.content ?: ""
                        val skillId = args["skillId"]?.jsonPrimitive?.content
                        buildResult(facade.executeAsync(taskDesc, skillId)) { JsonPrimitive(it) }
                    }
                    "rage/awaitAsyncTask" -> {
                        val taskId = args["taskId"]?.jsonPrimitive?.content ?: ""
                        val timeoutMs = args["timeoutMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 60_000L
                        buildResult(facade.awaitAsyncTask(taskId, timeoutMs)) { r ->
                            if (r == null) buildJsonObject { put("found", false) }
                            else r.toJson()
                        }
                    }
                    "rage/cancelAsyncTask" -> {
                        val taskId = args["taskId"]?.jsonPrimitive?.content ?: ""
                        val ok = facade.cancelAsyncTask(taskId)
                        buildJsonObject { put("success", ok) }.toString()
                    }

                    // ===== 任务队列（P1 增强） =====
                    "rage/enqueueTask" -> {
                        val desc = args["taskDescription"]?.jsonPrimitive?.content ?: ""
                        val priority = args["priority"]?.jsonPrimitive?.content ?: "NORMAL"
                        val skillId = args["skillId"]?.jsonPrimitive?.content
                        buildResult(facade.enqueueTask(desc, priority, skillId)) { JsonPrimitive(it) }
                    }
                    "rage/cancelQueuedTask" -> {
                        val taskId = args["taskId"]?.jsonPrimitive?.content ?: ""
                        buildResult(facade.cancelQueuedTask(taskId)) { JsonPrimitive(it) }
                    }
                    "rage/peekQueue" -> {
                        buildResult(facade.peekQueue()) { JsonPrimitive(it ?: "") }
                    }
                    "rage/pendingTaskCount" -> {
                        buildResult(facade.pendingTaskCount()) { JsonPrimitive(it) }
                    }
                    "rage/clearQueue" -> {
                        buildResult(facade.clearQueue()) { JsonPrimitive(it) }
                    }
                    "rage/getQueueSnapshot" -> {
                        buildResult(facade.getQueueSnapshot()) { s ->
                            if (s == null) buildJsonObject { put("found", false) }
                            else buildJsonObject {
                                put("pendingCount", s.pendingCount)
                                put("completedCount", s.completedCount)
                                put("failedCount", s.failedCount)
                                put("cancelledCount", s.cancelledCount)
                            }
                        }
                    }

                    // ===== 断点续传（P0 增强） =====
                    "rage/saveCheckpoint" -> {
                        val taskId = args["taskId"]?.jsonPrimitive?.content ?: ""
                        val completedSteps = args["completedSteps"]?.jsonPrimitive?.content?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                        val totalSteps = args["totalSteps"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val intermediateResult = args["intermediateResult"]?.jsonPrimitive?.content
                        buildResult(facade.saveCheckpoint(taskId, completedSteps, totalSteps, intermediateResult)) { JsonPrimitive(it) }
                    }
                    "rage/loadCheckpoint" -> {
                        val taskId = args["taskId"]?.jsonPrimitive?.content ?: ""
                        buildResult(facade.loadCheckpoint(taskId)) { c ->
                            if (c == null) buildJsonObject { put("found", false) }
                            else buildJsonObject {
                                put("found", true)
                                put("taskId", c.taskId)
                                put("completedSteps", c.completedSteps.size)
                                put("totalSteps", c.totalSteps)
                                put("progress", c.progress)
                                put("isComplete", c.isComplete)
                            }
                        }
                    }
                    "rage/resumeFromCheckpoint" -> {
                        val taskId = args["taskId"]?.jsonPrimitive?.content ?: ""
                        buildResult(facade.resumeFromCheckpoint(taskId)) { it.toJson() }
                    }
                    "rage/listCheckpoints" -> {
                        buildResult(facade.listCheckpoints()) { list ->
                            buildJsonObject {
                                put("count", list.size)
                                put("checkpoints", list.joinToString("\n") {
                                    "${it.taskId}: ${it.completedSteps}/${it.totalSteps}"
                                })
                            }
                        }
                    }
                    "rage/listIncompleteTasks" -> {
                        buildResult(facade.listIncompleteTasks()) { list ->
                            buildJsonObject { put("count", list.size) }
                        }
                    }
                    "rage/canResume" -> {
                        val taskId = args["taskId"]?.jsonPrimitive?.content ?: ""
                        buildResult(facade.canResume(taskId)) { JsonPrimitive(it) }
                    }
                    "rage/getResumePoint" -> {
                        val taskId = args["taskId"]?.jsonPrimitive?.content ?: ""
                        buildResult(facade.getResumePoint(taskId)) { JsonPrimitive(it ?: "") }
                    }
                    "rage/deleteCheckpoint" -> {
                        val taskId = args["taskId"]?.jsonPrimitive?.content ?: ""
                        buildResult(facade.deleteCheckpoint(taskId)) { JsonPrimitive(it) }
                    }
                    "rage/clearCheckpoints" -> {
                        buildResult(facade.clearCheckpoints()) { JsonObject(emptyMap()) }
                    }

                    // ===== 技能管理（P2 增强 — 多维查询） =====
                    "rage/listSkills" -> {
                        buildResult(facade.listSkills()) { list ->
                            buildJsonObject {
                                put("count", list.size)
                                put("skills", list.joinToString("\n") {
                                    "${it.skillId}: ${it.skillName} - ${it.description.take(60)}"
                                })
                            }
                        }
                    }
                    "rage/getSkillsByTag" -> {
                        val tag = args["tag"]?.jsonPrimitive?.content ?: ""
                        buildResult(facade.getSkillsByTag(tag)) { list ->
                            buildJsonObject { put("count", list.size) }
                        }
                    }
                    "rage/getSkillsByCapability" -> {
                        val cap = args["capability"]?.jsonPrimitive?.content ?: ""
                        buildResult(facade.getSkillsByCapability(cap)) { list ->
                            buildJsonObject { put("count", list.size) }
                        }
                    }
                    "rage/loadSkill" -> {
                        // 注意：IBurstSkill 实例化需要业务侧注入，这里返回提示
                        buildJsonObject {
                            put("success", false)
                            put("errorMessage", "loadSkill 需要通过 TypedServiceRegistry 直接调用 Facade，无法通过 Bridge 传递 IBurstSkill 实例")
                        }.toString()
                    }
                    "rage/unloadSkill" -> {
                        val skillId = args["skillId"]?.jsonPrimitive?.content ?: ""
                        buildResult(facade.unloadSkill(skillId)) { JsonPrimitive(it) }
                    }
                    "rage/getSkillCount" -> {
                        buildResult(facade.getSkillCount()) { JsonPrimitive(it) }
                    }
                    "rage/isSkillLoaded" -> {
                        val skillId = args["skillId"]?.jsonPrimitive?.content ?: ""
                        buildResult(facade.isSkillLoaded(skillId)) { JsonPrimitive(it) }
                    }

                    // ===== 预设与配置（P2 增强） =====
                    "rage/switchPreset" -> {
                        val presetName = args["preset"]?.jsonPrimitive?.content ?: "BALANCED"
                        val preset = runCatching { RagePreset.valueOf(presetName) }.getOrDefault(RagePreset.BALANCED)
                        buildResult(facade.switchPreset(preset)) { JsonObject(emptyMap()) }
                    }
                    "rage/updateConfig" -> {
                        val maxConcurrency = args["maxConcurrency"]?.jsonPrimitive?.content?.toIntOrNull()
                        val defaultTimeoutMs = args["defaultTimeoutMs"]?.jsonPrimitive?.content?.toLongOrNull()
                        val enableAdaptive = args["enableAdaptiveOptimization"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                        val enableMetrics = args["enableMetricsCollection"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                        val memoryBudgetMb = args["memoryBudgetMb"]?.jsonPrimitive?.content?.toIntOrNull()
                        buildResult(facade.updateConfig(maxConcurrency, defaultTimeoutMs, enableAdaptive, enableMetrics, memoryBudgetMb)) { JsonObject(emptyMap()) }
                    }
                    "rage/getCurrentConfig" -> {
                        buildResult(facade.getCurrentConfig()) { c ->
                            buildJsonObject {
                                put("maxConcurrency", c.maxConcurrency)
                                put("defaultTimeoutMs", c.defaultTimeoutMs)
                                put("enableAdaptiveOptimization", c.enableAdaptiveOptimization)
                                put("enableMetricsCollection", c.enableMetricsCollection)
                                put("memoryBudgetMb", c.memoryBudgetMb)
                                put("preset", c.preset)
                            }
                        }
                    }

                    // ===== 指标与状态（P1 增强） =====
                    "rage/getMetrics" -> {
                        val m = facade.getMetrics()
                        if (m != null) {
                            buildJsonObject {
                                put("success", true)
                                put("totalTasks", m.totalTasks)
                                put("successfulTasks", m.successfulTasks)
                                put("failedTasks", m.failedTasks)
                                put("cancelledTasks", m.cancelledTasks)
                                put("averageExecutionTimeMs", m.averageExecutionTimeMs)
                                put("successRate", m.successRate)
                                put("currentConcurrency", m.currentConcurrency)
                                put("peakConcurrency", m.peakConcurrency)
                                put("totalTokensProcessed", m.totalTokensProcessed)
                                put("totalMemoryUsedMb", m.totalMemoryUsedMb)
                            }.toString()
                        } else {
                            buildJsonObject { put("success", false); put("errorMessage", "BurstMode not initialized") }.toString()
                        }
                    }
                    "rage/observeNextMetrics" -> {
                        buildResult(facade.observeNextMetrics()) { m ->
                            if (m == null) buildJsonObject { put("found", false) }
                            else buildJsonObject {
                                put("totalTasks", m.totalTasks)
                                put("successRate", m.successRate)
                                put("currentConcurrency", m.currentConcurrency)
                            }
                        }
                    }
                    "rage/resetMetrics" -> {
                        buildResult(facade.resetMetrics()) { JsonObject(emptyMap()) }
                    }
                    "rage/getKernelState" -> {
                        buildJsonObject { put("success", true); put("state", facade.getKernelState()) }.toString()
                    }
                    "rage/getHealthStatus" -> {
                        buildResult(facade.getHealthStatus()) { h ->
                            buildJsonObject {
                                put("healthy", h.healthy)
                                put("usedMemoryMb", h.usedMemoryMb)
                                put("currentConcurrency", h.currentConcurrency)
                                put("maxConcurrency", h.maxConcurrency)
                                put("shouldDegrade", h.shouldDegrade)
                            }
                        }
                    }

                    // ===== 基础设施（P2 增强） =====
                    "rage/clearResultCache" -> {
                        val prefix = args["prefix"]?.jsonPrimitive?.content
                        buildResult(facade.clearResultCache(prefix)) { JsonPrimitive(it) }
                    }
                    "rage/getResultCacheStats" -> {
                        buildResult(facade.getResultCacheStats()) { s ->
                            buildJsonObject {
                                put("size", s.size)
                                put("hitCount", s.hitCount)
                                put("missCount", s.missCount)
                                put("hitRate", s.hitRate)
                            }
                        }
                    }
                    "rage/setSkillSelectionStrategy" -> {
                        val strategy = args["strategy"]?.jsonPrimitive?.content ?: "priority"
                        buildResult(facade.setSkillSelectionStrategy(strategy)) { JsonObject(emptyMap()) }
                    }

                    // ===== AR/VR 可视化 =====
                    "rage/enableSpatialVisualization" -> {
                        buildResult(facade.enableSpatialVisualization()) { JsonPrimitive(it) }
                    }

                    // ===== 关闭 =====
                    "rage/shutdown" -> {
                        buildResult(facade.shutdown()) { JsonObject(emptyMap()) }
                    }

                    else -> errorResponse("unknown method: $method")
                }
            }
        }.getOrElse { t -> errorResponse(t.message ?: t.javaClass.simpleName) }
    }

    override fun invokeAsync(method: String, argsJson: String, onProgress: (Int, String) -> Unit): String {
        onProgress(50, "executing")
        return invoke(method, argsJson)
    }

    override fun openStream(channelName: String): String = channelName
    override fun closeStream(channelName: String) {}

    private fun <T> buildResult(result: BridgeResult<T>, transform: (T) -> JsonObject): String = when (result) {
        is BridgeResult.Success -> buildJsonObject {
            put("success", true)
            put("data", transform(result.value))
        }.toString()
        is BridgeResult.Failure -> buildJsonObject {
            put("success", false)
            put("errorCode", result.error.code)
            put("errorMessage", result.error.message)
        }.toString()
    }

    private fun errorResponse(message: String): String = buildJsonObject {
        put("success", false)
        put("errorMessage", message)
    }.toString()
}

// JSON 扩展
private fun RageExecutionResult.toJson(): JsonObject = buildJsonObject {
    put("sessionId", sessionId)
    put("skillId", skillId)
    put("success", success)
    put("output", output)
    put("errorMessage", errorMessage ?: "")
    put("executionTimeMs", executionTimeMs)
    put("tokensProcessed", tokensProcessed)
}
