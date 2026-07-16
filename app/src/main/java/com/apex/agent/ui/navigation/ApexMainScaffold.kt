package com.apex.agent.ui.navigation

import androidx.compose.runtime.Composable
import com.apex.ui.main.material.ApexHomeScreen

/**
 * 主界面 Scaffold — 委托给 [ApexHomeScreen]。
 * MainActivity 通过此 Composable 展示主界面。
 */
@Composable
fun ApexMainScaffold() {
    ApexHomeScreen()
}
