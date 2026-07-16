package com.apex.integrations.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机启动广播接收器 — 用于重新调度 Workflow。占位实现。 */
class WorkflowBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 占位
    }
}
