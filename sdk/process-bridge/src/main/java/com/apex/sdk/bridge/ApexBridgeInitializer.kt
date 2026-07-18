package com.apex.sdk.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.apex.sdk.common.ApexLog
import com.apex.sdk.common.ApexSuite
import com.apex.sdk.common.ApkIdentity
import com.apex.sdk.common.ApkIdentityRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 自动初始化器 — 利用 ContentProvider 在 Application.onCreate 之前
 * 被系统调用的特性，自动完成 Bridge 注册。
 *
 * **机制**：
 *   Android 启动 APK 时，会先初始化所有 ContentProvider，再调用 Application.onCreate。
 *   Firebase、WorkManager 等都用这个机制做“零侵入”初始化。
 *
 * **本 Provider 做的事**：
 *   1. 注册本 APK 的 [ApkIdentity]
 *   2. 启动 HeartbeatReporter (via reflection)
 *   3. 启动 Watchdog (via reflection)
 *   4. bindService 到主 APK 的 [BridgeRegistryService]
 *
 * 各 APK 无需修改 Application 类，只需在 AndroidManifest 中声明本 Provider。
 * 但 SDK 通过 consumer-rules 已自动声明，业务侧无需任何配置。
 *
 * **主 APK 自身的初始化**：
 *   主 APK 也用本 Provider，但因为它的包名就是 com.apex.agent，
 *   bindService 会指向自己内部的 BridgeRegistryService，
 *   走进程内调用，零延迟。
 */
class ApexBridgeInitializer : ContentProvider() {

    private var heartbeat: Any? = null  // HeartbeatReporter, loaded via reflection if available

    /**
     * 自愈协程 scope — 仅主 APK 使用。订阅 Watchdog.events，APK 死亡时延迟 rebind。
     * SupervisorJob 保证单个自愈任务失败不会取消其他任务。
     */
    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        val apkId = detectApkId(ctx)

        // 注册身份
        ApkIdentityRegistry.register(
            ApkIdentity(
                id = apkId,
                packageName = ctx.packageName,
                displayName = apkId,
                defaultProcess = ApexSuite.MAIN_PROCESS,
                hostsForegroundService = true
            )
        )
        ApexLog.i(apkId, "[BridgeInitializer] onCreate (pid=${android.os.Process.myPid()}, pkg=${ctx.packageName})")

        // 启动心跳和看门狗（通过反射加载，避免循环依赖）
        try {
            val hrClass = Class.forName("com.apex.sdk.watchdog.HeartbeatReporter")
            val hrCtor = hrClass.getConstructor(String::class.java)
            val hr = hrCtor.newInstance(apkId)
            hrClass.getMethod("start").invoke(hr)
            heartbeat = hr

            val wdClass = Class.forName("com.apex.sdk.watchdog.Watchdog")
            // Kotlin object 的方法是实例方法，需要通过 INSTANCE 字段拿 receiver；
            // 旧实现 invoke(null) 会抛 NPE 被外层 catch 静默吞掉，导致 Watchdog 实际未启动。
            val wdInstance = wdClass.getField("INSTANCE").get(null)
            wdClass.getMethod("start").invoke(wdInstance)
        } catch (e: ClassNotFoundException) {
            // watchdog SDK not available in classpath, skip
            ApexLog.d(apkId, "[BridgeInitializer] watchdog SDK not available, skipping")
        } catch (e: Exception) {
            ApexLog.w(apkId, "[BridgeInitializer] watchdog init failed: ${e.message}")
        }

        // 注册包监听（监听套件中 APK 的安装/卸载）
        ApkPackageMonitor.register(ctx)

        // 刷新安装状态快照
        com.apex.sdk.common.ApkDependencyManager.refreshInstallState(ctx)

        // 主 APK 自身：启动 BridgeRegistryService（其他 APK bindService 到此）
        // 非 主 APK：bindService 到主 APK 的 Registry
        if (apkId == ApexSuite.ApkId.MAIN) {
            BridgeRegistryService.startInMainApp(ctx)
            // 同时也 bindToRegistry（bind 到自己）
            BridgeConnection.bindToRegistry(ctx) { connected ->
                ApexLog.i(apkId, "[BridgeInitializer] self-bridge bound = $connected")
            }

            // 订阅 Watchdog.events — 收到 ApkDied 后延迟 2s 重新 bindToRegistry，
            // 触发自愈：远程 APK 重启并重新注册服务时，本主 APK 的 Registry 能及时拿到新 Binder。
            subscribeWatchdogForSelfHealing(ctx)

            // 主 APK 启动时检查必须 APK 是否已安装
            val missing = com.apex.sdk.common.ApkDependencyManager.checkRequiredApks(ctx)
            if (missing.isNotEmpty()) {
                ApexLog.w(apkId, "[BridgeInitializer] ${missing.size} required APKs missing: ${missing.map { it.apkId }}")
                // 发布事件让 UI 显示提示
                SuiteEventBus.publish(
                    type = SuiteEventTypes.APK_REQUIRED_MISSING,
                    payload = mapOf(
                        "missingApkIds" to missing.map { it.apkId },
                        "missingDisplayNames" to missing.map { it.displayName },
                        "summary" to com.apex.sdk.common.ApkDependencyManager.buildMissingApksMessage(missing)
                    ),
                    sourceApk = apkId
                )
            }
        } else {
            BridgeConnection.bindToRegistry(ctx) { connected ->
                ApexLog.i(apkId, "[BridgeInitializer] bridge bound = $connected")
            }
        }

        return true
    }

    /**
     * 反射订阅 [Watchdog.events]。当 [WatchdogEvent.ApkDied] 事件到达时，
     * 延迟 2s 调用 [BridgeConnection.bindToRegistry] 触发自愈重连。
     *
     * 反射调用方式与 [onCreate] 中启动 Watchdog 的方式一致
     * （`Class.forName` + `INSTANCE` field），避免引入
     * `:sdk:process-bridge` → `:sdk:watchdog` 硬依赖。
     *
     * Watchdog 不在 classpath 时静默降级 —— 该 APK 失去自愈能力，
     * 但其他 BridgeRegistry 功能不受影响。
     */
    private fun subscribeWatchdogForSelfHealing(ctx: Context) {
        try {
            val wdClass = Class.forName("com.apex.sdk.watchdog.Watchdog")
            val wdInstance = wdClass.getField("INSTANCE").get(null)
            @Suppress("UNCHECKED_CAST")
            val events = wdClass.getMethod("getEvents").invoke(wdInstance) as? Flow<Any?>
            if (events == null) {
                ApexLog.w(
                    ApexSuite.ApkId.MAIN,
                    "[BridgeInitializer] Watchdog.events is null, self-healing disabled"
                )
                return
            }
            recoveryScope.launch {
                events.collect { event ->
                    handleWatchdogEvent(event, ctx)
                }
            }
            ApexLog.i(
                ApexSuite.ApkId.MAIN,
                "[BridgeInitializer] subscribed to Watchdog.events for self-healing"
            )
        } catch (e: ClassNotFoundException) {
            ApexLog.d(
                ApexSuite.ApkId.MAIN,
                "[BridgeInitializer] Watchdog not on classpath, self-healing disabled"
            )
        } catch (e: Throwable) {
            ApexLog.w(
                ApexSuite.ApkId.MAIN,
                "[BridgeInitializer] subscribe Watchdog failed: ${e.message}"
            )
        }
    }

    /**
     * 处理 Watchdog 事件。只关心 [WatchdogEvent.ApkDied] —— 收到后延迟 2s 调用
     * [BridgeConnection.bindToRegistry] 触发自愈。
     *
     * 通过反射读取事件的 `apkId` 字段（避免编译期依赖 WatchdogEvent 类型）。
     *
     * 延迟理由：远程 APK crash 后通常会被系统或用户重启，2s 给对方 Application.onCreate
     * + ApexBridgeInitializer + registerService 留出时间窗口，避免主 APK 在对方尚未
     * 重新注册时进行无效 lookup。
     */
    private suspend fun handleWatchdogEvent(event: Any?, ctx: Context) {
        if (event == null) return
        // 用 simpleName 判断事件类型，避免对 WatchdogEvent 子类编译期依赖
        if (event.javaClass.simpleName != "ApkDied") return

        val apkId: String = try {
            val field = event.javaClass.getDeclaredField("apkId")
            field.isAccessible = true
            field.get(event) as? String
        } catch (e: Throwable) {
            null
        } ?: return

        ApexLog.w(
            ApexSuite.ApkId.MAIN,
            "[BridgeInitializer] ApkDied: $apkId — scheduling self-heal rebind in 2s"
        )

        // 延迟 2s 等待远程 APK 重启并重新注册服务
        delay(2_000L)

        try {
            BridgeConnection.bindToRegistry(ctx) { connected ->
                ApexLog.i(
                    ApexSuite.ApkId.MAIN,
                    "[BridgeInitializer] self-heal rebind for $apkId: connected=$connected"
                )
            }
        } catch (t: Throwable) {
            ApexLog.e(
                ApexSuite.ApkId.MAIN,
                "[BridgeInitializer] self-heal rebind failed for $apkId",
                t
            )
        }
    }

    private fun detectApkId(ctx: Context): String {
        // 根据包名识别当前是哪个 APK
        return when (ctx.packageName) {
            "com.apex.agent" -> ApexSuite.ApkId.MAIN
            "com.apex.apk.engine" -> ApexSuite.ApkId.ENGINE
            "com.apex.apk.rage" -> ApexSuite.ApkId.RAGE
            "com.apex.apk.multiagent" -> ApexSuite.ApkId.MULTI_AGENT
            "com.apex.apk.workflow" -> ApexSuite.ApkId.WORKFLOW
            "com.apex.apk.market" -> ApexSuite.ApkId.MARKET
            "com.apex.apk.terminal" -> ApexSuite.ApkId.TERMINAL
            "com.apex.apk.workingfiles" -> ApexSuite.ApkId.WORKING_FILES
            // 注：diagnostics 已合并到主 APK，不再有独立包名
            "com.apex.apk.voice" -> ApexSuite.ApkId.VOICE
            else -> "unknown:${ctx.packageName}"
        }
    }

    // ContentProvider 必须实现的方法，本 Provider 不实际提供内容，全部 no-op
    override fun query(uri: Uri, p: Array<String>?, s: String?, sa: Array<String>?, so: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, sa: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, sa: Array<String>?): Int = 0
}
