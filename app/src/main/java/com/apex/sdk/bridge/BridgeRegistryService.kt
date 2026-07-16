package com.apex.sdk.bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** 套件级 Bridge Registry Service — 其他 APK 通过 bindService 注册自己。占位实现。 */
class BridgeRegistryService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
}
