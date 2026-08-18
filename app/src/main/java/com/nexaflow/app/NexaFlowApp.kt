package com.nexaflow.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexaflow.core.ui.isSystemReduceMotionEnabled
import com.nexaflow.core.ui.nexaFlowEffectsSpec
import com.nexaflow.core.ui.nexaFlowSpatialSpec
import com.nexaflow.feature.automations.AutomationDetailsScreen
import com.nexaflow.feature.builder.AutomationBuilderScreen
import com.nexaflow.feature.builder.MapPickerScreen
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

/**
 * App navigation. The dashboard is the single top-level destination — the
 * settings page is reached from the gear icon beside the dashboard search bar,
 * so there is no bottom navigation bar / rail taking up screen space.
 */
@Composable
fun NexaFlowApp() {
    val navController = rememberNavController()
    // Google 2026: directional spring navigation. Reduce-motion is hoisted
    // here (it is @Composable, the NavHost transition lambdas are not) and
    // degrades to a plain crossfade when the user disables animations.
    val reduceMotion = isSystemReduceMotionEnabled()
    // Spring for the slide itself — IntOffset spring, not the Float one,
    // from the active M3 MotionScheme (captured here because the NavHost
    // enter/exit lambdas are not @Composable).
    val slideSpring = nexaFlowSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    // Fade spec from the same scheme.
    val fadeSpec = nexaFlowEffectsSpec<Float>()

    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                // Google 2026: directional spring slide-in (content arrives
                // from the right, matching Android's system navigation).
                if (reduceMotion) {
                    fadeIn()
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = slideSpring,
                        initialOffset = { it / 8 }
                    ) + fadeIn(animationSpec = fadeSpec)
                }
            },
            exitTransition = {
                if (reduceMotion) {
                    fadeOut()
                } else {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = slideSpring,
                        targetOffset = { it / 12 }
                    ) + fadeOut(animationSpec = fadeSpec)
                }
            },
            popEnterTransition = {
                if (reduceMotion) {
                    fadeIn()
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = slideSpring,
                        initialOffset = { it / 8 }
                    ) + fadeIn(animationSpec = fadeSpec)
                }
            },
            popExitTransition = {
                if (reduceMotion) {
                    fadeOut()
                } else {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = slideSpring,
                        targetOffset = { it / 12 }
                    ) + fadeOut(animationSpec = fadeSpec)
                }
            }
        ) {
            composable("dashboard") {
                DashboardScreen(navController = navController)
            }
            composable("automation_builder?automationId={automationId}&templateId={templateId}") { entry ->
                AutomationBuilderScreen(
                    navController = navController,
                    automationId = entry.arguments?.getString("automationId"),
                    templateId = entry.arguments?.getString("templateId"),
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
            composable("icon_picker") {
                IconPickerScreen(navController = navController)
            }
            composable("map_picker") {
                MapPickerScreen(navController = navController)
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
