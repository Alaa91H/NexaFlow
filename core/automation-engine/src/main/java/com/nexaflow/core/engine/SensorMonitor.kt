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
        val enter: Boolean,
        val matchedTriggerIndices: Set<Int> = emptySet(),
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
     * Restores the durable active ids into the in-memory map. Stale keys for
     * deleted/disabled automations are pruned.
     */
    private suspend fun rearmFromLedger(fresh: List<Automation>) {
        val allowed = fresh.filter { it.enabled }.associate { automation ->
            automation.id to automation.triggers.filter { it.type == TriggerType.SENSOR }
                .map { SensorTriggerMatcher.sensorOf(it.config) }.filter { kind ->
                    val type = when (kind) {
                        "PROXIMITY" -> SENSOR_PROXIMITY
                        "LIGHT" -> SENSOR_LIGHT
                        else -> NumericSensors.specs[kind]?.type
                    }
                    SensorTriggerMatcher.isStateful(kind) && type != null && sensorManager.getDefaultSensor(type) != null
                }.toSet()
        }
        activeStates.retain(allowed)
        activeStore.activeKeys(SOURCE).forEach { key ->
            val id = key.substringBefore('|')
            val sensor = key.substringAfter('|', "")
            val kinds = allowed[id].orEmpty()
            when {
                sensor.isNotEmpty() && sensor in kinds -> activeStates.add(id, sensor)
                sensor.isEmpty() && kinds.isNotEmpty() -> {
                    // Earlier versions stored only the automation id. Reconcile each
                    // configured stateful sensor before deciding its missed exit.
                    kinds.forEach { kind ->
                        activeStates.add(id, kind)
                        activeStore.markActive(SOURCE, "$id|$kind")
                    }
                    activeStore.clearActive(SOURCE, key)
                }
                else -> activeStore.clearActive(SOURCE, key)
            }
        }
    }

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
        val readingValid = when (sensor) {
            "PROXIMITY" -> distanceCm.isFinite() && maxRangeCm.isFinite()
            "LIGHT" -> lux.isFinite()
            "SHAKE" -> shakeG.isFinite()
            "STEP" -> stepDelta > 0
            else -> value.isFinite()
        }
        if (!readingValid) return
        val now = SystemClock.elapsedRealtime()
        val occurredAtEpochMs = System.currentTimeMillis()
        if (!SensorTriggerMatcher.isStateful(sensor)) {
            val last = lastSensorEventAt[sensor]
            if (last != null && now - last < 200) return
            lastSensorEventAt[sensor] = now
        }
        runningScope.launch {
            val operations = stateMutex.withLock {
                if (!registered || !runningScope.isActive) {
                    return@withLock emptyList<PendingSensorOperation>()
                }
                val candidates = candidatesBySensor[sensor].orEmpty()
                val pending = mutableListOf<PendingSensorOperation>()
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
                                    value,
                                )
                        }
                    }.toSet()
                    val fired = matchedTriggerIndices.isNotEmpty()
                    val stateful = SensorTriggerMatcher.isStateful(sensor)
                    if (fired) {
                        val last = lastRunAt[automation.id]
                        val canRun = last == null || now - last >= automation.cooldownMillis
                        if (stateful && (canRun || activeStates.isActive(automation.id))) {
                            val entered = activeStates.add(automation.id, sensor)
                            if (entered || canRun) activeStore.markActive(SOURCE, "${automation.id}|$sensor")
                        }
                        if (canRun) {
                            lastRunAt[automation.id] = now
                            pending += PendingSensorOperation(
                                automation = automation,
                                enter = true,
                                matchedTriggerIndices = matchedTriggerIndices,
                            )
                        }
                    } else if (stateful && activeStates.contains(automation.id, sensor)) {
                        val ended = activeStates.remove(automation.id, sensor)
                        activeStore.clearActive(SOURCE, "${automation.id}|$sensor")
                        if (ended) {
                            pending += PendingSensorOperation(
                                automation = automation,
                                enter = false,
                            )
                        }
                    }
                }
                pending
            }
            operations.forEach { operation ->
                if (operation.enter) {
                    executionEngine.runAutomation(
                        automation = operation.automation,
                        completeExitOnFinish = !SensorTriggerMatcher.isStateful(sensor),
                        triggerOccurrence = TriggerOccurrence(
                            matchedTriggerIndices = operation.matchedTriggerIndices,
                            occurredAtEpochMs = occurredAtEpochMs,
                            sourceId = SOURCE,
                        ),
                    )
                } else {
                    executionEngine.runExit(operation.automation)
                }
            }
        }
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
        const val SOURCE = "sensor"
    }
}
