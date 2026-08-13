package com.nexaflow.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexaflow.core.ui.Dimens
import com.nexaflow.core.ui.NavigationDestination
import com.nexaflow.core.ui.NavigationStyle
import com.nexaflow.core.ui.NexaFlowNavigationBar
import com.nexaflow.core.ui.NexaFlowNavigationRail
import com.nexaflow.core.ui.isSystemReduceMotionEnabled
import com.nexaflow.core.ui.nexaFlowEffectsSpec
import com.nexaflow.core.ui.nexaFlowSpatialSpec
import com.nexaflow.core.ui.navigationStyleFor
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

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
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
            route = "settings",
            label = stringResource(com.nexaflow.feature.settings.R.string.settings_title),
            icon = Icons.Filled.Settings
        )
    )
    val isTopLevel = currentRoute in topLevelDestinations.map { it.route }

    // Adaptive navigation (M3 window size classes): expanded/extra-large
    // windows (tablets, foldables, desktop) get a navigation rail; compact
    // and medium keep the bottom bar. The NavHost graph is shared — only the
    // chrome changes — so back-stack state survives window resizes.
    val windowSizeClass = calculateWindowSizeClass(LocalActivity.current ?: return@NexaFlowApp)
    val navStyle = navigationStyleFor(windowSizeClass.widthSizeClass, isTopLevel)
    val useRail = navStyle == NavigationStyle.RAIL
    val showBottomBar = navStyle == NavigationStyle.BOTTOM_BAR
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
        Row(modifier = Modifier.fillMaxSize()) {
            // Expanded windows: navigation rail at the start, content beside
            // it. Row flips automatically with RTL (start = right in Arabic).
            if (useRail && isTopLevel) {
                NexaFlowNavigationRail(
                    currentRoute = currentRoute,
                    destinations = topLevelDestinations,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.width(Dimens.NavigationRailWidth)
                )
            }
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
}
