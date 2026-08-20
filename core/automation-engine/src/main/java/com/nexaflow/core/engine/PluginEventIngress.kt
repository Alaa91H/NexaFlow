package com.nexaflow.core.engine

import com.nexaflow.core.execution.compat.TriggerSource
import com.nexaflow.domain.events.EventPublishResult
import com.nexaflow.domain.events.NexaFlowEvent
import com.nexaflow.domain.events.NexaFlowEventBus
import com.nexaflow.domain.events.NexaFlowEventType
import com.nexaflow.domain.models.TriggerType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Validates external plugin event identity and bounds before publishing a
 * canonical event. This class deliberately has no ExecutionEngine dependency.
 */
class PluginEventIngress(
    private val triggerIndex: TriggerIndex,
    private val eventBus: NexaFlowEventBus,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val maxEventsPerWindow: Int = 30,
    private val rateLimitWindowMs: Long = 60_000L,
    private val deduplicationWindowMs: Long = 30_000L
) {
    private val rateLedger = LinkedHashMap<String, ArrayDeque<Long>>()
    private val dedupLedger = LinkedHashMap<String, Long>()
    private val lock = Any()

    init {
        require(maxEventsPerWindow in 1..1_000) { "maxEventsPerWindow must be in 1..1000" }
        require(rateLimitWindowMs in 1_000L..300_000L) { "rateLimitWindowMs must be in 1,000..300,000ms" }
        require(deduplicationWindowMs in 1_000L..300_000L) { "deduplicationWindowMs must be in 1,000..300,000ms" }
    }

    suspend fun publish(
        senderPackage: String,
        eventComponent: String,
        eventId: String,
        correlationId: String?,
        payload: JsonObject
    ): EventPublishResult {
        val normalizedCorrelation = correlationId?.takeIf { it.isNotBlank() } ?: eventId
        if (!isValidToken(senderPackage, 255) || !isValidToken(eventComponent, 255) ||
            !isValidToken(eventId, 128) || !isValidToken(normalizedCorrelation, 128)
        ) {
            return rejected("Plugin event identity is invalid")
        }
        val matchingInstances = triggerIndex.bySource(TriggerSource.PLUGIN.sourceId)
            .flatMap { automation -> automation.triggers.map { automation to it } }
            .filter { (_, trigger) ->
                trigger.type == TriggerType.PLUGIN_EVENT &&
                    trigger.config[KEY_APPROVAL] == APPROVAL_VALUE &&
                    trigger.config[KEY_PACKAGE] == senderPackage &&
                    trigger.config[KEY_COMPONENT] == eventComponent &&
                    trigger.config[KEY_INSTANCE]?.isNotBlank() == true &&
                    (trigger.config[KEY_EVENT_ID].isNullOrBlank() || trigger.config[KEY_EVENT_ID] == eventId)
            }
        if (matchingInstances.isEmpty()) {
            return rejected("No approved workflow trigger matches this plugin event")
        }
        val now = nowMs()
        val dedupKey = "$senderPackage|$eventComponent|$eventId|$normalizedCorrelation"
        val rateKey = "$senderPackage|$eventComponent"
        synchronized(lock) {
            prune(now)
            if (dedupLedger.containsKey(dedupKey)) return EventPublishResult(
                accepted = false,
                deduplicated = true,
                matchedSubscriptions = 0,
                reason = "Duplicate plugin event"
            )
            val samples = rateLedger.getOrPut(rateKey) { ArrayDeque() }
            if (samples.size >= maxEventsPerWindow) return rejected("Plugin event rate limit exceeded")
            samples.addLast(now)
            dedupLedger[dedupKey] = now
            while (dedupLedger.size > MAX_DEDUP_ENTRIES) dedupLedger.entries.iterator().run {
                next()
                remove()
            }
        }
        val instanceIds = matchingInstances.map { (_, trigger) -> checkNotNull(trigger.config[KEY_INSTANCE]) }.distinct()
        val canonicalPayload = JsonObject(
            mapOf(
                "pluginPackage" to JsonPrimitive(senderPackage),
                "eventComponent" to JsonPrimitive(eventComponent),
                "pluginEventId" to JsonPrimitive(eventId),
                "pluginInstances" to kotlinx.serialization.json.JsonArray(instanceIds.map(::JsonPrimitive)),
                "data" to payload
            )
        )
        return eventBus.publish(
            NexaFlowEvent(
                eventId = "plugin:$eventId:$normalizedCorrelation",
                type = NexaFlowEventType.CUSTOM,
                source = TriggerSource.PLUGIN.sourceId,
                occurredAt = now,
                payload = canonicalPayload,
                correlationId = normalizedCorrelation,
                deduplicationKey = dedupKey
            )
        )
    }

    private fun prune(now: Long) {
        rateLedger.entries.iterator().apply {
            while (hasNext()) {
                val (_, samples) = next()
                while (samples.firstOrNull()?.let { now - it >= rateLimitWindowMs } == true) samples.removeFirst()
                if (samples.isEmpty()) remove()
            }
        }
        dedupLedger.entries.iterator().apply {
            while (hasNext()) {
                val (_, acceptedAt) = next()
                if (now - acceptedAt >= deduplicationWindowMs) remove()
            }
        }
    }

    private fun isValidToken(value: String, maxLength: Int): Boolean =
        value.length in 1..maxLength && value.all { it.isLetterOrDigit() || it in ".:_-$" }

    private fun rejected(reason: String): EventPublishResult = EventPublishResult(
        accepted = false,
        deduplicated = false,
        matchedSubscriptions = 0,
        reason = reason
    )

    companion object {
        const val KEY_INSTANCE = "pluginInstance"
        const val KEY_APPROVAL = "pluginApproval"
        const val KEY_PACKAGE = "package"
        const val KEY_COMPONENT = "eventComponent"
        const val KEY_EVENT_ID = "pluginEventId"
        const val APPROVAL_VALUE = "approved"
        private const val MAX_DEDUP_ENTRIES = 1_024
    }
}
