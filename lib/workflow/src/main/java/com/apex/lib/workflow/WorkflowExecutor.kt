package com.apex.lib.workflow

import com.apex.sdk.bridge.ApexClient
import com.apex.sdk.common.ApexLog
import com.apex.sdk.common.ApexSuite
import com.apex.sdk.common.BridgeResult
import com.apex.sdk.common.Trace
import com.apex.sdk.common.bridgeRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * LLM 调用器接口 — 由宿主 APK（:app）注入。
 *
 * 实现应将 [invoke] 转化为实际 LLM 调用（OpenAI / DeepSeek / Claude / 本地模型等），
 * 返回纯文本响应包装为 [BridgeResult.Success]；失败时返回 [BridgeResult.Failure]。
 *
 * 设计动机：:lib:workflow 不应直接依赖任何具体 LLM 客户端 / OkHttp / Android Context，
 * 因此 LLM 调用通过此接口反查到宿主。host APK 应在创建 [WorkflowExecutor] 后立即：
 * ```
 * executor.llmInvoker = LlmInvoker { prompt, systemPrompt, config ->
 *     // 适配 :app 内的 LLMProvider (OpenAICompatProvider)
 *     BridgeResult.Success(provider.complete(prompt, systemPrompt, config))
 * }
 * ```
 */
fun interface LlmInvoker {
    suspend fun invoke(
        prompt: String,
        systemPrompt: String?,
        config: Map<String, Any>?
    ): BridgeResult<String>
}

/**
 * 工作流执行器 — 工作流 APK 的核心。
 *
 * 设计要点：
 *   - DAG 编排：根据 [WorkflowDefinition] 中的节点 + 边构建 DAG
 *   - 节点类型：LLM 调用 / 工具调用 / 条件 / 循环 / 并行 / HTTP / 终端 / 代码
 *   - 节点间数据传递通过 [ExecutionContext.variables]
 *   - 支持断点续跑（持久化 context 到 [CheckpointStore]）
 *
 * 节点处理优先级：
 *   1. [registerHandler] 注册的自定义处理器（按节点类型 simpleName 索引）
 *   2. 内置处理器 — 委托给 [ApexClient]（工具/终端/代码）或注入的 [llmInvoker]（LLM 调用）
 *      或纯 JDK 的 [java.net.HttpURLConnection]（HTTP 请求）
 *
 * 其他 APK（主 APK / 狂暴 APK）可通过 Bridge 调用 [execute]，
 * 由于本库被打包进 [:apk:workflow]，其他 APK 不会重复打包本库。
 */
class WorkflowExecutor {

    private val _events = MutableSharedFlow<WorkflowEvent>(extraBufferCapacity = 128)
    val events: Flow<WorkflowEvent> = _events.asSharedFlow()

    /** 自定义节点处理器 — 业务侧注入。key = WorkflowNodeSpec 子类的 simpleName（如 "LlmCall"）。 */
    private val customHandlers = mutableMapOf<String, suspend (WorkflowNodeSpec, ExecutionContext) -> NodeResult>()

    /**
     * LLM 调用器 — 由宿主 APK 注入。未注入时 LlmCall 节点将返回 failure。
     * @see LlmInvoker
     */
    var llmInvoker: LlmInvoker? = null

    fun registerHandler(nodeType: String, handler: suspend (WorkflowNodeSpec, ExecutionContext) -> NodeResult) {
        customHandlers[nodeType] = handler
    }

    suspend fun execute(
        workflow: WorkflowDefinition,
        initialInputs: Map<String, Any> = emptyMap()
    ): BridgeResult<ExecutionContext> = bridgeRun {

        val traceId = Trace.newId("wf")
        val context = ExecutionContext(
            workflowId = workflow.id,
            traceId = traceId,
            variables = initialInputs.toMutableMap(),
            currentNodeId = workflow.entryNodeId,
            history = mutableListOf()
        )

        val nodeMap = workflow.nodes.associateBy { it.id }
        var currentId: String? = workflow.entryNodeId

        while (currentId != null) {
            val node = nodeMap[currentId] ?: break
            _events.tryEmit(WorkflowEvent.NodeStarted(workflow.id, currentId, traceId))

            val result = executeNode(workflow, node, context)
            context.history.add(NodeExecutionRecord(node.id, System.currentTimeMillis(), result))
            _events.tryEmit(WorkflowEvent.NodeFinished(workflow.id, currentId, result, traceId))

            // 计算下一个节点
            currentId = computeNextNode(workflow, node, result, context)
        }

        _events.tryEmit(WorkflowEvent.WorkflowCompleted(workflow.id, context, traceId))
        context
    }

    private suspend fun executeNode(
        workflow: WorkflowDefinition,
        node: WorkflowNodeSpec,
        context: ExecutionContext
    ): NodeResult {
        // 1) 自定义处理器优先（按节点子类 simpleName 索引）
        val typeName = node::class.simpleName.orEmpty()
        customHandlers[typeName]?.let { handler ->
            ApexLog.d(ApexSuite.ApkId.WORKFLOW, "[Node] custom handler '$typeName': ${node.displayName}")
            return runCatching { handler(node, context) }.getOrElse { t ->
                NodeResult(
                    success = false,
                    output = mapOf("error" to (t.message ?: "custom handler '$typeName' failed"))
                )
            }
        }

        // 2) 内置处理器分发
        return when (node) {
            is WorkflowNodeSpec.LlmCall -> executeLlmCall(node, context)
            is WorkflowNodeSpec.ToolCall -> executeToolCall(node, context)
            is WorkflowNodeSpec.Condition -> executeCondition(node, context)
            is WorkflowNodeSpec.Loop -> executeLoop(workflow, node, context)
            is WorkflowNodeSpec.Parallel -> executeParallel(workflow, node, context)
            is WorkflowNodeSpec.HttpRequest -> executeHttpRequest(node, context)
            is WorkflowNodeSpec.Terminal -> executeTerminal(node, context)
            is WorkflowNodeSpec.Code -> executeCode(node, context)
        }
    }

    // ============================================================
    // 内置节点处理器
    // ============================================================

    private suspend fun executeLlmCall(node: WorkflowNodeSpec.LlmCall, context: ExecutionContext): NodeResult {
        val invoker = llmInvoker ?: run {
            ApexLog.w(ApexSuite.ApkId.WORKFLOW, "[Node:LlmCall] llmInvoker not configured")
            return NodeResult(success = false, output = mapOf("error" to "llmInvoker not configured"))
        }
        val prompt = interpolate(node.promptTemplate, context)
        val config: Map<String, Any> = buildMap {
            if (node.modelProvider.isNotEmpty()) put("modelProvider", node.modelProvider)
            if (node.modelName.isNotEmpty()) put("modelName", node.modelName)
        }
        ApexLog.d(ApexSuite.ApkId.WORKFLOW, "[Node:LlmCall] ${node.displayName} (prompt ${prompt.length} chars)")
        return when (val r = invoker.invoke(prompt, null, config)) {
            is BridgeResult.Success -> NodeResult(
                success = true,
                output = mapOf("response" to r.value, "prompt" to prompt)
            )
            is BridgeResult.Failure -> NodeResult(
                success = false,
                output = mapOf(
                    "error" to r.error.message,
                    "errorCode" to r.error.code
                )
            )
        }
    }

    private suspend fun executeToolCall(node: WorkflowNodeSpec.ToolCall, context: ExecutionContext): NodeResult {
        val args = interpolate(node.argumentsJson, context)
        ApexLog.d(ApexSuite.ApkId.WORKFLOW, "[Node:ToolCall] ${node.toolName} args=${args.take(200)}")
        return when (val r = ApexClient.engine.executeTool(node.toolName, args)) {
            is BridgeResult.Success -> NodeResult(
                success = true,
                output = mapOf("result" to r.value, "toolName" to node.toolName)
            )
            is BridgeResult.Failure -> NodeResult(
                success = false,
                output = mapOf(
                    "error" to r.error.message,
                    "toolName" to node.toolName,
                    "errorCode" to r.error.code
                )
            )
        }
    }

    private fun executeCondition(node: WorkflowNodeSpec.Condition, context: ExecutionContext): NodeResult {
        val interpolated = interpolate(node.expression, context)
        val truthy = evaluateExpression(interpolated)
        val branch = if (truthy) node.trueBranch else node.falseBranch
        ApexLog.d(
            ApexSuite.ApkId.WORKFLOW,
            "[Node:Condition] '$interpolated' -> ${if (truthy) "true" else "false"} -> $branch"
        )
        return NodeResult(
            success = true,
            output = mapOf(
                "branch" to if (truthy) "true" else "false",
                "expression" to interpolated
            ),
            nextNodeId = branch
        )
    }

    /**
     * Loop 节点：对 body 节点执行 [WorkflowNodeSpec.Loop.iterations] 次。
     *
     * 限制：body 节点的 [NodeResult.nextNodeId] 不被 honor（不递归子 DAG），
     * 每次迭代只调用一次 body 节点。若 body 是 LlmCall/ToolCall/HttpRequest 等叶子节点
     * （最常见用法），则行为符合预期。
     */
    private suspend fun executeLoop(
        workflow: WorkflowDefinition,
        node: WorkflowNodeSpec.Loop,
        context: ExecutionContext
    ): NodeResult {
        val nodeMap = workflow.nodes.associateBy { it.id }
        val bodyNode = nodeMap[node.bodyNodeId] ?: run {
            ApexLog.w(ApexSuite.ApkId.WORKFLOW, "[Node:Loop] body node '${node.bodyNodeId}' not found")
            return NodeResult(
                success = false,
                output = mapOf("error" to "Loop body node '${node.bodyNodeId}' not found")
            )
        }
        val maxIter = node.iterations.coerceAtLeast(0)
        var executed = 0
        var lastResult: NodeResult = NodeResult(success = true, output = mapOf("iterations" to 0))
        for (i in 0 until maxIter) {
            val r = executeNode(workflow, bodyNode, context)
            context.history.add(NodeExecutionRecord(bodyNode.id, System.currentTimeMillis(), r))
            executed++
            lastResult = r
            if (!r.success) {
                ApexLog.w(
                    ApexSuite.ApkId.WORKFLOW,
                    "[Node:Loop] body failed at iteration ${i + 1}/$maxIter: ${r.output["error"] ?: "unknown"}"
                )
                break
            }
        }
        return NodeResult(
            success = lastResult.success,
            output = mapOf(
                "iterations" to executed,
                "lastOutput" to lastResult.output
            )
        )
    }

    /**
     * Parallel 节点：并发执行所有 [WorkflowNodeSpec.Parallel.branchNodeIds]。
     *
     * 并发安全：每个分支获得 [ExecutionContext.variables] 的快照副本，
     * 互不干扰；执行完毕后 merge 回主 context（按分支顺序 synchronized 写入）。
     */
    private suspend fun executeParallel(
        workflow: WorkflowDefinition,
        node: WorkflowNodeSpec.Parallel,
        context: ExecutionContext
    ): NodeResult = coroutineScope {
        val nodeMap = workflow.nodes.associateBy { it.id }
        val deferred = node.branchNodeIds.map { branchId ->
            async {
                val branchNode = nodeMap[branchId]
                if (branchNode == null) {
                    ApexLog.w(ApexSuite.ApkId.WORKFLOW, "[Node:Parallel] branch '$branchId' not found")
                    branchId to NodeResult(
                        success = false,
                        output = mapOf("error" to "branch node '$branchId' not found")
                    )
                } else {
                    val branchCtx = ExecutionContext(
                        workflowId = context.workflowId,
                        traceId = context.traceId,
                        variables = context.variables.toMap().toMutableMap(),
                        currentNodeId = branchId,
                        history = mutableListOf()
                    )
                    val r = executeNode(workflow, branchNode, branchCtx)
                    synchronized(context.variables) {
                        branchCtx.variables.forEach { (k, v) -> context.variables[k] = v }
                    }
                    branchId to r
                }
            }
        }
        val results: Map<String, NodeResult> = deferred.awaitAll().toMap()
        val allSuccess = results.values.all { it.success }
        NodeResult(
            success = allSuccess,
            output = mapOf("branches" to results.mapValues { it.value.output })
        )
    }

    private suspend fun executeHttpRequest(node: WorkflowNodeSpec.HttpRequest, context: ExecutionContext): NodeResult {
        val url = interpolate(node.url, context)
        val method = node.method.uppercase().ifBlank { "GET" }
        val headers: Map<String, String> = parseJsonStringMap(node.headersJson)
        val body = interpolate(node.bodyJson, context)
        ApexLog.d(ApexSuite.ApkId.WORKFLOW, "[Node:HttpRequest] $method $url")
        return performHttpRequest(method, url, headers, body)
    }

    private suspend fun executeTerminal(node: WorkflowNodeSpec.Terminal, context: ExecutionContext): NodeResult {
        val command = interpolate(node.command, context)
        ApexLog.d(ApexSuite.ApkId.WORKFLOW, "[Node:Terminal] $command")
        return when (val r = ApexClient.engine.executeShell(command)) {
            is BridgeResult.Success -> NodeResult(
                success = true,
                output = mapOf("stdout" to r.value, "command" to command)
            )
            is BridgeResult.Failure -> NodeResult(
                success = false,
                output = mapOf(
                    "error" to r.error.message,
                    "command" to command,
                    "errorCode" to r.error.code
                )
            )
        }
    }

    private suspend fun executeCode(node: WorkflowNodeSpec.Code, context: ExecutionContext): NodeResult {
        val source = interpolate(node.source, context)
        val args = buildJsonObject {
            put("language", node.language)
            put("source", source)
        }.toString()
        ApexLog.d(
            ApexSuite.ApkId.WORKFLOW,
            "[Node:Code] (${node.language}) ${source.take(80).replace("\n", " ")}"
        )
        return when (val r = ApexClient.engine.executeTool("code_execution", args)) {
            is BridgeResult.Success -> NodeResult(
                success = true,
                output = mapOf("result" to r.value, "language" to node.language)
            )
            is BridgeResult.Failure -> NodeResult(
                success = false,
                output = mapOf(
                    "error" to r.error.message,
                    "language" to node.language,
                    "errorCode" to r.error.code
                )
            )
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    /** ${key} -> context.variables[key] 插值。缺失的 key 保持原样。 */
    private fun interpolate(template: String, context: ExecutionContext): String {
        if (!template.contains("\${")) return template
        val pattern = Regex("""\$\{([a-zA-Z_][a-zA-Z0-9_.\-]*)\}""")
        return pattern.replace(template) { mr ->
            val key = mr.groupValues[1]
            context.variables[key]?.toString() ?: mr.value
        }
    }

    /**
     * 简易表达式求值器（用于 Condition/Loop）。
     *
     * 支持的语法（操作符前后必须有空格）：
     *   - `lhs == rhs` / `lhs != rhs`        字符串相等比较
     *   - `lhs contains rhs`                  子串包含（忽略大小写）
     *   - `lhs startsWith rhs`               前缀匹配（忽略大小写）
     *   - `lhs endsWith rhs`                 后缀匹配（忽略大小写）
     *
     * 无操作符时按 truthiness 判定：
     *   - "true"/"1"/"yes"/"y"/"ok" -> true
     *   - "false"/"0"/"no"/"n"/""/"null" -> false
     *   - 其他非空字符串 -> true
     *
     * 调用前请先 [interpolate] 完成 ${key} 替换。
     */
    private fun evaluateExpression(expr: String): Boolean {
        val e = expr.trim()
        if (e.isEmpty()) return false
        val operators = listOf(" contains ", " startsWith ", " endsWith ", " == ", " != ")
        for (op in operators) {
            val idx = e.indexOf(op)
            if (idx > 0) {
                val lhs = e.substring(0, idx).trim().trim('"').trim('\'')
                val rhs = e.substring(idx + op.length).trim().trim('"').trim('\'')
                return when (op.trim()) {
                    "==" -> lhs == rhs
                    "!=" -> lhs != rhs
                    "contains" -> lhs.contains(rhs, ignoreCase = true)
                    "startsWith" -> lhs.startsWith(rhs, ignoreCase = true)
                    "endsWith" -> lhs.endsWith(rhs, ignoreCase = true)
                    else -> false
                }
            }
        }
        return when (e.lowercase()) {
            "true", "1", "yes", "y", "ok" -> true
            "false", "0", "no", "n", "null" -> false
            else -> true
        }
    }

    /** 解析 JSON 对象为 Map<String,String>；解析失败返回空 map（不抛异常）。 */
    private fun parseJsonStringMap(json: String): Map<String, String> {
        if (json.isBlank() || json.trim() == "{}") return emptyMap()
        return runCatching {
            val obj = JSON_FORMAT.parseToJsonElement(json).jsonObject
            obj.entries.associate { (k, v) -> k to v.jsonPrimitive.content }
        }.getOrElse { t ->
            ApexLog.w(
                ApexSuite.ApkId.WORKFLOW,
                "[Node:HttpRequest] failed to parse headers '$json': ${t.message}"
            )
            emptyMap()
        }
    }

    /**
     * 用 JDK [HttpURLConnection] 执行 HTTP 请求。在 [Dispatchers.IO] 上运行。
     *
     * 支持 GET/POST/PUT/PATCH/DELETE；可配置 headers + body。
     * 响应体最多读取 10KB（超出截断），避免大响应把内存打爆。
     */
    private suspend fun performHttpRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String
    ): NodeResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (java.net.URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                val writeBody = method in METHODS_WITH_BODY && body.isNotBlank()
                if (writeBody) {
                    doOutput = true
                    if (getRequestProperty("Content-Type") == null) {
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                }
            }
            if (conn.doOutput) {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { w ->
                    w.write(body)
                    w.flush()
                }
            }
            val status = conn.responseCode
            val bodyStr = buildString {
                val stream = if (status in 200..399) conn.inputStream else conn.errorStream
                if (stream != null) {
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
                        val buf = CharArray(8192)
                        var read: Int
                        var total = 0
                        while (r.read(buf).also { read = it } != -1) {
                            append(buf, 0, read)
                            total += read
                            if (total >= MAX_RESPONSE_BYTES) {
                                append("\n...(truncated at ${MAX_RESPONSE_BYTES} bytes)")
                                break
                            }
                        }
                    }
                }
            }
            NodeResult(
                success = status in 200..299,
                output = mapOf(
                    "status" to status,
                    "body" to bodyStr,
                    "url" to url,
                    "method" to method
                )
            )
        } catch (t: Throwable) {
            NodeResult(
                success = false,
                output = mapOf(
                    "error" to (t.message ?: t.javaClass.simpleName),
                    "url" to url,
                    "method" to method
                )
            )
        } finally {
            conn?.disconnect()
        }
    }

    private fun computeNextNode(
        workflow: WorkflowDefinition,
        node: WorkflowNodeSpec,
        result: NodeResult,
        @Suppress("UNUSED_PARAMETER") context: ExecutionContext
    ): String? {
        // 显式指定的 next node 优先
        if (result.nextNodeId != null) return result.nextNodeId
        // 否则取第一条出边
        return workflow.edges.firstOrNull { it.from == node.id }?.to
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_RESPONSE_BYTES = 10_000
        private val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")
        private val JSON_FORMAT = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

data class ExecutionContext(
    val workflowId: String,
    val traceId: String,
    val variables: MutableMap<String, Any>,
    var currentNodeId: String,
    val history: MutableList<NodeExecutionRecord>
)

data class NodeResult(
    val success: Boolean,
    val output: Map<String, Any> = emptyMap(),
    val nextNodeId: String? = null
)

data class NodeExecutionRecord(
    val nodeId: String,
    val timestampMs: Long,
    val result: NodeResult
)

sealed class WorkflowEvent {
    data class NodeStarted(val workflowId: String, val nodeId: String, val traceId: String) : WorkflowEvent()
    data class NodeFinished(val workflowId: String, val nodeId: String, val result: NodeResult, val traceId: String) : WorkflowEvent()
    data class WorkflowCompleted(val workflowId: String, val context: ExecutionContext, val traceId: String) : WorkflowEvent()
    data class WorkflowFailed(val workflowId: String, val error: String, val traceId: String) : WorkflowEvent()
}
