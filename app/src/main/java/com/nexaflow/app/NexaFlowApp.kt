package com.nexaflow.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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

@Composable
fun NexaFlowApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") {
            DashboardScreen()
        }
        composable("automation_builder") {
            AutomationBuilderScreen()
        }
        composable("automation_details/{automationId}") {
            AutomationDetailsScreen()
        }
        composable("profiles") {
            ProfilesScreen()
        }
        composable("history") {
            HistoryScreen()
        }
        composable("capability_center") {
            CapabilityCenterScreen()
        }
        composable("icon_picker") {
            IconPickerScreen()
        }
        composable("themes") {
            ThemeScreen()
        }
        composable("widgets") {
            WidgetsScreen()
        }
        composable("settings") {
            SettingsScreen()
        }
        // TODO: Add other feature screens here
    }
}
