package com.nexaflow.wear.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watch-side repository that holds the latest automation list received from the
 * phone via the Wearable Data Layer.
 *
 * [WearDataListenerService] calls [handleIncomingPayload] whenever a new
 * DataItem arrives; the [WearViewModel] observes [automations].
 */
@Singleton
class WearSyncRepository @Inject constructor() {

    private val json: Json = Json { ignoreUnknownKeys = true }

    // null = no payload has ever been received from the phone ("Connecting");
    // an empty list = the phone explicitly pushed an empty automation set.
    // The ViewModel relies on this distinction: without it, a user with zero
    // automations would see the Connecting spinner forever.
    private val _automations = MutableStateFlow<List<WearAutomationDto>?>(null)

    /**
     * Observable stream of the latest automation list from the phone.
     * Null until the first payload arrives; never null afterwards (a
     * malformed payload decodes to an empty list).
     */
    val automations: StateFlow<List<WearAutomationDto>?> = _automations.asStateFlow()

    /**
     * Parses [payload] (a JSON array of [WearAutomationDto]) and updates the
     * [automations] state. Called from [WearDataListenerService] on a background
     * thread; safe to call from any dispatcher.
     */
    fun handleIncomingPayload(payload: String) {
        val dtos = runCatching {
            json.decodeFromString<List<WearAutomationDto>>(payload)
        }.getOrElse { emptyList() }
        _automations.value = dtos
    }
}
