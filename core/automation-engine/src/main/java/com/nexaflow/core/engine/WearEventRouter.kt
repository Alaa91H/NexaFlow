package com.nexaflow.core.engine

import android.content.Context
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.TriggerMatchPolicy
import com.nexaflow.core.execution.TriggerStateEvaluator
import com.nexaflow.core.execution.compat.TriggerSource
import com.nexaflow.core.wearprotocol.WearRuntimeState
import com.nexaflow.domain.events.EventFilter
import com.nexaflow.domain.events.EventSubscription
import com.nexaflow.domain.events.NexaFlowEvent
import com.nexaflow.domain.events.NexaFlowEventBus
import com.nexaflow.domain.events.NexaFlowEventType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.models.isOneShotEvent
import com.nexaflow.domain.repositories.AutomationRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val exitCoordinator: ExitCoordinator,
    private val runtimeStore: AutomationRuntimeStore,
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
                    false -> if (shouldExitAfterWearFalse(automation)) {
                        exitIfNeeded(automation)
                    }
                    null -> Unit
                }
            }
        }
    }

    private suspend fun restoreAndPruneDurableState(all: List<Automation>) {
        val source = TriggerSource.WEAR.sourceId
        val byId = all.associateBy { it.id }
        val runtimeStates = runtimeStore.activeStates().filter { it.source == source }
        val runtimeIds = runtimeStates.mapTo(linkedSetOf()) { it.automationId }

        runtimeStates.forEach { state ->
            val automation = byId[state.automationId]
            when {
                automation == null -> {
                    runtimeStore.clear(state.automationId, state.occurrenceId)
                    activeAutomations -= state.automationId
                    activeStore.clearAutomation(source, state.automationId)
                }
                automation.enabled &&
                    automation.triggers.any { it.type == TriggerType.WEAR_EVENT } -> {
                    activeAutomations += state.automationId
                    activeStore.markActive(source, state.automationId)
                }
                else -> {
                    when (
                        exitCoordinator.requestExit(
                            automation = automation,
                            reason = ExitReason.AUTOMATION_DISABLED,
                            occurrenceId = state.occurrenceId,
                        )
                    ) {
                        is ExitCoordinatorResult.Executed,
                        ExitCoordinatorResult.NotActive,
                        ExitCoordinatorResult.StaleOccurrence -> {
                            activeAutomations -= state.automationId
                            activeStore.clearAutomation(source, state.automationId)
                        }
                        ExitCoordinatorResult.AlreadyInProgress,
                        is ExitCoordinatorResult.RecoveryRequired -> {
                            activeAutomations += state.automationId
                        }
                    }
                }
            }
        }

        // WEAR_EVENT did not exist before this lifecycle implementation, so a
        // compatibility marker without a runtime occurrence cannot represent
        // proven execution ownership. Drop it rather than fabricate an active run.
        activeStore.activeKeys(source)
            .filterNot { it.substringBefore('|') in runtimeIds }
            .forEach { staleKey ->
                val automationId = staleKey.substringBefore('|')
                activeAutomations -= automationId
                activeStore.clearAutomation(source, automationId)
            }
    }

    private fun wearConditionFor(automation: Automation): Boolean? {
        val results = automation.triggers
            .filter { it.type == TriggerType.WEAR_EVENT }
            .map { trigger ->
                when (
                    WearRuntimeState.conditionSatisfied(
                        watchInstallId = trigger.config["watchInstallId"],
                        wantConnected = (trigger.config["state"] ?: "CONNECTED") == "CONNECTED",
                    )
                ) {
                    true -> ConditionResult.Satisfied
                    false -> ConditionResult.Unsatisfied
                    null -> ConditionResult.Unknown
                }
            }

        if (results.isEmpty()) return null
        return when (TriggerMatchPolicy.aggregate(automation.triggerMatch, results)) {
            ConditionResult.Satisfied -> true
            ConditionResult.Unsatisfied -> false
            else -> null
        }
    }

    /**
     * ALL becomes false as soon as one Wear condition is false. For ANY, a
     * different trigger may still keep the automation active; confirm the
     * remaining current-state triggers before closing the lifecycle. Unknown
     * is deliberately fail-closed: an unreadable state is never proof that the
     * whole ANY expression ended.
     */
    private suspend fun shouldExitAfterWearFalse(automation: Automation): Boolean {
        if (automation.triggerMatch == TriggerMatchMode.ALL) return true
        val remaining = automation.triggers.filterNot { trigger ->
            trigger.type == TriggerType.WEAR_EVENT || trigger.isOneShotEvent()
        }
        if (remaining.isEmpty()) return true

        val states = remaining.map { trigger ->
            runCatching {
                TriggerStateEvaluator.evaluateTriggerState(context, trigger)
            }.getOrElse { ConditionResult.Unknown }
        }
        return states.all { it == ConditionResult.Unsatisfied }
    }

    private suspend fun enterIfNeeded(automation: Automation, now: Long) {
        if (automation.id in activeAutomations) return
        val last = lastRunAt[automation.id] ?: 0L
        if (now - last < automation.cooldownMillis) return

        lastRunAt[automation.id] = now
        val source = TriggerSource.WEAR.sourceId
        val occurrenceId = "wear:${automation.id}:${UUID.randomUUID()}"
        executionEngine.runAutomation(
            automation = automation,
            lifecycleContext = AutomationLifecycleContext(
                occurrenceId = occurrenceId,
                source = source,
                sourceKey = automation.id,
            ),
        )
        val accepted = runtimeStore.current(automation.id)?.let { state ->
            state.occurrenceId == occurrenceId && state.source == source
        } == true
        if (accepted) {
            activeAutomations += automation.id
            activeStore.markActive(source, automation.id)
        } else {
            // Admission/constraint failure never becomes lifecycle ownership.
            lastRunAt.remove(automation.id)
        }
    }

    private suspend fun exitIfNeeded(automation: Automation) {
        if (automation.id !in activeAutomations) return
        val source = TriggerSource.WEAR.sourceId
        val occurrenceId = runtimeStore.current(automation.id)
            ?.takeIf { it.source == source }
            ?.occurrenceId

        when (
            exitCoordinator.requestExit(
                automation = automation,
                reason = ExitReason.TRIGGER_FALSE,
                occurrenceId = occurrenceId,
            )
        ) {
            is ExitCoordinatorResult.Executed,
            ExitCoordinatorResult.NotActive,
            ExitCoordinatorResult.StaleOccurrence -> {
                activeAutomations -= automation.id
                activeStore.clearAutomation(source, automation.id)
            }
            ExitCoordinatorResult.AlreadyInProgress,
            is ExitCoordinatorResult.RecoveryRequired -> {
                // Keep ownership until a successful/reconciled exit proves end.
                activeAutomations += automation.id
            }
        }
    }
}
