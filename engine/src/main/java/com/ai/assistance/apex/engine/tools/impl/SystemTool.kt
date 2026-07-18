package com.ai.assistance.apex.engine.tools.impl

import android.content.Context
import android.os.Build
import android.util.Log
import com.ai.assistance.apex.engine.model.ExecutionResult
import com.ai.assistance.apex.engine.tools.Tool
import com.ai.assistance.apex.engine.tools.errorResult
import com.ai.assistance.apex.engine.tools.successResult
import com.ai.assistance.aiterminal.terminal.ai.CommandRiskAssessor
import com.ai.assistance.aiterminal.terminal.ai.DangerousCommandPatterns
import com.ai.assistance.aiterminal.terminal.ai.LLMAPI
import com.ai.assistance.aiterminal.terminal.ai.RiskLevel
import com.ai.assistance.aiterminal.terminal.ai.TerminalContext
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class SystemTool(private val context: Context? = null) : Tool {
    override val name = "system"
    override val description = "System information and operations tool"
    override val category = "system"
    override val parameters = arrayOf("command")
    override val requiresRoot = false

    override fun execute(args: String): ExecutionResult {
        val command = args.trim().lowercase()

        return when {
            command.startsWith("info") -> getSystemInfo()
            command.startsWith("uptime") -> getUptime()
            command.startsWith("mem") -> getMemoryInfo()
            command.startsWith("disk") -> getDiskInfo()
            command.startsWith("cpu") -> getCpuInfo()
            else -> {
                if (command.isNotEmpty()) {
                    executeShellCommand(command)
                } else {
                    errorResult("Unknown command: $command")
                }
            }
        }
    }

    private fun getSystemInfo(): ExecutionResult {
        val info = buildString {
            appendLine("=== System Information ===")
            appendLine("OS Version: ${Build.VERSION.RELEASE}")
            appendLine("API Level: ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Board: ${Build.BOARD}")
            appendLine("Hardware: ${Build.HARDWARE}")
            appendLine("CPU ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("Bootloader: ${Build.BOOTLOADER}")
            appendLine("Build ID: ${Build.ID}")
            appendLine("Build Fingerprint: ${Build.FINGERPRINT}")
        }

        return ExecutionResult().apply {
            exitCode = 0
            output = info
            success = true
        }
    }

    private fun getUptime(): ExecutionResult {
        val uptimeMs = android.os.SystemClock.uptimeMillis()
        val uptimeSeconds = uptimeMs / 1000

        val days = uptimeSeconds / 86400
        val hours = (uptimeSeconds % 86400) / 3600
        val minutes = (uptimeSeconds % 3600) / 60
        val seconds = uptimeSeconds % 60

        val result = buildString {
            appendLine("=== System Uptime ===")
            appendLine("Uptime: $uptimeMs ms")
            appendLine("Formatted: $days days, $hours hours, $minutes minutes, $seconds seconds")
        }

        return ExecutionResult().apply {
            exitCode = 0
            output = result
            success = true
        }
    }

    private fun getMemoryInfo(): ExecutionResult {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()

        val result = buildString {
            appendLine("=== Memory Information ===")
            appendLine("Total Memory: ${formatBytes(totalMemory)}")
            appendLine("Used Memory: ${formatBytes(usedMemory)}")
            appendLine("Free Memory: ${formatBytes(freeMemory)}")
            appendLine("Max Memory: ${formatBytes(maxMemory)}")
            appendLine("Available Processors: ${runtime.availableProcessors()}")
        }

        return ExecutionResult().apply {
            exitCode = 0
            output = result
            success = true
        }
    }

    private fun getDiskInfo(): ExecutionResult {
        val result = buildString {
            appendLine("=== Disk Information ===")
            appendLine("Data Directory: ${android.os.Environment.getDataDirectory().absolutePath}")
            appendLine("External Storage: ${android.os.Environment.getExternalStorageDirectory().absolutePath}")
        }

        return ExecutionResult().apply {
            exitCode = 0
            output = result
            success = true
        }
    }

    private fun getCpuInfo(): ExecutionResult {
        return try {
            val process = Runtime.getRuntime().exec("cat /proc/cpuinfo")
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(10, TimeUnit.SECONDS)

            ExecutionResult().apply {
                exitCode = 0
                this.output = output
                success = true
            }
        } catch (e: Exception) {
            errorResult(e.message ?: "Failed to get CPU info")
        }
    }

    private fun executeShellCommand(command: String): ExecutionResult {
        // === 风险评估拦截（Task E）===
        // CRITICAL/HIGH 直接拒绝；MEDIUM 仅记日志后放行；LOW/无匹配放行。
        val riskLevel = assessCommandRisk(command)
        when (riskLevel) {
            RiskLevel.CRITICAL, RiskLevel.HIGH -> {
                Log.w(TAG, "Command blocked by risk assessor (level=$riskLevel): $command")
                return errorResult("Command blocked by risk assessor (level=$riskLevel): $command")
            }
            RiskLevel.MEDIUM -> {
                Log.w(TAG, "MEDIUM-risk command proceeding: $command")
            }
            RiskLevel.LOW -> { /* safe to proceed */ }
        }

        return try {
            val process = Runtime.getRuntime().exec(command)
            val exitCode = if (process.waitFor(30, TimeUnit.SECONDS)) process.exitValue() else { process.destroyForcibly(); -1 }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }

            ExecutionResult().apply {
                this.exitCode = exitCode
                this.output = output
                this.error = error
                success = exitCode == 0
            }
        } catch (e: Exception) {
            errorResult(e.message ?: "Command execution failed")
        }
    }

    /**
     * 调用 :ai-terminal 的 [CommandRiskAssessor] 评估命令风险等级。
     * - 当 [context] 非空时使用完整 assessor（覆盖正则模式匹配 + 启发式分析），
     *   通过 runBlocking 阻塞调用 [CommandRiskAssessor.assessRisk] —— 该方法本身不调用 LLM。
     * - 当 [context] 为空时退化为同步的 [DangerousCommandPatterns.matchPattern]。
     */
    private fun assessCommandRisk(command: String): RiskLevel {
        val ctx = context ?: return DangerousCommandPatterns.matchPattern(command)?.riskLevel ?: RiskLevel.LOW
        val noOpLlmApi = object : LLMAPI {
            override suspend fun generate(prompt: String): String = ""
        }
        val assessor = CommandRiskAssessor(ctx, noOpLlmApi)
        // 传入空的 TerminalContext() 以跳过昂贵的 TerminalContextCollector.collectContext() 调用；
        // assessRisk() 内部不会调用 llmApi（只有 assessWithAI 会），所以 noOp LLM 是安全的。
        val result = runBlocking { assessor.assessRisk(command, TerminalContext()) }
        return result.level
    }

    private companion object {
        private const val TAG = "SystemTool"
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$bytes bytes"
        }
    }

}