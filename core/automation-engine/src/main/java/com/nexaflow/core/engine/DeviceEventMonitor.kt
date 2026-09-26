package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.TriggerOccurrence
import com.nexaflow.core.execution.compat.EventSource
import com.nexaflow.core.execution.events.MonitorEventAdapter
import com.nexaflow.domain.events.EventFilter
import com.nexaflow.domain.events.EventSubscription
import com.nexaflow.domain.events.NexaFlowEventBus
import com.nexaflow.domain.events.NexaFlowEventType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Stateful screen/power/headset event monitor.
 *
 * AutomationRuntimeStore is the source of truth for ownership across process
 * death. ActiveTriggerStore remains only as a compatibility mirror for older
 * installs that armed a state before the runtime ledger existed.
 */
@Singleton
class DeviceEventMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    private val runtimeStore: AutomationRuntimeStore,
    private val exitCoordinator: ExitCoordinator,
    private val triggerIndex: TriggerIndex,
    private val eventBus: NexaFlowEventBus,
    @ApplicationScope private val scope: CoroutineScope
) : EventSource {

    override val sourceId: String = SOURCE
    override val description: String = "Screen, power and wired-headset events"

    private val eventAdapter by lazy { MonitorEventAdapter(this, eventBus) }
    private var eventSubscription: EventSubscription? = null

    @Volatile
    private var registered = false

    private val lastRunAt = ConcurrentHashMap<String, Long>()
    private val evaluationMutex = Mutex()

    /** The event that ends the active phase of each device event. */
    private val oppositeEvent = mapOf(
        "SCREEN_ON" to "SCREEN_OFF",
        "SCREEN_OFF" to "SCREEN_ON",
        "POWER_CONNECTED" to "POWER_DISCONNECTED",
        "POWER_DISCONNECTED" to "POWER_CONNECTED",
        "HEADSET_CONNECTED" to "HEADSET_DISCONNECTED",
        "HEADSET_DISCONNECTED" to "HEADSET_CONNECTED"
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val event = when (intent.action) {
                Intent.ACTION_SCREEN_ON -> "SCREEN_ON"
                Intent.ACTION_SCREEN_OFF -> "SCREEN_OFF"
                Intent.ACTION_POWER_CONNECTED -> "POWER_CONNECTED"
                Intent.ACTION_POWER_DISCONNECTED -> "POWER_DISCONNECTED"
                Intent.ACTION_HEADSET_PLUG -> {
                    when (intent.getIntExtra("state", -1)) {
                        1 -> "HEADSET_CONNECTED"
                        0 -> "HEADSET_DISCONNECTED"
                        else -> return
                    }
                }
                else -> return
            }
            publishEvent(event)
        }
    }

    override fun start() = initialize()

    fun initialize() {
        if (registered) return
        registered = true
        val receiverFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        scope.launch {
            eventSubscription = eventBus.subscribe(
                filter = EventFilter(
                    types = setOf(NexaFlowEventType.SYSTEM_EVENT),
                    sources = setOf(sourceId)
                )
            ) { canonicalEvent ->
                val event = (canonicalEvent.payload["event"] as? JsonPrimitive)?.contentOrNull
                    ?: return@subscribe
                handleCanonicalEvent(event)
            }
            if (!registered) {
                eventSubscription?.let { eventBus.unsubscribe(it.id) }
                eventSubscription = null
                return@launch
            }

            val receiverRegistered = runCatching {
                context.registerReceiver(receiver, receiverFilter)
                true
            }.getOrDefault(false)
            if (!receiverRegistered) {
                registered = false
                eventSubscription?.let { eventBus.unsubscribe(it.id) }
                eventSubscription = null
                return@launch
            }

            reconcileCurrentState()
        }
    }

    fun reconcileAutomations() {
        scope.launch { reconcileCurrentState() }
    }

    override fun stop() {
        if (!registered) return
        registered = false
        eventSubscription?.let { subscription ->
            scope.launch { eventBus.unsubscribe(subscription.id) }
        }
        eventSubscription = null
        runCatching { context.unregisterReceiver(receiver) }
    }

    private fun publishEvent(event: String) {
        scope.launch {
            eventAdapter.publish(
                type = NexaFlowEventType.SYSTEM_EVENT,
                payload = JsonObject(mapOf("event" to JsonPrimitive(event)))
            )
        }
    }

    private suspend fun reconcileCurrentState() {
        reconcileSnapshot(readCurrentSnapshot())
    }

    /**
     * Deterministic restart/edit boundary. A null value is UNKNOWN and must not
     * fabricate a trigger end.
     */
    internal suspend fun reconcileSnapshot(snapshot: Map<String, Boolean?>) =
        evaluationMutex.withLock {
            val automations = repository.getAutomations().first()
            val byId = automations.associateBy { it.id }
            rearmFromLedger(byId)

            runtimeStore.activeStates()
                .filter { it.source == SOURCE }
                .forEach { state ->
                    val automation = byId[state.automationId]
                    when {
                        automation == null -> clearLegacyState(state.automationId)
                        !automation.enabled || monitoredTrigger(automation) == null -> {
                            requestExit(
                                automation = automation,
                                reason = ExitReason.AUTOMATION_DISABLED,
                                occurrenceId = state.occurrenceId
                            )
                        }
                        else -> markLegacyActive(state)
                    }
                }

            automations
                .filter { it.enabled && monitoredTrigger(it) != null }
                .forEach { automation ->
                    val trigger = monitoredTrigger(automation) ?: return@forEach
                    val event = configuredEvent(trigger)
                    val holds = snapshot[event]
                    val state = runtimeStore.current(automation.id)

                    when (holds) {
                        true -> when {
                            state?.source == SOURCE -> markLegacyActive(state)
                            state != null -> clearLegacyState(automation.id)
                            else -> activate(automation, event)
                        }

                        false -> {
                            if (state?.source == SOURCE) {
                                requestExit(
                                    automation = automation,
                                    reason = ExitReason.TRIGGER_FALSE,
                                    occurrenceId = state.occurrenceId
                                )
                            } else if (state == null) {
                                clearLegacyState(automation.id)
                            }
                        }

                        null -> {
                            if (state?.source == SOURCE) markLegacyActive(state)
                            else if (state == null) clearLegacyState(automation.id)
                        }
                    }
                }
        }

    /**
     * One canonical edge. Exact trigger edges activate, exact opposite edges
     * exit. Other events are irrelevant and never imply a condition state.
     */
    internal suspend fun handleCanonicalEvent(event: String) =
        evaluationMutex.withLock {
            val automations = if (triggerIndex.version > 0L) {
                triggerIndex.bySource(sourceId)
            } else {
                repository.getAutomations().first()
            }
            val byId = repository.getAutomations().first().associateBy { it.id }
            rearmFromLedger(byId)

            automations
                .filter { it.enabled && monitoredTrigger(it) != null }
                .forEach { automation ->
                    val trigger = monitoredTrigger(automation) ?: return@forEach
                    val configured = configuredEvent(trigger)
                    val state = runtimeStore.current(automation.id)

                    when {
                        event == configured -> when {
                            state?.source == SOURCE -> markLegacyActive(state)
                            state != null -> clearLegacyState(automation.id)
                            else -> activate(automation, event)
                        }

                        oppositeEvent[configured] == event && state?.source == SOURCE -> {
                            requestExit(
                                automation = automation,
                                reason = ExitReason.TRIGGER_FALSE,
                                occurrenceId = state.occurrenceId
                            )
                        }
                    }
                }
        }

    private suspend fun rearmFromLedger(automations: Map<String, Automation>) {
        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = automations[state.automationId]
                if (automation == null) {
                    // Definition gone: preserve durable evidence and drop only
                    // the compatibility mirror.
                    clearLegacyState(state.automationId)
                } else {
                    markLegacyActive(state)
                }
            }

        activeStore.activeKeys(SOURCE).forEach { key ->
            val automationId = key.substringBefore('|')
            val automation = automations[automationId]
            if (automation == null) {
                clearLegacyState(automationId)
                return@forEach
            }

            if (runtimeStore.current(automationId) == null) {
                val event = key.substringAfter('|', missingDelimiterValue = "")
                    .takeIf { it in oppositeEvent.keys }
                    ?: monitoredTrigger(automation)?.let(::configuredEvent)
                    ?: return@forEach
                runtimeStore.activateStrict(
                    AutomationRuntimeState(
                        automationId = automationId,
                        occurrenceId = "legacy:$SOURCE:$automationId:${UUID.randomUUID()}",
                        source = SOURCE,
                        sourceKey = "$automationId|$event",
                        lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                        activatedAt = System.currentTimeMillis()
                    )
                )
            }

            val state = runtimeStore.current(automationId)
            if (state?.source == SOURCE) {
                markLegacyActive(state)
                if (!automation.enabled || monitoredTrigger(automation) == null) {
                    requestExit(
                        automation = automation,
                        reason = ExitReason.AUTOMATION_DISABLED,
                        occurrenceId = state.occurrenceId
                    )
                }
            } else {
                // A stale device-event key must never authorize an exit for a
                // lifecycle owned by a different stateful source.
                clearLegacyState(automationId)
            }
        }
    }

    private suspend fun activate(automation: Automation, event: String) {
        val now = System.currentTimeMillis()
        val last = lastRunAt[automation.id] ?: 0L
        if (now - last <= automation.cooldownMillis) return
        lastRunAt[automation.id] = now

        val occurrenceId = "device:${automation.id}:${UUID.randomUUID()}"
        val sourceKey = "${automation.id}|$event"
        val triggerIndices = automation.triggers.mapIndexedNotNull { index, trigger ->
            index.takeIf {
                (trigger.type == TriggerType.DEVICE || trigger.type == TriggerType.HEADPHONE) &&
                    configuredEvent(trigger) == event
            }
        }.toSet()

        executionEngine.runAutomation(
            automation = automation,
            lifecycleContext = AutomationLifecycleContext(
                occurrenceId = occurrenceId,
                source = SOURCE,
                sourceKey = sourceKey
            ),
            triggerOccurrence = TriggerOccurrence(
                matchedTriggerIndices = triggerIndices,
                occurredAtEpochMs = now,
                sourceId = SOURCE,
                eventId = "device:$event"
            )
        )

        runtimeStore.current(automation.id)
            ?.takeIf { it.source == SOURCE && it.occurrenceId == occurrenceId }
            ?.let { markLegacyActive(it) }
            ?: clearLegacyState(automation.id)
    }

    private suspend fun requestExit(
        automation: Automation,
        reason: ExitReason,
        occurrenceId: String
    ) {
        when (
            exitCoordinator.requestExit(
                automation = automation,
                reason = reason,
                occurrenceId = occurrenceId
            )
        ) {
            is ExitCoordinatorResult.Executed,
            ExitCoordinatorResult.NotActive,
            ExitCoordinatorResult.StaleOccurrence -> clearLegacyState(automation.id)
            ExitCoordinatorResult.AlreadyInProgress,
            is ExitCoordinatorResult.RecoveryRequired -> {
                runtimeStore.current(automation.id)
                    ?.takeIf { it.source == SOURCE }
                    ?.let { markLegacyActive(it) }
            }
        }
    }

    private suspend fun markLegacyActive(state: AutomationRuntimeState) {
        activeStore.markActive(SOURCE, state.sourceKey)
    }

    private suspend fun clearLegacyState(automationId: String) {
        activeStore.clearAutomation(SOURCE, automationId)
    }

    private fun monitoredTrigger(automation: Automation): Trigger? =
        automation.triggers.firstOrNull {
            it.type == TriggerType.DEVICE || it.type == TriggerType.HEADPHONE
        }

    private fun configuredEvent(trigger: Trigger): String =
        if (trigger.type == TriggerType.HEADPHONE) {
            when (trigger.config["event"] ?: "CONNECTED") {
                "DISCONNECTED" -> "HEADSET_DISCONNECTED"
                else -> "HEADSET_CONNECTED"
            }
        } else {
            trigger.config["event"] ?: "SCREEN_ON"
        }

    private fun readCurrentSnapshot(): Map<String, Boolean?> {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val screenOn = powerManager?.let { runCatching { it.isInteractive }.getOrNull() }
        val charging = batteryManager?.let { runCatching { it.isCharging }.getOrNull() }
        val headset = audioManager?.let { runCatching { it.hasWiredOutputDevice() }.getOrNull() }

        return mapOf(
            "SCREEN_ON" to screenOn,
            "SCREEN_OFF" to screenOn?.not(),
            "POWER_CONNECTED" to charging,
            "POWER_DISCONNECTED" to charging?.not(),
            "HEADSET_CONNECTED" to headset,
            "HEADSET_DISCONNECTED" to headset?.not()
        )
    }

    /** Equivalent modern check for the deprecated wired-headset state flag. */
    private fun AudioManager.hasWiredOutputDevice(): Boolean =
        getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            device.type in WIRED_OUTPUT_DEVICE_TYPES
        }

    private companion object {
        const val SOURCE = "device"
        val WIRED_OUTPUT_DEVICE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET
        )
    }
}
