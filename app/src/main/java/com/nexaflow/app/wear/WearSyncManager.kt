package com.nexaflow.app.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.nexaflow.core.execution.WEAR_KEY_PAYLOAD
import com.nexaflow.core.execution.WEAR_KEY_UPDATED_AT
import com.nexaflow.core.execution.WEAR_PATH_AUTOMATIONS
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes the current automation list (with last-run status) to any connected
 * Wear OS watch via the Wearable Data Layer.
 *
 * Observes [AutomationRepository] and [HistoryRepository] on the application
 * [CoroutineScope]; on each emission it serializes the combined view to JSON
 * and writes it as a DataItem at [WEAR_PATH_AUTOMATIONS]. A debounce of
 * [DEBOUNCE_MS] coalesces rapid consecutive saves (e.g. bulk enable-all) into
 * one push.
 *
 * Call [start] once from [com.nexaflow.app.NexaFlowApplication.onCreate].
 * Call [stop] if you need to tear down the manager (test or shutdown).
 */
@Singleton
class WearSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val automationRepository: AutomationRepository,
    private val historyRepository: HistoryRepository,
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Start observing repositories and pushing updates to the watch. */
    // debounce() is FlowPreview: intentional and stable-enough for this
    // throttling use case; opted in locally rather than project-wide.
    @OptIn(FlowPreview::class)
    fun start() {
        syncScope.launch {
            combine(
                automationRepository.getAutomations(),
                historyRepository.getLatestExecutions(),
            ) { automations, latestRuns ->
                buildDtos(automations, latestRuns)
            }
                .debounce(DEBOUNCE_MS)
                .collect { dtos ->
                    pushToWatch(dtos)
                }
        }
    }

    /** Cancel the background sync scope. */
    fun stop() {
        syncScope.cancel()
    }

    private fun buildDtos(
        automations: List<Automation>,
        latestRuns: List<ExecutionRecord>,
    ): List<WearAutomationDto> {
        val runsByAutomation = latestRuns.associateBy { it.automationId }
        return automations.map { automation ->
            val lastRun = runsByAutomation[automation.id]
            WearAutomationDto(
                id = automation.id,
                name = automation.name,
                icon = automation.icon,
                iconColor = automation.iconColor,
                enabled = automation.enabled,
                lastRunAt = lastRun?.executedAt,
                lastRunSuccess = lastRun?.success,
                lastRunMessage = lastRun?.message?.take(MAX_MESSAGE_LENGTH),
            )
        }
    }

    private suspend fun pushToWatch(dtos: List<WearAutomationDto>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = json.encodeToString(dtos)
                val request = PutDataMapRequest.create(WEAR_PATH_AUTOMATIONS).apply {
                    dataMap.putString(WEAR_KEY_PAYLOAD, payload)
                    dataMap.putLong(WEAR_KEY_UPDATED_AT, System.currentTimeMillis())
                }
                Wearable.getDataClient(context)
                    .putDataItem(request.asPutDataRequest().setUrgent())
                    .await()
            }.onFailure {
                Log.w(TAG, "Failed to push automation list to watch", it)
            }
        }
    }

    private companion object {
        const val TAG = "WearSyncManager"
        const val DEBOUNCE_MS = 500L
        const val MAX_MESSAGE_LENGTH = 100
    }
}
