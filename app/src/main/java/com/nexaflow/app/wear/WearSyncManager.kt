package com.nexaflow.app.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.NodeClient
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.coroutineScope
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
        // Advertise the phone companion capability: the watch uses it to tell
        // a reachable companion apart from a paired-but-dead phone.
        syncScope.launch {
            runCatching {
                Wearable.getCapabilityClient(context).addLocalCapability(
                    com.nexaflow.core.execution.WEAR_CAPABILITY_PHONE_APP
                ).await()
            }.onFailure { Log.w(TAG, "Capability advertisement failed", it) }
        }
        // Re-push whenever a watch (re)connects: the historical design pushed
        // only on data changes, so a phone process started while the watch was
        // disconnected left it with no data — the reported "sync never starts"
        // bug. DataItems survive in the Data Layer cache, but a fresh urgent
        // push closes every race (GMS reconnect, stale cache, watch reboot).
        syncScope.launch {
            nodeConnectedEvents()
                .collect {
                    runCatching { pushNow() }
                        .onFailure { Log.w(TAG, "Connectivity re-push failed", it) }
                }
        }
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

    /**
     * Emits once per (re)connection of a watch node. The first emission is
     * the initial connectivity state: a watch already present at app start
     * also gets its fresh push without waiting for a data change.
     *
     * Uses the CapabilityClient "companion available" signal — the documented
     * reachability source (play-services-wearable 19 has no NodeClient
     * connect listener). When the watch becomes reachable the capability info
     * carries at least one node, which is exactly the re-push trigger.
     */
    private fun nodeConnectedEvents(): kotlinx.coroutines.flow.Flow<Unit> =
        kotlinx.coroutines.flow.callbackFlow {
            val capabilityClient = Wearable.getCapabilityClient(context)
            val listener =
                com.google.android.gms.wearable.CapabilityClient.OnCapabilityChangedListener { info ->
                    if (info.nodes.isNotEmpty()) this@callbackFlow.trySend(Unit)
                }
            // Snapshot the initial state: a watch already connected at start
            // must trigger the first push without waiting for a transition.
            coroutineScope {
                launch {
                    val reachable = runCatching {
                        capabilityClient.getCapability(
                            com.nexaflow.core.execution.WEAR_CAPABILITY_PHONE_APP,
                            CapabilityClient.FILTER_REACHABLE
                        ).await()
                    }.getOrNull()?.nodes?.isNotEmpty() == true
                    if (reachable) send(Unit)
                }
            }
            runCatching {
                capabilityClient.addListener(
                    listener,
                    com.nexaflow.core.execution.WEAR_CAPABILITY_PHONE_APP
                )
            }.onFailure { Log.w(TAG, "Capability listener registration failed", it) }
            awaitClose {
                runCatching { capabilityClient.removeListener(listener) }
            }
        }

    /**
     * Builds and pushes the current automation list right now. Used by the
     * watch-initiated sync request ([com.nexaflow.core.execution.WEAR_PATH_SYNC_REQUEST])
     * and by the connectivity re-push; coalesced through the same builder as
     * the flow-driven push so both paths serialize an identical payload.
     */
    suspend fun pushNow() {
        val automations = automationRepository.getAutomations().first()
        val latestRuns = historyRepository.getLatestExecutions().first()
        pushToWatch(buildDtos(automations, latestRuns))
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
