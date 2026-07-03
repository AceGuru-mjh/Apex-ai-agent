package com.apex.agent.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 主界面 — 左侧抽屉导航（汉堡菜单）。
 *
 * - 左上角三条横杠 → 点击从左侧滑出 NavigationDrawer
 * - 右上角：上下文百分比
 * - 无底部导航栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApexMainScaffold() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(ApexTab.CHAT) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                // 抽屉头部
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            "Apex AI Agent",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "v1.0.0 · 套件 6/9 已安装",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()

                // 导航项
                ApexTab.values().forEach { tab ->
                    NavigationDrawerItem(
                        label = { Text(tab.label, style = MaterialTheme.typography.bodyLarge) },
                        selected = currentTab == tab,
                        icon = { Icon(tab.icon, contentDescription = tab.description) },
                        onClick = {
                            currentTab = tab
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                Spacer(Modifier.weight(1f))
                HorizontalDivider()
                // 底部信息
                Text(
                    "Apex Suite · Material You 3",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    ) {
        when (currentTab) {
            ApexTab.CHAT -> com.apex.agent.ui.screens.chat.ChatScreen(
                onMenuClick = { scope.launch { drawerState.open() } }
            )
            ApexTab.SUITE -> com.apex.agent.ui.screens.suite.SuiteScreen(
                onMenuClick = { scope.launch { drawerState.open() } }
            )
            ApexTab.DIAGNOSTICS -> com.apex.agent.ui.screens.diagnostics.DiagnosticsScreen(
                onMenuClick = { scope.launch { drawerState.open() } }
            )
            ApexTab.SETTINGS -> com.apex.agent.ui.screens.settings.SettingsScreen(
                onMenuClick = { scope.launch { drawerState.open() } }
            )
        }
    }
}
