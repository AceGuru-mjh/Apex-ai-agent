package com.apex.api.chat

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** AI 前台服务 — 保持网络连接稳定。占位实现。 */
class AIForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
}
