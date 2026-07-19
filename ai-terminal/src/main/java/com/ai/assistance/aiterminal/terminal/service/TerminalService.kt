package com.ai.assistance.aiterminal.terminal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import com.ai.assistance.aiterminal.terminal.ITerminalCallback
import com.ai.assistance.aiterminal.terminal.ITerminalService
import com.ai.assistance.aiterminal.terminal.TerminalManager
import com.ai.assistance.aiterminal.terminal.model.TerminalEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 终端Service（原ApexTerminalCore的TerminalService）
 * 独立进程，AIDL IPC 通信，前台服务
 */
class TerminalService : Service() {
    companion object {
        private const val TAG = "TerminalService"
        private const val CHANNEL_ID = "terminal_service_channel"
        private const val NOTIFICATION_ID = 1001
    }

    // AIDL回调列表（支持多客户端）
    private val callbacks = RemoteCallbackList<ITerminalCallback>()

    // 终端管理器实例
    private val terminalManager = TerminalManager.instance

    // D-5: per-caller (UID) rate limiting for executeCommand. A malicious
    // binder client can call executeCommand in a tight loop and spawn
    // thousands of shell processes (fork-bomb / DoS). Sliding 1-second
    // window, max 10 commands per UID per window. ConcurrentHashMap +
    // per-list synchronized block for thread safety (binder calls arrive
    // on pool threads).
    private val commandTimes = ConcurrentHashMap<Int, MutableList<Long>>()

    private fun checkRateLimit(uid: Int): Boolean {
        val now = System.currentTimeMillis()
        val times = commandTimes.computeIfAbsent(uid) { mutableListOf() }
        synchronized(times) {
            times.removeAll { now - it > 1000 }  // drop entries older than 1s
            if (times.size >= 10) return false   // 10 cmds/sec per caller
            times.add(now)
            return true
        }
    }

    // AIDL Binder
    private val binder = object : ITerminalService.Stub() {
        // 创建会话
        override fun createSession(sessionId: String): Boolean {
            Log.d(TAG, "createSession: $sessionId")
            return terminalManager.createSession(sessionId)
        }

        // 启动会话
        override fun startSession(sessionId: String, shellType: String): Boolean {
            Log.d(TAG, "startSession: $sessionId, shellType: $shellType")
            return terminalManager.startSession(sessionId, shellType)
        }

        // 执行命令
        override fun executeCommand(sessionId: String, command: String): Boolean {
            Log.d(TAG, "executeCommand: $sessionId, command: $command")
            // D-5: per-caller rate limit. Without this a binder client can
            // hammer executeCommand in a tight loop and fork-bomb the device.
            // 10 cmds/sec/UID sliding window — see checkRateLimit().
            //
            // Per-session concurrent-command limit: NOT enforced here.
            // Session.state (CREATED/RUNNING/SUSPENDED/CLOSED) tracks
            // session lifecycle, not per-command execution, so there is no
            // reliable 'command in flight' flag to check without a deeper
            // change to the JNI layer. The rate limit above is the primary
            // guard against concurrent-spam DoS.
            val uid = Binder.getCallingUid()
            if (!checkRateLimit(uid)) {
                Log.w(TAG, "Rate limit exceeded for uid=$uid, rejecting command on session $sessionId")
                return false
            }
            return terminalManager.executeCommand(sessionId, command)
        }

        // 切换会话
        override fun switchSession(sessionId: String): Boolean {
            Log.d(TAG, "switchSession: $sessionId")
            return terminalManager.switchSession(sessionId)
        }

        // 切换目录
        override fun changeDirectory(sessionId: String, path: String): Boolean {
            Log.d(TAG, "changeDirectory: $sessionId, path: $path")
            return terminalManager.changeDirectory(sessionId, path)
        }

        // 获取当前目录
        override fun getCurrentDirectory(sessionId: String): String {
            Log.d(TAG, "getCurrentDirectory: $sessionId")
            return terminalManager.getCurrentDirectory(sessionId) ?: ""
        }

        // 挂起会话
        override fun suspendSession(sessionId: String) {
            Log.d(TAG, "suspendSession: $sessionId")
            terminalManager.suspendSession(sessionId)
        }

        // 恢复会话
        override fun resumeSession(sessionId: String) {
            Log.d(TAG, "resumeSession: $sessionId")
            terminalManager.resumeSession(sessionId)
        }

        // 关闭会话
        override fun closeSession(sessionId: String): Boolean {
            Log.d(TAG, "closeSession: $sessionId")
            return terminalManager.closeSession(sessionId)
        }

        // 关闭所有会话
        override fun closeAllSessions() {
            Log.d(TAG, "closeAllSessions")
            terminalManager.closeAllSessions()
        }

        // 注册回调
        override fun registerCallback(callback: ITerminalCallback?) {
            Log.d(TAG, "registerCallback")
            callback?.let { callbacks.register(it) }
        }

        // 注销回调
        override fun unregisterCallback(callback: ITerminalCallback?) {
            Log.d(TAG, "unregisterCallback")
            callback?.let { callbacks.unregister(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // 创建通知通道并启动前台服务
        // 创建通知通道并启动前台服务
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 监听终端事件，转发到AIDL回调
        CoroutineScope(Dispatchers.Main).launch {
            terminalManager.eventFlow.collect { event ->
                dispatchEventToCallbacks(event)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Terminal Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Terminal core service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Terminal Core")
                .setContentText("Terminal service is running")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("Terminal Core")
                .setContentText("Terminal service is running")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed, cleaning up resources")
        // 清理资源
        callbacks.kill()
        terminalManager.closeAllSessions()
        terminalManager.cleanup()
    }

    /**
     * 分发事件到所有AIDL回调客户端
     */
    private fun dispatchEventToCallbacks(event: TerminalEvent) {
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            val callback = callbacks.getBroadcastItem(i)
            try {
                when (event) {
                    is TerminalEvent.CommandOutput -> {
                        callback.onCommandOutput(event.sessionId, event.output)
                    }
                    is TerminalEvent.DirectoryChanged -> {
                        callback.onDirectoryChanged(event.sessionId, event.newDir)
                    }
                    is TerminalEvent.SessionStateChanged -> {
                        callback.onSessionStateChanged(event.sessionId, event.state.name)
                    }
                    is TerminalEvent.CommandFinished -> {
                        callback.onCommandFinished(event.sessionId, event.command, event.exitCode)
                    }
                    is TerminalEvent.ErrorOccurred -> {
                        callback.onErrorOccurred(event.sessionId, event.message, event.code)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error dispatching event to callback", e)
            }
        }
        callbacks.finishBroadcast()
    }
}
