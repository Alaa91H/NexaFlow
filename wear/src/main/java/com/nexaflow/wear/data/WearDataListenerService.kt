package com.nexaflow.wear.data

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Watch-side Wearable listener that receives automation list DataItems pushed
 * by the phone and forwards them to [WearSyncRepository] for the UI to observe.
 *
 * Declared in the manifest with a DATA_CHANGED filter on /nexaflow/ paths.
 */
@AndroidEntryPoint
class WearDataListenerService : WearableListenerService() {

    @Inject
    lateinit var wearSyncRepository: WearSyncRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.use { buffer ->
            buffer.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == WearProtocol.PATH_AUTOMATIONS
                ) {
                    handleAutomationUpdate(event.dataItem)
                }
            }
        }
    }

    private fun handleAutomationUpdate(dataItem: com.google.android.gms.wearable.DataItem) {
        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
        val payload = dataMap.getString(WearProtocol.KEY_PAYLOAD) ?: return
        serviceScope.launch {
            wearSyncRepository.handleIncomingPayload(payload)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
