package com.nexaflow.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexaflow.core.capability.CapabilityCenterScreen
import com.nexaflow.core.ui.NavigationDestination
import com.nexaflow.core.ui.NexaFlowNavigationBar
import com.nexaflow.feature.automations.AutomationDetailsScreen
import com.nexaflow.feature.builder.AutomationBuilderScreen
import com.nexaflow.feature.builder.MapPickerScreen
import com.nexaflow.feature.builder.VariablesScreen
import com.nexaflow.feature.dashboard.DashboardScreen
import com.nexaflow.feature.history.ExecutionDetailsScreen
import com.nexaflow.feature.history.HistoryScreen
import com.nexaflow.feature.icons.IconPickerScreen
import com.nexaflow.feature.settings.NotificationManagerScreen
import com.nexaflow.feature.settings.PermissionManagerScreen
import com.nexaflow.feature.settings.PluginManagerScreen
import com.nexaflow.feature.settings.SettingsScreen
import com.nexaflow.feature.themes.ThemeScreen
import com.nexaflow.feature.widgets.WidgetsScreen

@Composable
fun NexaFlowApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val topLevelDestinations = listOf(
        NavigationDestination(
            route = "dashboard",
            label = stringResource(com.nexaflow.feature.dashboard.R.string.dashboard_title),
            icon = Icons.Filled.Checklist
        ),
        NavigationDestination(
            route = "history",
            label = stringResource(com.nexaflow.feature.history.R.string.history_title),
            icon = Icons.Filled.History
        ),
        NavigationDestination(
            route = "settings",
            label = stringResource(com.nexaflow.feature.settings.R.string.settings_title),
            icon = Icons.Filled.Settings
        )
    )
    val showBottomBar = currentRoute in topLevelDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NexaFlowNavigationBar(
                    currentRoute = currentRoute,
                    destinations = topLevelDestinations,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            // Tab semantics: keep each top-level tab's own state
                            // and only pop back to the start destination.
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                DashboardScreen(navController = navController)
            }
            composable("automation_builder?automationId={automationId}") { entry ->
                AutomationBuilderScreen(
                    navController = navController,
                    automationId = entry.arguments?.getString("automationId"),
                    // The entry's handle is stable for this destination; the
                    // icon picker writes its result here. Reading it from the
                    // navController instead would re-point at the top entry
                    // while the picker is open and drop the result.
                    savedStateHandle = entry.savedStateHandle
                )
            }
            composable("automation_details/{automationId}") {
                AutomationDetailsScreen(navController = navController)
            }
            composable("permission_manager") {
                PermissionManagerScreen(navController = navController)
            }
            composable("notification_manager") {
                NotificationManagerScreen(navController = navController)
            }
            composable("plugins") {
                PluginManagerScreen(navController = navController)
            }
            composable("history") {
                HistoryScreen(navController = navController)
            }
            composable("execution_details/{recordId}") {
                ExecutionDetailsScreen(navController = navController)
            }
            composable("capability_center") {
                CapabilityCenterScreen()
            }
            composable("icon_picker") {
                IconPickerScreen(navController = navController)
            }
            composable("map_picker") {
                MapPickerScreen(navController = navController)
            }
            composable("variables") {
                VariablesScreen(navController = navController)
            }
            composable("themes") {
                ThemeScreen(navController = navController)
            }
            composable("widgets") {
                WidgetsScreen(navController = navController)
            }
            composable("settings") {
                SettingsScreen(navController = navController)
            }
        }
    }
}
