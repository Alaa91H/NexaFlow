package com.nexaflow.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexaflow.core.capability.CapabilityCenterScreen
import com.nexaflow.feature.automations.AutomationDetailsScreen
import com.nexaflow.feature.builder.AutomationBuilderScreen
import com.nexaflow.feature.dashboard.DashboardScreen
import com.nexaflow.feature.history.HistoryScreen
import com.nexaflow.feature.icons.IconPickerScreen
import com.nexaflow.feature.profiles.ProfilesScreen
import com.nexaflow.feature.settings.SettingsScreen
import com.nexaflow.feature.themes.ThemeScreen
import com.nexaflow.feature.widgets.WidgetsScreen

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    BottomTab("dashboard", "Home", Icons.Filled.Dashboard),
    BottomTab("profiles", "Profiles", Icons.Filled.Group),
    BottomTab("capability_center", "Device", Icons.Filled.Security),
    BottomTab("history", "History", Icons.Filled.History),
    BottomTab("settings", "Settings", Icons.Filled.Settings)
)

@Composable
fun NexaFlowApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (bottomTabs.any { it.route == currentRoute }) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                            label = { Text(text = tab.label) }
                        )
                    }
                }
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
            composable("automation_builder") {
                AutomationBuilderScreen(navController = navController)
            }
            composable("automation_details/{automationId}") {
                AutomationDetailsScreen(navController = navController)
            }
            composable("profiles") {
                ProfilesScreen(navController = navController)
            }
            composable("history") {
                HistoryScreen(navController = navController)
            }
            composable("capability_center") {
                CapabilityCenterScreen()
            }
            composable("icon_picker") {
                IconPickerScreen(navController = navController)
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
