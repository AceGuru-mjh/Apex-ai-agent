package com.ai.assistance.apex.engine.shizuku

import android.content.Context
import android.util.Log
import com.ai.assistance.aiterminal.terminal.ai.CommandRiskAssessor
import com.ai.assistance.aiterminal.terminal.ai.LLMAPI
import com.ai.assistance.aiterminal.terminal.ai.RiskLevel
import com.ai.assistance.aiterminal.terminal.ai.TerminalContext
import kotlinx.coroutines.runBlocking
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
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()

            CommandResult(exitCode, output, error)
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "Shizuku command execution failed")
        }
    }

    /**
     * 调用 :ai-terminal 的 [CommandRiskAssessor] 评估命令风险等级。
     * 通过 runBlocking 阻塞调用 [CommandRiskAssessor.assessRisk] —— 该方法本身不调用 LLM，
     * 因此 noOp LLM 实现是安全的；传入空的 [TerminalContext] 以跳过昂贵的上下文收集。
     */
    private fun assessCommandRisk(command: String): RiskLevel {
        val noOpLlmApi = object : LLMAPI {
            override suspend fun generate(prompt: String): String = ""
        }
        val assessor = CommandRiskAssessor(context, noOpLlmApi)
        val result = runBlocking { assessor.assessRisk(command, TerminalContext()) }
        return result.level
    }

    data class CommandResult(
        val exitCode: Int,
        val output: String,
        val error: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }
}