package com.nexaflow.core.execution.events

import com.nexaflow.core.execution.compat.EventSource
import com.nexaflow.domain.events.EventFilter
import com.nexaflow.domain.events.EventPublishResult
import com.nexaflow.domain.events.EventSubscription
import com.nexaflow.domain.events.NexaFlowEvent
import com.nexaflow.domain.events.NexaFlowEventBus
import com.nexaflow.domain.events.NexaFlowEventType
import java.util.LinkedHashMap
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Event bus implementation for adapters over NexaFlow's existing monitors.
 * Each subscription owns one bounded FIFO channel, so events accepted by the
 * bus are delivered to that subscriber in publication order without creating a
 * second trigger engine or a permanent Android service.
 */
class InMemoryNexaFlowEventBus(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val deduplicationWindowMs: Long = DEFAULT_DEDUPLICATION_WINDOW_MS,
    private val subscriptionBufferSize: Int = DEFAULT_SUBSCRIPTION_BUFFER_SIZE,
    private val onSubscriberFailure: (subscriptionId: String, throwable: Throwable) -> Unit = { _, _ -> }
) : NexaFlowEventBus {

    private data class Subscriber(
        val id: String,
        val filter: EventFilter,
        val channel: Channel<NexaFlowEvent>,
        val job: Job
    )

    private val mutex = Mutex()
    private val subscribers = LinkedHashMap<String, Subscriber>()
    private val deduplicationLedger = LinkedHashMap<String, Long>()
    @Volatile
    private var closeRequested = false
    private var closed = false

    init {
        require(deduplicationWindowMs >= 0L) { "deduplicationWindowMs must be non-negative" }
        require(subscriptionBufferSize > 0) { "subscriptionBufferSize must be positive" }
    }

    override suspend fun subscribe(
        filter: EventFilter,
        onEvent: suspend (NexaFlowEvent) -> Unit
    ): EventSubscription {
        check(!closeRequested) { "Event bus is closed" }
        val id = UUID.randomUUID().toString()
        val channel = Channel<NexaFlowEvent>(subscriptionBufferSize)
        val job = scope.launch {
            for (event in channel) {
                runCatching { onEvent(event) }
                    .onFailure { throwable -> onSubscriberFailure(id, throwable) }
            }
        }
        val subscriber = Subscriber(id, filter, channel, job)
        val registered = mutex.withLock {
            if (closed || closeRequested) false else {
                subscribers[id] = subscriber
                true
            }
        }
        if (!registered) {
            channel.close()
            job.cancel()
            throw IllegalStateException("Event bus is closed")
        }
        return object : EventSubscription {
            override val id: String = id
            override fun close() {
                scope.launch { unsubscribe(id) }
            }
        }
    }

    override suspend fun publish(event: NexaFlowEvent): EventPublishResult {
        val matching = mutex.withLock {
            if (closed || closeRequested) {
                return EventPublishResult(
                    accepted = false,
                    deduplicated = false,
                    matchedSubscriptions = 0,
                    reason = "Event bus is closed"
                )
            }
            val now = nowMs()
            pruneDeduplicationLedger(now)
            val key = event.deduplicationKey
            if (key != null && key in deduplicationLedger) {
                return EventPublishResult(
                    accepted = false,
                    deduplicated = true,
                    matchedSubscriptions = 0,
                    reason = "Duplicate event within the deduplication window"
                )
            }
            if (key != null) deduplicationLedger[key] = now
            subscribers.values.filter { it.filter.matches(event) }
        }
        // Apply bounded backpressure after releasing the registry lock. An
        // unsubscribe/close racing this delivery closes the channel; that is an
        // expected lifecycle race, not a reason to fail an already accepted event.
        matching.forEach { subscriber ->
            try {
                subscriber.channel.send(event)
            } catch (_: ClosedSendChannelException) {
                // Subscription was removed after the atomic snapshot.
            }
        }
        return EventPublishResult(
            accepted = true,
            deduplicated = false,
            matchedSubscriptions = matching.size
        )
    }

    override fun close() {
        if (closeRequested) return
        closeRequested = true
        scope.launch {
            val toClose = mutex.withLock {
                if (closed) return@withLock emptyList()
                closed = true
                deduplicationLedger.clear()
                subscribers.values.toList().also { subscribers.clear() }
            }
            toClose.forEach {
                it.channel.close()
                it.job.cancel()
            }
        }
    }

    override suspend fun unsubscribe(subscriptionId: String): Boolean {
        val subscriber = mutex.withLock { subscribers.remove(subscriptionId) } ?: return false
        subscriber.channel.close()
        subscriber.job.cancel()
        return true
    }

    private fun pruneDeduplicationLedger(now: Long) {
        if (deduplicationWindowMs == 0L) {
            deduplicationLedger.clear()
            return
        }
        val iterator = deduplicationLedger.entries.iterator()
        while (iterator.hasNext()) {
            val (_, acceptedAt) = iterator.next()
            if (now - acceptedAt >= deduplicationWindowMs) iterator.remove()
        }
    }

    private companion object {
        const val DEFAULT_DEDUPLICATION_WINDOW_MS = 5_000L
        const val DEFAULT_SUBSCRIPTION_BUFFER_SIZE = 64
    }
}

/**
 * Adapter supplied to an existing Android monitor. It turns an observed signal
 * into a canonical [NexaFlowEvent] and publishes it, while leaving receiver
 * registration and monitor lifecycle inside the existing [EventSource].
 */
class MonitorEventAdapter(
    private val source: EventSource,
    private val eventBus: NexaFlowEventBus,
    private val eventId: () -> String = { UUID.randomUUID().toString() },
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun publish(
        type: NexaFlowEventType,
        payload: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()),
        correlationId: String? = null,
        deduplicationKey: String? = null
    ): EventPublishResult = eventBus.publish(
        NexaFlowEvent(
            eventId = eventId(),
            type = type,
            source = source.sourceId,
            occurredAt = nowMs(),
            payload = payload,
            correlationId = correlationId,
            deduplicationKey = deduplicationKey
        )
    )
}
