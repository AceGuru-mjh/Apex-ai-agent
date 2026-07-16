package com.apex.core.tools.system

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** 屏幕截图前台服务 — 占位实现。 */
class ScreenCaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
}
