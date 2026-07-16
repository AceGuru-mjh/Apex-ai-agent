package com.apex.agent.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** 后台常驻服务 — 占位实现。 */
class PermanentBackgroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
}
