package com.nexaflow.wear.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.wear.data.WearAutomationDto
import com.nexaflow.wear.data.WearDataLayerClient
import com.nexaflow.wear.data.WearSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * ViewModel for the watch companion UI.
 *
 * The watch first restores the latest durable DataItem snapshot (when one is
 * already cached locally), then asks the phone for a fresh snapshot. Fresh-sync
 * requests use bounded exponential backoff and are considered successful only
 * after a new DataItem revision arrives.
 */
@HiltViewModel
class WearViewModel @Inject constructor(
    private val syncRepository: WearSyncRepository,
    private val dataLayerClient: WearDataLayerClient,
) : ViewModel() {

    private val _runningId = MutableStateFlow<String?>(null)
    private var refreshJob: Job? = null

    init {
        refreshFromPhone()
    }

    val uiState: StateFlow<WearUiState> =
        combine(syncRepository.automations, _runningId) { automations, runningId ->
            when {
                // Null = no cached/live snapshot has been loaded yet.
                automations == null -> WearUiState.Connecting
                automations.isEmpty() -> WearUiState.Empty
                runningId != null -> WearUiState.Running(automations, runningId)
                else -> WearUiState.Loaded(automations)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            WearUiState.Connecting,
        )

    /**
     * Sends a force-run command for [automation].
     *
     * Run commands are intentionally not auto-retried: repeating a delivered
     * run command could execute non-idempotent automation actions twice.
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
     * Restores cached state (only when this process has none yet) and requests
     * a fresh snapshot from the phone. Repeated lifecycle resumes do not spawn
     * overlapping retry loops.
     */
    fun refreshFromPhone() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            bootstrapCachedStateIfNeeded()
            requestFreshStateWithRetry()
        }
    }

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

    private suspend fun bootstrapCachedStateIfNeeded() {
        if (syncRepository.automations.value != null) return
        dataLayerClient.readCachedAutomationPayload()
            ?.let(syncRepository::handleIncomingPayload)
    }

    /**
     * Sync requests are idempotent, so bounded retry is safe. A successful
     * MessageClient send is not enough; the loop stops only when the repository
     * observes a newer DataItem revision from the phone.
     */
    private suspend fun requestFreshStateWithRetry() {
        var retryDelayMs = 0L

        repeat(MAX_SYNC_ATTEMPTS) {
            if (retryDelayMs > 0L) delay(retryDelayMs)

            val baselineRevision = syncRepository.updateSequence.value
            val requestDelivered = dataLayerClient.requestSync()

            if (requestDelivered) {
                val refreshed = withTimeoutOrNull(SYNC_RESPONSE_TIMEOUT_MS) {
                    syncRepository.updateSequence.first { revision ->
                        revision > baselineRevision
                    }
                } != null

                if (refreshed) return
            }

            retryDelayMs = when (retryDelayMs) {
                0L -> INITIAL_RETRY_DELAY_MS
                else -> (retryDelayMs * 2L).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }

        Log.d(TAG, "Fresh Wear sync not confirmed after bounded retries; cached state remains usable")
    }

    private companion object {
        const val TAG = "WearViewModel"
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        const val MAX_SYNC_ATTEMPTS = 4
        const val SYNC_RESPONSE_TIMEOUT_MS = 1_500L
        const val INITIAL_RETRY_DELAY_MS = 500L
        const val MAX_RETRY_DELAY_MS = 2_000L
    }
}
