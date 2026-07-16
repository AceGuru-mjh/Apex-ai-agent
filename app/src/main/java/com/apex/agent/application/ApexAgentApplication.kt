package com.apex.agent.application

import android.app.Application
import android.util.Log
import com.apex.core.application.ApexApplication

/**
 * Apex 套件主 APK 的 Application 入口。
 *
 * 负责初始化全局上下文、全局异常处理器等基础组件。
 * 所有模块通过 [ApexApplication.context] 获取全局 Context。
 */
class ApexAgentApplication : Application() {

    companion object {
        private const val TAG = "ApexAgentApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ApexAgentApplication.onCreate() — initializing")
        // 初始化全局 Context（供各模块使用）
        ApexApplication.init(this)
        // 安装全局未捕获异常处理器，避免闪退无提示
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
        Log.i(TAG, "ApexAgentApplication initialized successfully")
    }
}

/** Hilt 入口点占位 — 当前未启用 Hilt，保留以兼容原有引用。 */
interface BurstKernelInitializerEntryPoint
