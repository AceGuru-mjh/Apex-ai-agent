package com.apex.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.apex.ui.features.chat.ChatScreen
import com.apex.ui.features.home.HomeScreen
import com.apex.ui.features.selfmodify.AuditLogScreen
import com.apex.ui.features.selfmodify.SelfModifyConfirmationDialog
import com.apex.ui.features.selfmodify.SelfModifyHostViewModel
import com.apex.ui.features.settings.SettingsScreen

@Composable
fun ApexNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    // Phase 5c: collect pending HIGH/CRITICAL self-modify confirmation requests
    // and show the risk dialog. The VM is scoped to this composable's scope.
    val hostViewModel: SelfModifyHostViewModel = hiltViewModel()
    val pendingRequest by hostViewModel.pendingRequest.collectAsStateWithLifecycle()

    SelfModifyConfirmationDialog(
        request = pendingRequest,
        onApprove = { id -> hostViewModel.approve(id) },
        onReject = { id -> hostViewModel.reject(id) }
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                ApexRoute.bottomNavItems.forEach { route ->
                    NavigationBarItem(
                        selected = currentDest?.hierarchy?.any { it.route == route.path } == true,
                        onClick = {
                            navController.navigate(route.path) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(route.icon, contentDescription = route.title) },
                        label = { Text(route.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ApexRoute.Home.path,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ApexRoute.Home.path) { HomeScreen(onNavigate = { route -> navController.navigate(route) }) }
            composable(ApexRoute.Chat.path) { ChatScreen() }
            composable(ApexRoute.Settings.path) {
                SettingsScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable(ApexRoute.AuditLog.path) {
                AuditLogScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
