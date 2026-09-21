package com.nexaflow.wear.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watch-side repository that holds the latest automation list received from the
 * phone via the Wearable Data Layer.
 *
 * [WearDataListenerService] calls [handleIncomingPayload] whenever a new
 * DataItem arrives; [WearDataLayerClient] can also feed the last cached
 * DataItem during cold start so the UI does not depend on the phone being
 * reachable at that exact moment.
 */
@Singleton
class WearSyncRepository @Inject constructor() {

    private val json: Json = Json { ignoreUnknownKeys = true }
    private val sequence = AtomicLong(0L)

    // null = no payload has ever been observed on this process ("Connecting");
    // an empty list = the phone explicitly pushed an empty automation set.
    private val _automations = MutableStateFlow<List<WearAutomationDto>?>(null)
    private val _updateSequence = MutableStateFlow(0L)

    /** Latest automation list, or null until a cached/live DataItem is loaded. */
    val automations: StateFlow<List<WearAutomationDto>?> = _automations.asStateFlow()

    /**
     * Process-local monotonic revision. Every accepted cached or live DataItem
     * increments this value. The ViewModel snapshots it before a sync request
     * and waits for it to advance, so MessageClient delivery is not mistaken
     * for successful state synchronization.
     */
    val updateSequence: StateFlow<Long> = _updateSequence.asStateFlow()

    /**
     * Parses [payload] (a JSON array of [WearAutomationDto]) and publishes it.
     * Malformed payloads preserve historical behavior by becoming an explicit
     * empty list rather than leaving the UI indefinitely in Connecting.
     */
    fun handleIncomingPayload(payload: String) {
        val decoded = runCatching {
            json.decodeFromString<List<WearAutomationDto>>(payload)
        }
        _automations.value = decoded.getOrElse { emptyList() }

        // A malformed frame is visible as an empty state for backward
        // compatibility, but it must not acknowledge a fresh-sync request.
        // The ViewModel will continue its bounded retries until a valid
        // automation snapshot arrives.
        if (decoded.isSuccess) {
            _updateSequence.value = sequence.incrementAndGet()
        }
    }
}
