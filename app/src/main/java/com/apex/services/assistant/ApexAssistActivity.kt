package com.apex.services.assistant

import android.app.Activity
import android.os.Bundle

/** 系统助手入口 Activity — 透明占位。 */
class ApexAssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
