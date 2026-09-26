package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ACTION_AUTOMATIONS_CHANGED
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.TriggerOccurrence
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.NumericSensors
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires automations with a SENSOR trigger from live device sensors: proximity
 * (covered/uncovered), light (above/below a lux threshold), shake (linear
 * acceleration magnitude), step counter and the numeric modes in [NumericSensors].
 * Stateful sensors retain independent activations. Exit runs after the final
 * active sensor condition for the task ends.
 *
 * Battery-friendly: listeners are registered ONLY for sensor kinds that at
 * least one enabled automation watches, and the automation set is refreshed on
 * ACTION_AUTOMATIONS_CHANGED (and on initialize), so toggling a task off
 * unregisters its sensors immediately.
 */
@Singleton
class SensorMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    private val runtimeStore: AutomationRuntimeStore,
    private val exitCoordinator: ExitCoordinator,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    @Volatile
    private var automations: List<Automation> = emptyList()

    private val lastRunAt = ConcurrentHashMap<String, Long>()
    /** Automations currently in their triggered state (fires exit on the opposite event). */
    private val activeStates = SensorActivationState()
    private val stateMutex = Mutex()
    @Volatile private var monitorScope: CoroutineScope? = null
    private val registeredListeners = mutableSetOf<SensorEventListener>()
    /** Transient events are throttled; state changes must never be dropped. */
    private val lastSensorEventAt = ConcurrentHashMap<String, Long>()
    /** Cached candidates per sensor — rebuilt only on refresh, not per reading. */
    private var candidatesBySensor: Map<String, List<Automation>> = emptyMap()

    private data class PendingSensorOperation(
        val automation: Automation,
        val sensor: String,
        val enter: Boolean,
        val matchedTriggerIndices: Set<Int> = emptySet(),
        val occurrenceId: String? = null,
        val lifecycleStart: Boolean = false,
    )

    private val sensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    // ---- sensor listeners -------------------------------------------------

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.isEmpty()) return
            handleReading(
                "PROXIMITY",
                distanceCm = event.values[0],
                maxRangeCm = event.sensor.maximumRange
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val lightListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.isEmpty()) return
            handleReading("LIGHT", lux = event.values[0])
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.size < 3) return
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            handleReading("SHAKE", shakeG = sqrt(x * x + y * y + z * z))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val stepListener = object : SensorEventListener {
        var lastSteps: Int = -1

        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.isEmpty()) return
            val steps = event.values[0].toInt()
            val delta = if (lastSteps >= 0) (steps - lastSteps).coerceAtLeast(0) else 0
            lastSteps = steps
            if (delta > 0) handleReading("STEP", stepDelta = delta)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val numericListeners = NumericSensors.specs.mapValues { (kind, _) ->
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val value = NumericSensors.reading(kind, event.values) ?: return
                handleReading(kind, value = value)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
    }

    // ---- lifecycle --------------------------------------------------------

    fun initialize() {
        if (registered) return
        registered = true
        val runningScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]) + Dispatchers.Main.immediate)
        monitorScope = runningScope
        val filter = IntentFilter(ACTION_AUTOMATIONS_CHANGED)
        // Internal app broadcast (AUTOMATIONS_CHANGED) — never exported.
        runCatching {
            ContextCompat.registerReceiver(context, changeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        runningScope.launch { stateMutex.withLock { refresh() } }
    }

    fun stop() {
        if (!registered) return
        registered = false
        monitorScope?.cancel()
        monitorScope = null
        runCatching { context.unregisterReceiver(changeReceiver) }
        unregisterAll()
        activeStates.clear()
        automations = emptyList()
        candidatesBySensor = emptyMap()
        lastSensorEventAt.clear()
        lastRunAt.clear()
        stepListener.lastSteps = -1
    }

    /** Reloads the automation set and (un)registers sensors to match it. */
    private suspend fun refresh() {
        if (!registered) return
        val fresh = try {
            repository.getAutomations().first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return // Keep the last known registration set on a transient repository failure.
        }
        if (!registered || monitorScope?.isActive != true) return
        automations = fresh
        candidatesBySensor = mapOf(
            "PROXIMITY" to SensorTriggerMatcher.automationsFor(fresh, "PROXIMITY"),
            "LIGHT" to SensorTriggerMatcher.automationsFor(fresh, "LIGHT"),
            "SHAKE" to SensorTriggerMatcher.automationsFor(fresh, "SHAKE"),
            "STEP" to SensorTriggerMatcher.automationsFor(fresh, "STEP")
        ) + NumericSensors.specs.keys.associateWith { SensorTriggerMatcher.automationsFor(fresh, it) }
        // Re-arm the durable active set before the first reading reconciles:
        // stateful sensors (proximity/light) deliver readings continuously, so
        // a task whose condition already ended while the process was down
        // fires its missed exit on the next reading.
        rearmFromLedger(fresh)
        updateRegistrations(fresh)
    }

    /**
     * Restores durable sensor ownership before the first live reading.
     *
     * The runtime ledger owns whether the automation still has an exit to
     * execute; ActiveTriggerStore stores the per-sensor detail needed to know
     * when the *final* stateful sensor condition ends. Legacy id-only markers
     * are promoted conservatively to every configured stateful sensor.
     */
    internal suspend fun rearmFromLedger(fresh: List<Automation>) {
        val byId = fresh.associateBy { it.id }
        val configuredStateful = fresh.associate { automation ->
            automation.id to automation.triggers
                .filter { it.type == TriggerType.SENSOR }
                .map { SensorTriggerMatcher.sensorOf(it.config) }
                .filter { SensorTriggerMatcher.isStateful(it) }
                .toSet()
        }

        activeStates.retain(
            fresh.filter { it.enabled }.associate { automation ->
                automation.id to configuredStateful[automation.id].orEmpty()
            }
        )

        // Runtime ownership is authoritative. Reconstruct at least the sensor
        // set encoded by the lifecycle even if the compatibility mirror was
        // lost during a crash.
        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = byId[state.automationId]
                when {
                    automation == null -> {
                        activeStates.removeAutomation(state.automationId)
                        activeStore.clearAutomation(SOURCE, state.automationId)
                    }
                    !automation.enabled ||
                        configuredStateful[automation.id].isNullOrEmpty() -> {
                        requestExit(
                            automation = automation,
                            reason = ExitReason.AUTOMATION_DISABLED,
                            occurrenceId = state.occurrenceId
                        )
                    }
                    else -> {
                        val restoredSensors = parseSourceSensors(state.sourceKey)
                            .filter { it in configuredStateful[automation.id].orEmpty() }
                            .toSet()
                        if (restoredSensors.isEmpty()) {
                            // The configuration no longer contains any sensor
                            // that can own this occurrence. Close the old
                            // lifecycle before a newly configured sensor can
                            // establish another one.
                            requestExit(
                                automation = automation,
                                reason = ExitReason.AUTOMATION_DISABLED,
                                occurrenceId = state.occurrenceId
                            )
                        } else {
                            restoredSensors.forEach { sensor ->
                                activeStates.add(automation.id, sensor)
                                activeStore.markActive(SOURCE, "${automation.id}|$sensor")
                            }
                        }
                    }
                }
            }

        // Upgrade pre-runtime-ledger compatibility keys and restore every
        // per-sensor condition they can prove.
        activeStore.activeKeys(SOURCE).forEach { key ->
            val automationId = key.substringBefore('|')
            val automation = byId[automationId]
            val kinds = configuredStateful[automationId].orEmpty()

            if (automation == null) {
                activeStates.removeAutomation(automationId)
                activeStore.clearAutomation(SOURCE, automationId)
                return@forEach
            }

            val current = runtimeStore.current(automationId)
            if (current != null && current.source != SOURCE) {
                // Another stateful source owns this task. A stale sensor mirror
                // must never authorize an exit for that foreign occurrence.
                activeStates.removeAutomation(automationId)
                activeStore.clearAutomation(SOURCE, automationId)
                return@forEach
            }

            val sensor = key.substringAfter('|', "")
            when {
                sensor.isNotEmpty() && sensor in kinds -> {
                    activeStates.add(automationId, sensor)
                }
                sensor.isEmpty() && kinds.isNotEmpty() -> {
                    kinds.forEach { kind ->
                        activeStates.add(automationId, kind)
                        activeStore.markActive(SOURCE, "$automationId|$kind")
                    }
                    activeStore.clearActive(SOURCE, key)
                }
                else -> {
                    activeStore.clearActive(SOURCE, key)
                }
            }

            if (!automation.enabled || kinds.isEmpty()) {
                current
                    ?.takeIf { it.source == SOURCE }
                    ?.let { state ->
                        requestExit(
                            automation = automation,
                            reason = ExitReason.AUTOMATION_DISABLED,
                            occurrenceId = state.occurrenceId
                        )
                    }
                if (current == null) {
                    activeStates.removeAutomation(automationId)
                    activeStore.clearAutomation(SOURCE, automationId)
                }
                return@forEach
            }

            if (runtimeStore.current(automationId) == null &&
                activeStates.isActive(automationId)
            ) {
                val sensors = activeStates.sensorsFor(automationId)
                runtimeStore.activateStrict(
                    AutomationRuntimeState(
                        automationId = automationId,
                        occurrenceId = "legacy:$SOURCE:$automationId:${UUID.randomUUID()}",
                        source = SOURCE,
                        sourceKey = sourceKeyFor(automationId, sensors),
                        lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                        activatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun sourceKeyFor(automationId: String, sensors: Set<String>): String =
        "$automationId|${sensors.sorted().joinToString(",")}"

    private fun parseSourceSensors(sourceKey: String): Set<String> =
        sourceKey.substringAfter('|', "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun updateRegistrations(automations: List<Automation>) {
        val wanted = SensorTriggerMatcher.automationsFor(automations, "PROXIMITY").isNotEmpty()
        setRegistration(SENSOR_PROXIMITY, proximityListener, wanted)
        setRegistration(SENSOR_LIGHT, lightListener, wantedBy("LIGHT", automations))
        setRegistration(SENSOR_SHAKE, shakeListener, wantedBy("SHAKE", automations))
        setRegistration(SENSOR_STEP, stepListener, wantedBy("STEP", automations))
        numericListeners.forEach { (kind, listener) ->
            setRegistration(NumericSensors.specs.getValue(kind).type, listener, wantedBy(kind, automations))
        }
    }

    private fun wantedBy(sensor: String, automations: List<Automation>): Boolean =
        SensorTriggerMatcher.automationsFor(automations, sensor).isNotEmpty()

    private fun setRegistration(
        sensorType: Int,
        listener: SensorEventListener,
        wanted: Boolean
    ) {
        if (!wanted) {
            if (registeredListeners.remove(listener)) runCatching { sensorManager.unregisterListener(listener) }
            if (listener === stepListener) stepListener.lastSteps = -1
            return
        }
        if (listener in registeredListeners) return
        val sensor = sensorManager.getDefaultSensor(sensorType) ?: return
        if (runCatching { sensorManager.registerListener(listener, sensor, SENSOR_DELAY, handler) }.getOrDefault(false)) {
            registeredListeners.add(listener)
        }
    }

    private fun unregisterAll() {
        registeredListeners.toList().forEach { listener -> runCatching { sensorManager.unregisterListener(listener) } }
        registeredListeners.clear()
    }

    // ---- event handling ---------------------------------------------------

    private fun handleReading(
        sensor: String,
        distanceCm: Float = 0f,
        lux: Float = 0f,
        shakeG: Float = 0f,
        stepDelta: Int = 0,
        maxRangeCm: Float = 0f,
        value: Float = 0f
    ) {
        val runningScope = monitorScope?.takeIf { registered && it.isActive } ?: return
        if (!isValidReading(sensor, distanceCm, lux, shakeG, stepDelta, maxRangeCm, value)) {
            return
        }

        val elapsedRealtimeMs = SystemClock.elapsedRealtime()
        val occurredAtEpochMs = System.currentTimeMillis()
        if (!SensorTriggerMatcher.isStateful(sensor)) {
            val last = lastSensorEventAt[sensor]
            if (last != null && elapsedRealtimeMs - last < TRANSIENT_DEBOUNCE_MS) return
            lastSensorEventAt[sensor] = elapsedRealtimeMs
        }

        runningScope.launch {
            processReading(
                sensor = sensor,
                distanceCm = distanceCm,
                lux = lux,
                shakeG = shakeG,
                stepDelta = stepDelta,
                maxRangeCm = maxRangeCm,
                value = value,
                elapsedRealtimeMs = elapsedRealtimeMs,
                occurredAtEpochMs = occurredAtEpochMs,
                candidates = candidatesBySensor[sensor].orEmpty(),
                requireLiveMonitor = true
            )
        }
    }

    /**
     * Deterministic sensor lifecycle seam used by regression tests. Production
     * listeners use the same [processReading] path with the cached candidate
     * set built during [refresh].
     */
    internal suspend fun reconcileReadingForTest(
        sensor: String,
        distanceCm: Float = 0f,
        lux: Float = 0f,
        shakeG: Float = 0f,
        stepDelta: Int = 0,
        maxRangeCm: Float = 0f,
        value: Float = 0f,
        elapsedRealtimeMs: Long = 1_000L,
        occurredAtEpochMs: Long = 1_000L
    ) {
        if (!isValidReading(sensor, distanceCm, lux, shakeG, stepDelta, maxRangeCm, value)) {
            return
        }
        val fresh = repository.getAutomations().first()
        processReading(
            sensor = sensor,
            distanceCm = distanceCm,
            lux = lux,
            shakeG = shakeG,
            stepDelta = stepDelta,
            maxRangeCm = maxRangeCm,
            value = value,
            elapsedRealtimeMs = elapsedRealtimeMs,
            occurredAtEpochMs = occurredAtEpochMs,
            candidates = SensorTriggerMatcher.automationsFor(fresh, sensor),
            requireLiveMonitor = false
        )
    }

    private suspend fun processReading(
        sensor: String,
        distanceCm: Float,
        lux: Float,
        shakeG: Float,
        stepDelta: Int,
        maxRangeCm: Float,
        value: Float,
        elapsedRealtimeMs: Long,
        occurredAtEpochMs: Long,
        candidates: List<Automation>,
        requireLiveMonitor: Boolean
    ) = stateMutex.withLock {
        if (requireLiveMonitor &&
            (!registered || monitorScope?.isActive != true)
        ) {
            return@withLock
        }

        val stateful = SensorTriggerMatcher.isStateful(sensor)
        candidates.forEach { automation ->
            val matchedTriggerIndices = automation.triggers.mapIndexedNotNull { index, trigger ->
                index.takeIf {
                    trigger.type == TriggerType.SENSOR &&
                        SensorTriggerMatcher.sensorOf(trigger.config) == sensor &&
                        SensorTriggerMatcher.matches(
                            trigger.config,
                            sensor,
                            distanceCm,
                            lux,
                            shakeG,
                            stepDelta,
                            maxRangeCm,
                            value
                        )
                }
            }.toSet()
            val fired = matchedTriggerIndices.isNotEmpty()

            if (!stateful) {
                if (!fired) return@forEach
                val last = lastRunAt[automation.id]
                if (last != null && elapsedRealtimeMs - last < automation.cooldownMillis) {
                    return@forEach
                }
                lastRunAt[automation.id] = elapsedRealtimeMs
                executionEngine.runAutomation(
                    automation = automation,
                    completeExitOnFinish = true,
                    triggerOccurrence = TriggerOccurrence(
                        matchedTriggerIndices = matchedTriggerIndices,
                        occurredAtEpochMs = occurredAtEpochMs,
                        sourceId = SOURCE
                    )
                )
                return@forEach
            }

            if (fired) {
                handleStatefulEnter(
                    automation = automation,
                    sensor = sensor,
                    matchedTriggerIndices = matchedTriggerIndices,
                    elapsedRealtimeMs = elapsedRealtimeMs,
                    occurredAtEpochMs = occurredAtEpochMs
                )
            } else if (activeStates.contains(automation.id, sensor)) {
                handleStatefulExit(
                    automation = automation,
                    sensor = sensor
                )
            }
        }
    }

    private suspend fun handleStatefulEnter(
        automation: Automation,
        sensor: String,
        matchedTriggerIndices: Set<Int>,
        elapsedRealtimeMs: Long,
        occurredAtEpochMs: Long
    ) {
        val last = lastRunAt[automation.id]
        val canRun = last == null || elapsedRealtimeMs - last >= automation.cooldownMillis
        val alreadySensorActive = activeStates.contains(automation.id, sensor)
        val automationAlreadyActive = activeStates.isActive(automation.id)
        val current = runtimeStore.current(automation.id)

        if (current != null && current.source != SOURCE) {
            // Another stateful source owns the task. Sensor compatibility state
            // must never authorize a foreign lifecycle exit.
            activeStates.removeAutomation(automation.id)
            activeStore.clearAutomation(SOURCE, automation.id)
            return
        }

        if (!alreadySensorActive && (canRun || automationAlreadyActive)) {
            activeStates.add(automation.id, sensor)
        }

        val sensors = activeStates.sensorsFor(automation.id)
        if (sensors.isEmpty()) return

        if (current?.source == SOURCE) {
            activeStore.markActive(SOURCE, "${automation.id}|$sensor")
            runtimeStore.updateActiveSourceKey(
                automationId = automation.id,
                occurrenceId = current.occurrenceId,
                sourceKey = sourceKeyFor(automation.id, sensors)
            )
            if (canRun) {
                lastRunAt[automation.id] = elapsedRealtimeMs
                executionEngine.runAutomation(
                    automation = automation,
                    triggerOccurrence = TriggerOccurrence(
                        matchedTriggerIndices = matchedTriggerIndices,
                        occurredAtEpochMs = occurredAtEpochMs,
                        sourceId = SOURCE
                    )
                )
            }
            return
        }

        // The first stateful sensor to enter establishes the durable lifecycle.
        // Do not write the compatibility mirror until ExecutionEngine confirms
        // occurrence admission.
        if (!automationAlreadyActive && canRun) {
            val occurrenceId = "sensor:${automation.id}:${UUID.randomUUID()}"
            lastRunAt[automation.id] = elapsedRealtimeMs
            executionEngine.runAutomation(
                automation = automation,
                lifecycleContext = AutomationLifecycleContext(
                    occurrenceId = occurrenceId,
                    source = SOURCE,
                    sourceKey = sourceKeyFor(automation.id, sensors)
                ),
                triggerOccurrence = TriggerOccurrence(
                    matchedTriggerIndices = matchedTriggerIndices,
                    occurredAtEpochMs = occurredAtEpochMs,
                    sourceId = SOURCE
                )
            )
            val admitted = runtimeStore.current(automation.id)
                ?.let { state ->
                    state.source == SOURCE && state.occurrenceId == occurrenceId
                } == true
            if (admitted) {
                sensors.forEach { activeSensor ->
                    activeStore.markActive(SOURCE, "${automation.id}|$activeSensor")
                }
            } else {
                // Constraints/admission may reject the run before lifecycle
                // ownership exists. Do not leave a sensor condition armed as
                // though main actions had actually started.
                activeStates.removeAutomation(automation.id)
                activeStore.clearAutomation(SOURCE, automation.id)
                lastRunAt.remove(automation.id)
            }
        }
    }

    private suspend fun handleStatefulExit(
        automation: Automation,
        sensor: String
    ) {
        val finalConditionEnded = activeStates.remove(automation.id, sensor)
        if (!finalConditionEnded) {
            activeStore.clearActive(SOURCE, "${automation.id}|$sensor")
            runtimeStore.current(automation.id)
                ?.takeIf { it.source == SOURCE }
                ?.let { state ->
                    runtimeStore.updateActiveSourceKey(
                        automationId = automation.id,
                        occurrenceId = state.occurrenceId,
                        sourceKey = sourceKeyFor(
                            automation.id,
                            activeStates.sensorsFor(automation.id)
                        )
                    )
                }
            return
        }

        val state = runtimeStore.current(automation.id)
        if (state?.source != SOURCE) {
            activeStore.clearAutomation(SOURCE, automation.id)
            return
        }

        when (
            exitCoordinator.requestExit(
                automation = automation,
                reason = ExitReason.TRIGGER_FALSE,
                occurrenceId = state.occurrenceId
            )
        ) {
            is ExitCoordinatorResult.Executed,
            ExitCoordinatorResult.NotActive,
            ExitCoordinatorResult.StaleOccurrence -> {
                activeStore.clearAutomation(SOURCE, automation.id)
            }
            ExitCoordinatorResult.AlreadyInProgress,
            is ExitCoordinatorResult.RecoveryRequired -> {
                // The end behavior still belongs to this occurrence. Restore
                // the volatile sensor marker so repeated readings cannot create
                // a fresh lifecycle while recovery remains pending.
                activeStates.add(automation.id, sensor)
                activeStore.markActive(SOURCE, "${automation.id}|$sensor")
            }
        }
    }

    private fun isValidReading(
        sensor: String,
        distanceCm: Float,
        lux: Float,
        shakeG: Float,
        stepDelta: Int,
        maxRangeCm: Float,
        value: Float
    ): Boolean = when (sensor) {
        "PROXIMITY" -> distanceCm.isFinite() && maxRangeCm.isFinite()
        "LIGHT" -> lux.isFinite()
        "SHAKE" -> shakeG.isFinite()
        "STEP" -> stepDelta > 0
        else -> value.isFinite()
    }

    private val changeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            monitorScope?.launch { stateMutex.withLock { refresh() } }
        }
    }

    private companion object {
        const val SENSOR_PROXIMITY = Sensor.TYPE_PROXIMITY
        const val SENSOR_LIGHT = Sensor.TYPE_LIGHT
        const val SENSOR_SHAKE = Sensor.TYPE_LINEAR_ACCELERATION
        const val SENSOR_STEP = Sensor.TYPE_STEP_COUNTER
        // Normal rate keeps battery impact low; shake/step only need coarse
        // samples and proximity/light are stateful, not time-critical.
        const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_NORMAL
        const val TRANSIENT_DEBOUNCE_MS = 200L
        const val SOURCE = "sensor"
    }
}
