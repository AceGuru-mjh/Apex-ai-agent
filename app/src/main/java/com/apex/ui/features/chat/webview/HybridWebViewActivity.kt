package com.apex.ui.features.chat.webview

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

/** Hybrid WebView 容器 — 占位实现，支持加载 URL。 */
class HybridWebViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent?.data?.toString() ?: intent?.getStringExtra("url") ?: "about:blank"
        val webView = WebView(this)
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.loadUrl(url)
        setContentView(webView)
    }
}
