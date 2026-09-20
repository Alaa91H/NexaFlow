package com.nexaflow.wear.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.nexaflow.wear.data.WearAutomationDto

private const val ROUTE_LIST = "list"
private const val ROUTE_DETAIL = "detail/{automationId}"

/**
 * Root composable for the Wear OS companion app.
 *
 * Navigation:
 * - [ROUTE_LIST]: [AutomationListScreen] — the main entry point.
 * - [ROUTE_DETAIL]: [AutomationDetailScreen] — swipe-to-dismiss to go back.
 */
@Composable
fun WearApp(viewModel: WearViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = ROUTE_LIST,
    ) {
        composable(ROUTE_LIST) {
            AutomationListScreen(
                state = uiState,
                onRunNow = viewModel::runNow,
                onToggle = { automation, enabled -> viewModel.toggleEnabled(automation, enabled) },
                onOpenDetail = { automation ->
                    navController.navigate("detail/${automation.id}")
                },
            )
        }

        composable(ROUTE_DETAIL) { backStackEntry ->
            val automationId = backStackEntry.arguments?.getString("automationId")
            val automation = resolveAutomation(uiState, automationId)
            if (automation != null) {
                val isRunning = uiState is WearUiState.Running &&
                    (uiState as WearUiState.Running).runningId == automationId
                AutomationDetailScreen(
                    automation = automation,
                    isRunning = isRunning,
                    onRunNow = { viewModel.runNow(automation) },
                    onToggle = { enabled -> viewModel.toggleEnabled(automation, enabled) },
                )
            }
        }
    }
}

private fun resolveAutomation(state: WearUiState, id: String?): WearAutomationDto? {
    if (id == null) return null
    return when (state) {
        is WearUiState.Loaded -> state.automations.firstOrNull { it.id == id }
        is WearUiState.Running -> state.automations.firstOrNull { it.id == id }
        else -> null
    }
}
