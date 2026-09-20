package com.nexaflow.wear.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.wear.data.WearDataLayerClient
import com.nexaflow.wear.data.WearAutomationDto
import com.nexaflow.wear.data.WearSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the watch companion UI.
 *
 * Observes the automation list from [WearSyncRepository] (populated by
 * [WearDataListenerService]) and exposes [uiState] for the Compose screens.
 * Commands (run now, toggle) are sent to the phone via [WearDataLayerClient].
 */
@HiltViewModel
class WearViewModel @Inject constructor(
    private val syncRepository: WearSyncRepository,
    private val dataLayerClient: WearDataLayerClient,
) : ViewModel() {

    private val _runningId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WearUiState> =
        combine(syncRepository.automations, _runningId) { automations, runningId ->
            when {
                automations.isEmpty() && runningId == null -> WearUiState.Connecting
                automations.isEmpty() -> WearUiState.Empty
                runningId != null -> WearUiState.Running(automations, runningId)
                else -> WearUiState.Loaded(automations)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), WearUiState.Connecting)

    /**
     * Sends a force-run command for [automation] to the phone. Updates
     * [uiState] to [WearUiState.Running] while the command is in-flight so
     * the UI shows a loading indicator on the relevant card.
     */
    fun runNow(automation: WearAutomationDto) {
        if (_runningId.value != null) return
        viewModelScope.launch {
            _runningId.value = automation.id
            try {
                dataLayerClient.sendRunCommand(automation.id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Log.w(TAG, "Run command failed for ${automation.id}", e)
            } finally {
                _runningId.value = null
            }
        }
    }

    /**
     * Sends a toggle command for [automation] to the phone.
     * The phone's [WearCommandListenerService] applies the change and
     * [WearSyncManager] pushes a fresh automation list back to the watch.
     */
    fun toggleEnabled(automation: WearAutomationDto, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                dataLayerClient.sendToggleCommand(automation.id, enabled)
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w(TAG, "Toggle command failed for ${automation.id}", it)
            }
        }
    }

    private companion object {
        const val TAG = "WearViewModel"
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
