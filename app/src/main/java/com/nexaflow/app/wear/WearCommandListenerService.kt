package com.nexaflow.app.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.nexaflow.core.execution.WEAR_PATH_CAPABILITIES_V1
import com.nexaflow.core.execution.WEAR_PATH_RUN_COMMAND
import com.nexaflow.core.execution.WEAR_PATH_SYNC_REQUEST
import com.nexaflow.core.execution.WEAR_PATH_TOGGLE_COMMAND
import com.nexaflow.core.execution.WEAR_TOGGLE_SEPARATOR
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Phone-side Wearable listener that receives run and toggle commands from the
 * watch via the Wearable MessageClient and routes them to the appropriate
 * domain objects.
 *
 * Declared in the phone's [AndroidManifest.xml] with a MESSAGE_RECEIVED
 * filter scoped to `/nexaflow/` paths. Hilt entry-point injection is used
 * instead of [@AndroidEntryPoint] because [WearableListenerService] is started
 * by the platform — not by Hilt — and the standard Hilt injection lifecycle
 * requires explicit opt-in through [EntryPointAccessors].
 */
class WearCommandListenerService : WearableListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WearBridgeEntryPoint {
        fun executionEngine(): ExecutionEngine
        fun automationRepository(): AutomationRepository
        fun wearSyncManager(): WearSyncManager
        fun wearDeviceRegistry(): WearDeviceRegistry
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val entryPoint: WearBridgeEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            WearBridgeEntryPoint::class.java,
        )
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.use { buffer ->
            buffer.forEach { event ->
                if (
                    event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == WEAR_PATH_CAPABILITIES_V1
                ) {
                    entryPoint.wearDeviceRegistry().acceptDataItem(event.dataItem)
                }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WEAR_PATH_RUN_COMMAND -> handleRunCommand(event.data)
            WEAR_PATH_TOGGLE_COMMAND -> handleToggleCommand(event.data)
            WEAR_PATH_SYNC_REQUEST -> handleSyncRequest()
            else -> Log.d(TAG, "Ignoring unknown Wear message path: ${event.path}")
        }
    }

    /**
     * The watch asked for the current automation list (its UI just started or
     * connectivity returned). Re-push the DataItem immediately: the historical
     * push-on-change-only design left the watch on its "Connecting" spinner
     * forever whenever the phone process started while the watch was away.
     */
    private fun handleSyncRequest() {
        serviceScope.launch {
            runCatching {
                entryPoint.wearSyncManager().pushNow()
            }.onFailure {
                Log.w(TAG, "Wear sync-request push failed", it)
            }
        }
    }

    private fun handleRunCommand(data: ByteArray) {
        val automationId = data.toString(Charsets.UTF_8).trim()
        if (automationId.isBlank()) return
        serviceScope.launch {
            val automation = entryPoint.automationRepository().getAutomationById(automationId)
                ?: return@launch
            runCatching {
                entryPoint.executionEngine().forceRun(automation)
            }.onFailure {
                Log.w(TAG, "Wear force-run failed for automation $automationId", it)
            }
        }
    }

    private fun handleToggleCommand(data: ByteArray) {
        val payload = data.toString(Charsets.UTF_8).trim()
        val separatorIndex = payload.indexOf(WEAR_TOGGLE_SEPARATOR)
        if (separatorIndex < 0) return
        val automationId = payload.substring(0, separatorIndex)
        val enabledStr = payload.substring(separatorIndex + 1)
        val enabled = enabledStr.toBooleanStrictOrNull() ?: return
        if (automationId.isBlank()) return
        serviceScope.launch {
            runCatching {
                entryPoint.automationRepository()
                    .updateAutomationStatus(automationId, enabled)
                entryPoint.executionEngine().notifyAutomationsChanged()
            }.onFailure {
                Log.w(TAG, "Wear toggle failed for automation $automationId", it)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "WearCommandListener"
    }
}
