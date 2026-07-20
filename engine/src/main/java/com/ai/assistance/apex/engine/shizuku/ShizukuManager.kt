package com.ai.assistance.apex.engine.shizuku

import android.content.Context
import android.util.Log
import com.ai.assistance.aiterminal.terminal.ai.DangerousCommandPatterns
import com.ai.assistance.aiterminal.terminal.ai.RiskLevel
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ShizukuManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuManager"
        private var instance: ShizukuManager? = null

        fun getInstance(context: Context): ShizukuManager {
            return instance ?: synchronized(this) {
                instance ?: ShizukuManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var isShizukuAvailable = false
    private var shizukuVersion = -1

    init {
        checkShizukuAvailability()
    }

    private fun checkShizukuAvailability() {
        try {
            shizukuVersion = Shizuku.getVersion()
            isShizukuAvailable = shizukuVersion > 0
        } catch (e: Exception) {
            isShizukuAvailable = false
        }
    }

    fun isAvailable(): Boolean = isShizukuAvailable

    fun getVersion(): Int = shizukuVersion

    fun isPermissionGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            Log.e(TAG, "requestPermission failed", e)
        }
    }

    fun executeCommand(command: String): CommandResult {
        if (!isAvailable()) {
            return CommandResult(-1, "", "Shizuku is not available")
        }

        if (!isPermissionGranted()) {
            return CommandResult(-1, "", "Shizuku permission not granted")
        }

        // === 风险评估拦截（Task E）===
        // CRITICAL/HIGH 直接拒绝；MEDIUM 仅记日志后放行；LOW/无匹配放行。
        val riskLevel = assessCommandRisk(command)
        when (riskLevel) {
            RiskLevel.CRITICAL, RiskLevel.HIGH -> {
                Log.w(TAG, "Command blocked by risk assessor (level=$riskLevel): $command")
                return CommandResult(-1, "", "Command blocked by risk assessor (level=$riskLevel): $command")
            }
            RiskLevel.MEDIUM -> {
                Log.w(TAG, "MEDIUM-risk command proceeding: $command")
            }
            RiskLevel.LOW -> { /* safe to proceed */ }
        }

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val exitCode = if (process.waitFor(30, TimeUnit.SECONDS)) 0 else { process.destroyForcibly(); -1 }
            // PERF-32: 用 .use{} 确保 BufferedReader（底层 FileInputStream）关闭，
            // 避免每次执行命令泄露文件描述符。
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }

            CommandResult(exitCode, output, error)
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "Shizuku command execution failed")
        }
    }

    /**
     * 评估命令风险等级（PERF-27: 同步实现，去除 runBlocking + 对象分配开销）。
     *
     * 旧实现通过 `runBlocking { assessor.assessRisk(...) }` 在调用线程上阻塞协程，
     * 但 `assessRisk` 在不调用 LLM 时只依赖 [DangerousCommandPatterns.matchPattern]
     * 的正则匹配——纯同步 CPU-bound。这里直接走 matchPattern，避免每次执行命令时
     * 构造 LLMAPI/CommandRiskAssessor/TerminalContext + 协程桥接。
     */
    private fun assessCommandRisk(command: String): RiskLevel {
        val pattern = DangerousCommandPatterns.matchPattern(command)
        return pattern?.riskLevel ?: RiskLevel.LOW
    }

    data class CommandResult(
        val exitCode: Int,
        val output: String,
        val error: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }
}