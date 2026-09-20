package com.nexaflow.wear.presentation

import com.nexaflow.wear.data.WearAutomationDto

/** Sealed UI state for the watch automation list screen. */
sealed interface WearUiState {
    /** Initial state before the first automation list is received from the phone. */
    data object Connecting : WearUiState

    /** The phone pushed an empty automation list. */
    data object Empty : WearUiState

    /** At least one automation is available to display. */
    data class Loaded(val automations: List<WearAutomationDto>) : WearUiState

    /** A run command is in-flight for the given automation id. */
    data class Running(
        val automations: List<WearAutomationDto>,
        val runningId: String,
    ) : WearUiState
}
