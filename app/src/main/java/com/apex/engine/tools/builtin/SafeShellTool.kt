package com.apex.engine.tools.builtin

import android.content.Context
import android.util.Log
import com.ai.assistance.aiterminal.terminal.ai.CommandRiskAssessor
import com.ai.assistance.aiterminal.terminal.ai.LLMAPI
import com.ai.assistance.aiterminal.terminal.ai.RiskLevel
import com.ai.assistance.aiterminal.terminal.ai.TerminalContext
import com.apex.core.model.ApexTool
import com.apex.core.model.ToolCategory
import com.apex.core.model.ToolMetadata
import com.apex.core.model.ToolResult
import com.apex.sdk.bridge.ApexClient
import com.apex.sdk.common.BridgeResult

/**
 * 安全 Shell 工具（Task E）
 *
 * 在执行任意 shell 命令前先调用 :ai-terminal 的 [CommandRiskAssessor.assessRisk] 评估风险：
 *  - CRITICAL / HIGH  → 直接拒绝，返回 [ToolResult.Error]
 *  - MEDIUM           → 记录警告日志后委托执行
 *  - LOW / 无匹配     → 委托执行
 *
 * 通过的命令委托给 [ApexClient.engine.executeShell]，由 :engine 进程实际执行。
 * 注意：:engine 进程内的 SystemTool / ShizukuManager 也各自再做一次风险评估，
 * 形成纵深防御（defense in depth）。
 */
class SafeShellTool(private val context: Context) : ApexTool {

    override val metadata = ToolMetadata(
        id = "safe_shell",
        name = "Safe Shell",
        description = "Execute a shell command after risk assessment. HIGH/CRITICAL commands are blocked.",
        category = ToolCategory.SYSTEM,
        isReadOnly = false
    )

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val command = arguments["command"]?.toString()
        if (command.isNullOrEmpty()) {
            return ToolResult.Error("Missing or empty 'command' argument")
        }

        // === 第 1 道风险评估（在 :app 进程内）===
        // noOp LLM 是安全的：assessRisk() 本身不调用 LLM（只有 assessWithAI 才会）。
        val noOpLlmApi = object : LLMAPI {
            override suspend fun generate(prompt: String): String = ""
        }
        val assessor = CommandRiskAssessor(context, noOpLlmApi)
        // 传入空的 TerminalContext() 以跳过昂贵的 TerminalContextCollector.collectContext() 调用。
        val risk = assessor.assessRisk(command, TerminalContext())

        when (risk.level) {
            RiskLevel.CRITICAL, RiskLevel.HIGH -> {
                Log.w(TAG, "Command blocked by risk assessor (level=${risk.level}): $command")
                return ToolResult.Error(
                    "Command blocked by risk assessor (level=${risk.level}): $command"
                )
            }
            RiskLevel.MEDIUM -> {
                Log.w(TAG, "MEDIUM-risk command proceeding: $command")
            }
            RiskLevel.LOW -> { /* safe to proceed */ }
        }

        // === 委托给 :engine 进程执行 ===
        // :engine 端的 SystemTool 会再次做风险评估（第 2 道防线）。
        return when (val r = ApexClient.engine.executeShell(command)) {
            is BridgeResult.Success -> ToolResult.Success(r.value)
            is BridgeResult.Failure -> ToolResult.Error(r.error.message)
        }
    }

    private companion object {
        private const val TAG = "SafeShellTool"
    }
}
