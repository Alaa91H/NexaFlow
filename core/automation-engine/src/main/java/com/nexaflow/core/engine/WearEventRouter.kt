package com.nexaflow.core.engine

import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.compat.TriggerSource
import com.nexaflow.core.wearprotocol.WearRuntimeState
import com.nexaflow.domain.events.EventFilter
import com.nexaflow.domain.events.EventSubscription
import com.nexaflow.domain.events.NexaFlowEvent
import com.nexaflow.domain.events.NexaFlowEventBus
import com.nexaflow.domain.events.NexaFlowEventType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stateful post-bus router for Wear OS connection triggers.
 *
 * The phone-side Data Layer bridge owns reachability observation and publishes
 * canonical [NexaFlowEventType.WEAR_CONNECTION_CHANGED] events. This router
 * subscribes after that boundary, resolves only workflows indexed under the
 * Wear source, and owns the stateful enter/exit lifecycle.
 */
@Singleton
class WearEventRouter @Inject constructor(
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    private val triggerIndex: TriggerIndex,
    private val eventBus: NexaFlowEventBus,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val lifecycleMutex = Mutex()
    private val activeAutomations = ConcurrentHashMap.newKeySet<String>()
    private val lastRunAt = ConcurrentHashMap<String, Long>()

    @Volatile
    private var subscription: EventSubscription? = null

    @Volatile
    private var started = false

    suspend fun start() {
        if (started) return
        started = true
        val created = eventBus.subscribe(
            filter = EventFilter(
                types = setOf(NexaFlowEventType.WEAR_CONNECTION_CHANGED),
                sources = setOf(TriggerSource.WEAR.sourceId),
            ),
            onEvent = ::route,
        )
        if (!started) {
            eventBus.unsubscribe(created.id)
            return
        }
        subscription = created
        reconcileNow()
    }

    fun stop() {
        started = false
        subscription?.let { current ->
            scope.launch { eventBus.unsubscribe(current.id) }
        }
        subscription = null
    }

    fun reconcileAutomations() {
        if (!started) return
        scope.launch { reconcileNow() }
    }

    private suspend fun route(@Suppress("UNUSED_PARAMETER") event: NexaFlowEvent) {
        reconcileNow()
    }

    private suspend fun reconcileNow() {
        lifecycleMutex.withLock {
            val all = repository.getAutomations().first()
            restoreAndPruneDurableState(all)

            val candidates = if (triggerIndex.version > 0L) {
                triggerIndex.bySource(TriggerSource.WEAR.sourceId)
            } else {
                all.filter { automation ->
                    automation.enabled && automation.triggers.any { it.type == TriggerType.WEAR_EVENT }
                }
            }

            val now = System.currentTimeMillis()
            candidates.forEach { automation ->
                when (wearConditionFor(automation)) {
                    true -> enterIfNeeded(automation, now)
                    false -> exitIfNeeded(automation)
                    null -> Unit
                }
            }
        }
    }

    private suspend fun restoreAndPruneDurableState(all: List<Automation>) {
        val byId = all.associateBy { it.id }
        activeStore.activeKeys(TriggerSource.WEAR.sourceId).forEach { automationId ->
            val automation = byId[automationId]
            if (
                automation?.enabled == true &&
                automation.triggers.any { it.type == TriggerType.WEAR_EVENT }
            ) {
                activeAutomations += automationId
            } else {
                activeAutomations -= automationId
                activeStore.clearAutomation(TriggerSource.WEAR.sourceId, automationId)
            }
        }
    }

    private fun wearConditionFor(automation: Automation): Boolean? {
        val states = automation.triggers
            .filter { it.type == TriggerType.WEAR_EVENT }
            .map { trigger ->
                WearRuntimeState.conditionSatisfied(
                    watchInstallId = trigger.config["watchInstallId"],
                    wantConnected = (trigger.config["state"] ?: "CONNECTED") == "CONNECTED",
                )
            }

        if (states.isEmpty()) return null
        return when (automation.triggerMatch) {
            TriggerMatchMode.ANY -> when {
                states.any { it == true } -> true
                states.all { it == false } -> false
                else -> null
            }
            TriggerMatchMode.ALL -> when {
                states.any { it == false } -> false
                states.all { it == true } -> true
                else -> null
            }
        }
    }

    private suspend fun enterIfNeeded(automation: Automation, now: Long) {
        if (automation.id in activeAutomations) return
        val last = lastRunAt[automation.id] ?: 0L
        if (now - last < automation.cooldownMillis) return

        lastRunAt[automation.id] = now
        activeAutomations += automation.id
        activeStore.markActive(TriggerSource.WEAR.sourceId, automation.id)
        executionEngine.runAutomation(automation)
    }

    private suspend fun exitIfNeeded(automation: Automation) {
        if (!activeAutomations.remove(automation.id)) return
        activeStore.clearAutomation(TriggerSource.WEAR.sourceId, automation.id)
        executionEngine.runExit(automation)
    }
}
