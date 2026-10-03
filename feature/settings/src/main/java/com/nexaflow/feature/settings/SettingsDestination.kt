package com.nexaflow.feature.settings

/** Shared route contract for destinations exposed directly by Settings. */
object SettingsDestination {
    const val EXECUTION_HISTORY_ROUTE = "history"
    const val AI_AGENTS_ROUTE = "ai_agents"
    const val MANAGED_AI_AGENTS_ROUTE = ManagedAgentDestination.ROUTE
    const val ACTIVITY_HISTORY_ROUTE = "activity_history"
}
