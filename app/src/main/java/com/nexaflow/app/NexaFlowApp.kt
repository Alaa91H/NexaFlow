package com.nexaflow.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexaflow.feature.dashboard.DashboardScreen

@Composable
fun NexaFlowApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "dashboard") {
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
            CapabilityCenterScreen(navController = navController)
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
        // TODO: Add other feature screens here
    }
}
