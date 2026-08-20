package com.nexaflow.core.engine

import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.compat.TriggerSource
import com.nexaflow.domain.events.EventFilter
import com.nexaflow.domain.events.EventSubscription
import com.nexaflow.domain.events.NexaFlowEvent
import com.nexaflow.domain.events.NexaFlowEventBus
import com.nexaflow.domain.events.NexaFlowEventType
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Canonical post-bus route for plugin events. The route begins *after*
 * [PluginEventReceiver] has authenticated and published a [NexaFlowEvent], then
 * uses [TriggerIndex] to choose configured workflows. It is intentionally the
 * only component in this feature allowed to call the existing engine.
 */
class PluginEventRouter(
    private val scope: CoroutineScope,
    private val eventBus: NexaFlowEventBus,
    private val triggerIndex: TriggerIndex,
    private val executionEngine: ExecutionEngine,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val lock = Any()
    private val lastRunAt = HashMap<String, Long>()
    private var subscription: EventSubscription? = null
    private var started = false

    suspend fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        val created = eventBus.subscribe(
            filter = EventFilter(
                types = setOf(NexaFlowEventType.CUSTOM),
                sources = setOf(TriggerSource.PLUGIN.sourceId)
            ),
            onEvent = ::route
        )
        val discard = synchronized(lock) {
            if (started) {
                subscription = created
                false
            } else {
                true
            }
        }
        if (discard) eventBus.unsubscribe(created.id)
    }

    fun stop() {
        val active = synchronized(lock) {
            started = false
            subscription.also { subscription = null }
        }
        if (active != null) scope.launch { eventBus.unsubscribe(active.id) }
    }

    private suspend fun route(event: NexaFlowEvent) {
        val pluginPackage = (event.payload["pluginPackage"] as? JsonPrimitive)?.contentOrNull ?: return
        val component = (event.payload["eventComponent"] as? JsonPrimitive)?.contentOrNull ?: return
        val eventId = (event.payload["pluginEventId"] as? JsonPrimitive)?.contentOrNull ?: return
        val instances = (event.payload["pluginInstances"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: return
        val targets = triggerIndex.bySource(TriggerSource.PLUGIN.sourceId)
            .filter { automation -> automation.triggers.any { trigger ->
                trigger.type == TriggerType.PLUGIN_EVENT &&
                    trigger.config[PluginEventIngress.KEY_APPROVAL] == PluginEventIngress.APPROVAL_VALUE &&
                    trigger.config[PluginEventIngress.KEY_PACKAGE] == pluginPackage &&
                    trigger.config[PluginEventIngress.KEY_COMPONENT] == component &&
                    trigger.config[PluginEventIngress.KEY_INSTANCE] in instances &&
                    (trigger.config[PluginEventIngress.KEY_EVENT_ID].isNullOrBlank() ||
                        trigger.config[PluginEventIngress.KEY_EVENT_ID] == eventId)
            } }
        targets.forEach { automation ->
            if (!admitCooldown(automation.id, automation.cooldownMillis, event.occurredAt)) return@forEach
            // The event has already crossed the authenticated receiver → bus →
            // index boundary. This reuses the singleton execution engine rather
            // than constructing an interpreter, manager, or recovery path.
            runCatching { executionEngine.runAutomation(automation) }
        }
    }

    private fun admitCooldown(automationId: String, cooldownMs: Long, occurredAt: Long): Boolean = synchronized(lock) {
        val now = maxOf(nowMs(), occurredAt)
        val last = lastRunAt[automationId]
        if (last != null && now - last < cooldownMs) return false
        lastRunAt[automationId] = now
        while (lastRunAt.size > MAX_COOLDOWN_ENTRIES) lastRunAt.entries.iterator().run {
            next()
            remove()
        }
        true
    }

    private companion object {
        const val MAX_COOLDOWN_ENTRIES = 2_048
    }
}
