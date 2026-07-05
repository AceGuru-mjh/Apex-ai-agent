package com.ai.assistance.aiterminal.terminal.ai

import android.content.Context
import com.apex.data.model.ToolParameterSchema
import com.apex.data.model.ToolPrompt

/**
 * Agent 终端工具提供者 — 把 [AgentTerminalExecutor] 的工具注册到 LLM Tool Call 系统
 *
 * # 使用方式
 *
 * 在配置 LLM 时,把这些工具加入 availableTools:
 *
 * ```
 * val provider = AgentTerminalToolProvider(context)
 * val tools = provider.getAllToolPrompts()
 * // tools 传给 AIService.sendMessage(availableTools = tools)
 * ```
 *
 * 当 LLM 返回 tool_call 时,执行:
 *
 * ```
 * val result = provider.executeToolCall(toolName, argumentsJson)
 * // result 是 JSON 字符串,返回给 LLM 作为 tool_result
 * ```
 */
class AgentTerminalToolProvider(private val context: Context) {

    private val executor = AgentTerminalExecutor(context)

    /**
     * 获取所有终端工具的 ToolPrompt(供 LLM function calling)
     */
    fun getAllToolPrompts(): List<ToolPrompt> = listOf(
        // === 命令执行 ===
        ToolPrompt(
            name = "agent_exec",
            description = "执行单条 shell 命令,返回 stdout、stderr 和 exitCode。适用于 ls/cat/grep/git 等快速命令。",
            parametersStructured = listOf(
                ToolParameterSchema("command", "string", "要执行的 shell 命令", required = true),
                ToolParameterSchema("working_dir", "string", "工作目录,默认用户主目录", required = false),
                ToolParameterSchema("timeout_ms", "number", "超时毫秒,默认 30000", required = false),
            ),
        ),
        ToolPrompt(
            name = "agent_exec_batch",
            description = "批量执行多条命令,共享工作目录。前一条 cd 会影响后一条。适用于多步骤操作。",
            parametersStructured = listOf(
                ToolParameterSchema("commands", "array", "命令列表(JSON 数组)", required = true),
                ToolParameterSchema("working_dir", "string", "初始工作目录", required = false),
                ToolParameterSchema("stop_on_error", "boolean", "失败时是否停止后续命令,默认 true", required = false),
            ),
        ),

        // === 持久会话 ===
        ToolPrompt(
            name = "agent_session_create",
            description = "创建持久会话,后续命令在同一工作目录和环境变量下执行。适用于需要多次 cd 的复杂操作。",
            parametersStructured = listOf(
                ToolParameterSchema("working_dir", "string", "初始工作目录,默认 ~", required = false),
            ),
        ),
        ToolPrompt(
            name = "agent_session_exec",
            description = "在已创建的会话中执行命令,保持工作目录和 export 的环境变量。",
            parametersStructured = listOf(
                ToolParameterSchema("session_id", "string", "会话 ID(create_session 返回)", required = true),
                ToolParameterSchema("command", "string", "要执行的命令", required = true),
                ToolParameterSchema("timeout_ms", "number", "超时毫秒,默认 30000", required = false),
            ),
        ),
        ToolPrompt(
            name = "agent_session_close",
            description = "关闭持久会话,释放资源。",
            parametersStructured = listOf(
                ToolParameterSchema("session_id", "string", "要关闭的会话 ID", required = true),
            ),
        ),

        // === 文件操作 ===
        ToolPrompt(
            name = "agent_file_tree",
            description = "获取目录树 JSON 结构,包含文件名/路径/大小/子节点。适用于快速了解项目结构。",
            parametersStructured = listOf(
                ToolParameterSchema("path", "string", "根路径,默认当前目录", required = false),
                ToolParameterSchema("max_depth", "number", "最大递归深度,默认 3", required = false),
                ToolParameterSchema("include_hidden", "boolean", "是否包含隐藏文件(以.开头),默认 false", required = false),
            ),
        ),
        ToolPrompt(
            name = "agent_grep",
            description = "高级文本搜索,支持正则/文件名过滤/上下文行。比 grep 命令更结构化。",
            parametersStructured = listOf(
                ToolParameterSchema("pattern", "string", "搜索模式(正则表达式)", required = true),
                ToolParameterSchema("path", "string", "搜索路径,默认当前目录", required = false),
                ToolParameterSchema("file_pattern", "string", "文件名过滤,如 *.kt,默认 *", required = false),
                ToolParameterSchema("context_lines", "number", "上下文行数,默认 0", required = false),
                ToolParameterSchema("max_results", "number", "最大结果数,默认 50", required = false),
            ),
        ),

        // === 管道 ===
        ToolPrompt(
            name = "agent_pipeline",
            description = "管道执行多条命令(cmd1 | cmd2 | cmd3),自动处理管道连接。",
            parametersStructured = listOf(
                ToolParameterSchema("commands", "array", "命令列表(按管道顺序)", required = true),
                ToolParameterSchema("working_dir", "string", "工作目录", required = false),
            ),
        ),

        // === 后台执行 ===
        ToolPrompt(
            name = "agent_bg_exec",
            description = "后台执行长命令(如 npm install / gradle build),立即返回 taskId。适用于不阻塞的长时间操作。",
            parametersStructured = listOf(
                ToolParameterSchema("command", "string", "要后台执行的命令", required = true),
                ToolParameterSchema("working_dir", "string", "工作目录", required = false),
            ),
        ),
        ToolPrompt(
            name = "agent_bg_status",
            description = "查询后台任务的执行状态和已输出。Agent 可轮询此接口。",
            parametersStructured = listOf(
                ToolParameterSchema("task_id", "string", "后台任务 ID", required = true),
            ),
        ),
        ToolPrompt(
            name = "agent_bg_cancel",
            description = "取消正在运行的后台任务。",
            parametersStructured = listOf(
                ToolParameterSchema("task_id", "string", "要取消的任务 ID", required = true),
            ),
        ),

        // === 高级执行 ===
        ToolPrompt(
            name = "agent_exec_parallel",
            description = "并行执行多条独立命令(互不依赖)。适合同时检查多个文件、ping多个服务器、获取多种系统信息。每条命令独立工作目录。",
            parametersStructured = listOf(
                ToolParameterSchema("tasks", "array", "任务列表,每项含 id/command/working_dir", required = true),
                ToolParameterSchema("timeout_ms", "number", "总超时毫秒,默认 60000", required = false),
            ),
        ),
        ToolPrompt(
            name = "agent_exec_retry",
            description = "带自动重试的命令执行(指数退避)。适合网络命令/不稳定操作。默认重试3次,退避1s/2s/4s。",
            parametersStructured = listOf(
                ToolParameterSchema("command", "string", "要执行的命令", required = true),
                ToolParameterSchema("working_dir", "string", "工作目录", required = false),
                ToolParameterSchema("max_retries", "number", "最大重试次数,默认 3", required = false),
                ToolParameterSchema("backoff_ms", "number", "退避基数毫秒,默认 1000(指数增长)", required = false),
            ),
        ),

        // === 命令模板 ===
        ToolPrompt(
            name = "agent_template_list",
            description = "列出所有可用命令模板(git_status/git_commit_push/build_gradle/find_process/disk_usage/port_info/file_search/env_check)。",
            parametersStructured = emptyList(),
        ),
        ToolPrompt(
            name = "agent_template_exec",
            description = "执行命令模板(用模板名+参数代替记住复杂命令)。",
            parametersStructured = listOf(
                ToolParameterSchema("template_id", "string", "模板 ID(如 git_status/env_check)", required = true),
                ToolParameterSchema("parameters", "object", "模板参数(键值对)", required = false),
            ),
        ),

        // === 审计与统计 ===
        ToolPrompt(
            name = "agent_audit_logs",
            description = "获取最近的执行审计日志(工具名/命令/成功/耗时/输出大小)。",
            parametersStructured = listOf(
                ToolParameterSchema("limit", "number", "返回条数,默认 50", required = false),
            ),
        ),
        ToolPrompt(
            name = "agent_stats",
            description = "获取执行统计(总执行数/成功率/平均耗时/活跃会话/后台任务)。",
            parametersStructured = emptyList(),
        ),
    )

    /**
     * 执行 LLM 返回的 tool_call
     *
     * @param toolName 工具名(agent_exec / agent_grep / ...)
     * @param argumentsJson LLM 返回的参数 JSON
     * @return 执行结果 JSON(返回给 LLM 作为 tool_result)
     */
    suspend fun executeToolCall(toolName: String, argumentsJson: String): String {
        return executor.executeAgentTool(toolName, argumentsJson)
    }

    /** 获取底层 executor(高级用法) */
    fun getExecutor(): AgentTerminalExecutor = executor

    /** 释放资源 */
    fun shutdown() {
        executor.shutdown()
    }
}
