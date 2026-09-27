package com.nexaflow.core.agentapi

import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Semaphore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bounded in-process event stream for agents.
 *
 * Events contain only redacted identifiers/diagnostic attributes. The ring is
 * intentionally non-durable; streamId changes after process restart so clients
 * can detect cursor reset and refresh authoritative state through normal APIs.
 */
class AgentEventHub(
    val streamId: String = UUID.randomUUID().toString(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val capacity: Int = DEFAULT_CAPACITY,
    maxWaiters: Int = DEFAULT_MAX_WAITERS
) {
    private val lock = Any()
    private val ring = ArrayDeque<AgentEventV1>()
    private val updates = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    private val waiterSlots = Semaphore(maxWaiters)
    private var sequence = 0L

    init {
        require(streamId.isNotBlank())
        require(capacity in 1..MAX_CAPACITY)
        require(maxWaiters in 1..MAX_WAITERS)
    }

    fun publish(
        type: AgentEventTypeV1,
        automationId: String? = null,
        agentId: String? = null,
        executionId: String? = null,
        attributes: Map<String, String> = emptyMap()
    ): AgentEventV1 {
        val event = synchronized(lock) {
            sequence += 1L
            val next = AgentEventV1(
                streamId = streamId,
                sequence = sequence,
                type = type,
                occurredAt = nowMillis(),
                automationId = boundedOptional(automationId, MAX_ID_LENGTH),
                agentId = boundedOptional(agentId, MAX_ID_LENGTH),
                executionId = boundedOptional(executionId, MAX_ID_LENGTH),
                attributes = sanitizeAttributes(attributes)
            )
            ring.addLast(next)
            while (ring.size > capacity) {
                ring.removeFirst()
            }
            next
        }
        updates.tryEmit(event.sequence)
        return event
    }

    fun snapshot(query: AgentEventQuery): AgentEventBatchV1 =
        synchronized(lock) {
            val reset = query.streamId != null && query.streamId != streamId
            val effectiveAfter = if (reset) 0L else query.afterSequence.coerceAtLeast(0L)
            val oldest = ring.firstOrNull()?.sequence ?: sequence
            val latest = ring.lastOrNull()?.sequence ?: sequence
            val available = ring.asSequence()
                .filter { it.sequence > effectiveAfter }
                .toList()
            val limit = query.limit.coerceIn(1, MAX_BATCH_SIZE)
            AgentEventBatchV1(
                streamId = streamId,
                events = available.take(limit),
                oldestSequence = oldest,
                latestSequence = latest,
                reset = reset,
                truncated = available.size > limit ||
                    // The caller asked from the beginning (or a reset forced
                    // the cursor there) but the ring already evicted older
                    // events: the snapshot is a truncated view of history.
                    (!reset && effectiveAfter < oldest - 1L)
            )
        }

    suspend fun await(query: AgentEventQuery): AgentEventBatchV1 {
        val initial = snapshot(query)
        val wait = query.waitMs.coerceIn(0L, MAX_WAIT_MS)
        if (initial.events.isNotEmpty() || initial.reset || wait == 0L) {
            return initial
        }
        if (!waiterSlots.tryAcquire()) {
            return initial
        }
        try {
            withTimeoutOrNull(wait) {
                updates.first { it > query.afterSequence }
            }
            return snapshot(query)
        } finally {
            waiterSlots.release()
        }
    }

    private fun sanitizeAttributes(
        source: Map<String, String>
    ): Map<String, String> = source.entries
        .asSequence()
        .filter { (key, _) -> key.isNotBlank() }
        .take(MAX_ATTRIBUTES)
        .associate { (key, value) ->
            key.take(MAX_ATTRIBUTE_KEY_LENGTH) to value.take(MAX_ATTRIBUTE_VALUE_LENGTH)
        }

    private fun boundedOptional(value: String?, limit: Int): String? =
        value?.takeIf(String::isNotBlank)?.take(limit)

    companion object {
        const val DEFAULT_CAPACITY = 512
        const val DEFAULT_MAX_WAITERS = 4
        const val MAX_WAIT_MS = 10_000L
        const val MAX_BATCH_SIZE = 100
        const val MAX_CAPACITY = 4_096
        const val MAX_WAITERS = 8
        const val MAX_ATTRIBUTES = 16
        const val MAX_ATTRIBUTE_KEY_LENGTH = 64
        const val MAX_ATTRIBUTE_VALUE_LENGTH = 512
        const val MAX_ID_LENGTH = 256
    }
}
