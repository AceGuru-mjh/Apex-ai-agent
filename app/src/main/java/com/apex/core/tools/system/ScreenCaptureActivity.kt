package com.apex.core.tools.system

import android.app.Activity
import android.os.Bundle

/** 屏幕截图请求 Activity — 透明占位，用于触发 MediaProjection 权限。 */
class ScreenCaptureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 占位：实际实现需调用 MediaProjectionManager.createScreenCaptureIntent()
        finish()
    }
}
