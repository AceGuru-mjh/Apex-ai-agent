package com.apex.sdk.watchdog

import android.os.IBinder
import android.os.RemoteException
import com.apex.sdk.common.ApexLog
import com.apex.sdk.common.ApexSuite
import com.apex.sdk.common.Trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 跨 APK 看门狗。
 *
 * **解决什么问题？**
 *   多 APK 体系中，某个 APK crash 后，调用方不应卡死或反复报错，
 *   而是应该：感知死亡 → 重启连接 → 重新注册服务 → 恢复业务。
 *
 * **实现**（双重检测，互补冗余）：
 *   - **被动 — Binder 死亡通知**：每个注册到 [BridgeRegistryService] 的 IApkBridge，
 *     通过 [linkToBinderDeath] 安装 [IBinder.DeathRecipient]。
 *     当对方 APK 进程死亡、Binder 变 dead 时，内核立即回调 DeathRecipient →
 *     [reportDeath] → [WatchdogEvent.ApkDied]，无需等待心跳超时（毫秒级响应）。
 *   - **主动 — 心跳轮询**：每个承载核心 Service 的 APK 定期向 Watchdog 上报心跳（[heartbeat]）。
 *     超过 [ApexSuite.WATCHDOG_TIMEOUT_MS] 未收到心跳 → [WatchdogEvent.ApkUnresponsive]
 *     （用于检测“进程活着但服务卡死”的 zombie 状态）。
 *
 * **恢复流程**：本类只负责“感知 + 上报”，**不直接负责重连**。
 *   重连（重新 bindService / 重新注册服务）由宿主 APK 订阅 [events] 后自行处理，
 *   例如 [ApexBridgeInitializer] 在主 APK 中订阅 [WatchdogEvent.ApkDied]，
 *   收到事件后延迟 2s 调用 `BridgeConnection.bindToRegistry` 触发自愈。
 *
 * **调用方应订阅 [events]**：
 *   ```kotlin
 *   Watchdog.events.onEach { event ->
 *       when (event) {
 *           is WatchdogEvent.ApkDied -> restartConnection(event.apkId)
 *           is WatchdogEvent.ApkUnresponsive -> reconnect(event.apkId)
 *           is WatchdogEvent.ApkRecovered -> resumeBusiness()
 *       }
 *   }.collect()
 *   ```
 */
object Watchdog {

    private const val TAG_SUB = "Watchdog"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    private val heartbeats = ConcurrentHashMap<String, HeartbeatRecord>()

    /** apkId → 已安装的 DeathRecipient + 对应 Binder，便于 [unlinkBinderDeath] 解除。 */
    private val deathRecipients = ConcurrentHashMap<String, DeathRecipientEntry>()

    private val _events = MutableSharedFlow<WatchdogEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WatchdogEvent> = _events.asSharedFlow()

    private data class HeartbeatRecord(
        val apkId: String,
        var lastHeartbeatMs: Long,
        var deathRecipientRegistered: Boolean = false,
        /** 已被 [reportDeath] 标记死亡；轮询循环跳过，避免反复上报 ApkUnresponsive。 */
        var dead: Boolean = false
    )

    private data class DeathRecipientEntry(
        val recipient: IBinder.DeathRecipient,
        val binder: IBinder
    )

    /**
     * 注册一个 APK 到 Watchdog 监控。
     */
    fun watch(apkId: String) {
        heartbeats[apkId] = HeartbeatRecord(apkId, System.currentTimeMillis())
        ApexLog.i(ApexSuite.ApkId.MAIN, "[$TAG_SUB] watching apk: $apkId")
    }

    /**
     * 取消监控。
     *
     * 顺带清理已安装的 [IBinder.DeathRecipient]，避免泄漏。
     */
    fun unwatch(apkId: String) {
        unlinkBinderDeath(apkId)
        heartbeats.remove(apkId)
    }

    /**
     * 接收来自某 APK 的心跳上报。
     */
    fun heartbeat(apkId: String) {
        val record = heartbeats[apkId]
        if (record != null) {
            record.lastHeartbeatMs = System.currentTimeMillis()
            record.dead = false
        } else {
            // 新 APK 首次心跳，自动加入监控
            heartbeats[apkId] = HeartbeatRecord(apkId, System.currentTimeMillis())
            _events.tryEmit(WatchdogEvent.ApkRegistered(apkId, Trace.newId()))
        }
    }

    /**
     * 主动上报某 APK 已死亡（通常由 [IBinder.DeathRecipient.binderDied] 触发，
     * 也可由调用方主动上报）。
     *
     * 行为：
     *   - 立即发射 [WatchdogEvent.ApkDied]（不等待心跳超时）。
     *   - 在 [HeartbeatRecord] 中把该 APK 标记为 [HeartbeatRecord.dead]，
     *     使轮询循环跳过、[snapshot] 报告 unhealthy。
     *   - 清理 [deathRecipients] 中的 stale entry（内核已经自动 unlink recipient）。
     *
     * **注意**：本方法只负责“感知 + 上报”，**不负责重连**。
     * 重连由宿主 APK 订阅 [events] 后自行实现
     * （参见 [ApexBridgeInitializer] 中的 ApkDied 自愈逻辑）。
     */
    fun reportDeath(apkId: String, reason: String? = null) {
        ApexLog.w(ApexSuite.ApkId.MAIN, "[$TAG_SUB] apk died: $apkId, reason=$reason")
        heartbeats[apkId]?.let {
            it.dead = true
            it.deathRecipientRegistered = false
        }
        // 内核在 binderDied 回调后已自动 unlink recipient，这里只清理本地 map。
        deathRecipients.remove(apkId)
        _events.tryEmit(WatchdogEvent.ApkDied(apkId, reason, Trace.newId()))
    }

    /**
     * 主动上报某 APK 已恢复。
     */
    fun reportRecovered(apkId: String) {
        ApexLog.i(ApexSuite.ApkId.MAIN, "[$TAG_SUB] apk recovered: $apkId")
        heartbeats[apkId]?.let {
            it.dead = false
            it.lastHeartbeatMs = System.currentTimeMillis()
        }
        _events.tryEmit(WatchdogEvent.ApkRecovered(apkId, Trace.newId()))
    }

    /**
     * 为指定 APK 的 [IBinder] 安装 [IBinder.DeathRecipient]。
     *
     * 当对方 APK 进程死亡、Binder 变 dead 时，内核立即回调 DeathRecipient，
     * 进而调用 [reportDeath]，发射 [WatchdogEvent.ApkDied] —— 无需等待心跳超时。
     *
     * 调用方通常是 `BridgeRegistryService.register`：每注册一个 IApkBridge，
     * 立即为其 `asBinder()` 安装死亡监听（通过反射调用本方法，避免硬依赖）。
     *
     * 行为约定：
     *   - 若 [binder] 已经 dead（[IBinder.isBinderAlive] == false），直接调用 [reportDeath]，
     *     不安装监听（`linkToDeath` 在 dead binder 上会抛 [RemoteException]）。
     *   - 若同一 apkId 已有旧 recipient，先 [unlinkBinderDeath] 移除，避免泄漏。
     *   - [RemoteException] 仍可能因竞态抛出（`isBinderAlive` 与 `linkToDeath` 之间 binder 死掉），
     *     此时 catch 后调用 [reportDeath]，效果等同于收到回调。
     */
    fun linkToBinderDeath(apkId: String, binder: IBinder) {
        // 先清理同 apkId 的旧 recipient
        if (deathRecipients.containsKey(apkId)) {
            unlinkBinderDeath(apkId)
        }

        if (!binder.isBinderAlive) {
            ApexLog.w(ApexSuite.ApkId.MAIN, "[$TAG_SUB] binder already dead at linkToBinderDeath: $apkId")
            reportDeath(apkId, "binder already dead at linkToBinderDeath")
            return
        }

        val recipient = IBinder.DeathRecipient {
            ApexLog.w(ApexSuite.ApkId.MAIN, "[$TAG_SUB] binderDied callback: $apkId")
            reportDeath(apkId, "binder died")
        }

        try {
            binder.linkToDeath(recipient, 0)
            deathRecipients[apkId] = DeathRecipientEntry(recipient, binder)
            heartbeats[apkId]?.deathRecipientRegistered = true
            ApexLog.i(ApexSuite.ApkId.MAIN, "[$TAG_SUB] linkToDeath installed: $apkId")
        } catch (e: RemoteException) {
            // 竞态：isBinderAlive 与 linkToDeath 之间 binder 死了
            ApexLog.w(
                ApexSuite.ApkId.MAIN,
                "[$TAG_SUB] linkToDeath RemoteException: $apkId — ${e.message}"
            )
            reportDeath(apkId, "linkToDeath RemoteException: ${e.message}")
        }
    }

    /**
     * 移除指定 APK 的 [IBinder.DeathRecipient]。
     *
     * 调用方通常是 `BridgeRegistryService.unregister`：注销服务前先解除监听，
     * 避免 unregister 后仍触发回调。
     *
     * 幂等：若该 apkId 没有安装 recipient，直接返回，无副作用。
     * 容错：[IBinder.unlinkToDeath] 在 binder 已 dead 或 recipient 已被内核自动移除时
     * 可能返回 false 或抛异常，统一 try/catch 吞掉（仅 debug 日志）。
     */
    fun unlinkBinderDeath(apkId: String) {
        val entry = deathRecipients.remove(apkId) ?: return
        try {
            entry.binder.unlinkToDeath(entry.recipient, 0)
        } catch (e: Throwable) {
            // binder 已 dead / recipient 已被内核自动移除 — 安全忽略
            ApexLog.d(ApexSuite.ApkId.MAIN, "[$TAG_SUB] unlinkToDeath no-op for $apkId: ${e.message}")
        }
        heartbeats[apkId]?.deathRecipientRegistered = false
    }

    /**
     * 启动心跳轮询监控循环。
     *
     * 注意：Binder 死亡通知（[linkToBinderDeath]）是事件驱动的，不依赖此循环。
     * 此循环只负责检测“进程活着但服务卡死”的 zombie 状态。
     */
    fun start() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (true) {
                delay(ApexSuite.WATCHDOG_HEARTBEAT_INTERVAL_MS)
                val now = System.currentTimeMillis()
                val timeoutMs = ApexSuite.WATCHDOG_TIMEOUT_MS
                heartbeats.forEach { (apkId, record) ->
                    if (record.dead) return@forEach  // 已被 DeathRecipient 标记死亡，跳过
                    val elapsed = now - record.lastHeartbeatMs
                    if (elapsed > timeoutMs) {
                        ApexLog.w(ApexSuite.ApkId.MAIN, "[$TAG_SUB] apk unresponsive: $apkId (${elapsed}ms)")
                        _events.tryEmit(WatchdogEvent.ApkUnresponsive(apkId, elapsed, Trace.newId()))
                    }
                }
            }
        }
    }

    /**
     * 停止监控。
     */
    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * 当前所有监控状态快照（用于诊断 UI）。
     */
    fun snapshot(): List<ApkHealth> {
        val now = System.currentTimeMillis()
        return heartbeats.map { (apkId, record) ->
            val elapsed = now - record.lastHeartbeatMs
            ApkHealth(
                apkId = apkId,
                lastHeartbeatAgoMs = elapsed,
                healthy = !record.dead && elapsed < ApexSuite.WATCHDOG_TIMEOUT_MS
            )
        }
    }
}

data class ApkHealth(
    val apkId: String,
    val lastHeartbeatAgoMs: Long,
    val healthy: Boolean
)

sealed class WatchdogEvent(val traceId: String) {
    data class ApkRegistered(val apkId: String, val tid: String) : WatchdogEvent(tid)
    data class ApkDied(val apkId: String, val reason: String?, val tid: String) : WatchdogEvent(tid)
    data class ApkUnresponsive(val apkId: String, val elapsedMs: Long, val tid: String) : WatchdogEvent(tid)
    data class ApkRecovered(val apkId: String, val tid: String) : WatchdogEvent(tid)
}
