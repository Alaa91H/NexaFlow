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
import androidx.core.content.ContextCompat
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ACTION_AUTOMATIONS_CHANGED
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires automations with a SENSOR trigger from live device sensors: proximity
 * (covered/uncovered), light (above/below a lux threshold), shake (linear
 * acceleration magnitude) and step counter. Stateful sensors (proximity/light)
 * also fire the task's exit behavior when the condition ends.
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
    /** Automations currently in their triggered state (fires exit on end). */
    private val activeStates = ConcurrentHashMap<String, Boolean>()

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
        private var lastSteps: Int = -1

        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.isEmpty()) return
            val steps = event.values[0].toInt()
            val delta = if (lastSteps >= 0) (steps - lastSteps).coerceAtLeast(0) else 0
            lastSteps = steps
            if (delta > 0) handleReading("STEP", stepDelta = delta)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    // ---- lifecycle --------------------------------------------------------

    fun initialize() {
        if (registered) return
        registered = true
        val filter = IntentFilter(ACTION_AUTOMATIONS_CHANGED)
        // Internal app broadcast (AUTOMATIONS_CHANGED) — never exported.
        runCatching {
            ContextCompat.registerReceiver(context, changeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        scope.launch { refresh() }
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(changeReceiver) }
        unregisterAll()
        activeStates.clear()
    }

    /** Reloads the automation set and (un)registers sensors to match it. */
    private suspend fun refresh() {
        val fresh = runCatching { repository.getAutomations().first() }.getOrDefault(emptyList())
        automations = fresh
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
        val enabledIds = fresh.filter { it.enabled }.map { it.id }.toSet()
        activeStore.activeKeys(SOURCE).forEach { key ->
            val id = key.substringBefore('|')
            if (id in enabledIds) {
                activeStates[id] = true
            } else {
                activeStore.clearAutomation(SOURCE, id)
            }
        }
    }

    private fun updateRegistrations(automations: List<Automation>) {
        val wanted = SensorTriggerMatcher.automationsFor(automations, "PROXIMITY").isNotEmpty()
        setRegistration(SENSOR_PROXIMITY, proximityListener, wanted)
        setRegistration(SENSOR_LIGHT, lightListener, wantedBy("LIGHT", automations))
        setRegistration(SENSOR_SHAKE, shakeListener, wantedBy("SHAKE", automations))
        setRegistration(SENSOR_STEP, stepListener, wantedBy("STEP", automations))
    }

    private fun wantedBy(sensor: String, automations: List<Automation>): Boolean =
        SensorTriggerMatcher.automationsFor(automations, sensor).isNotEmpty()

    private fun setRegistration(
        sensorType: Int,
        listener: SensorEventListener,
        wanted: Boolean
    ) {
        val sensor = sensorManager.getDefaultSensor(sensorType) ?: return
        if (wanted) {
            runCatching {
                sensorManager.registerListener(listener, sensor, SENSOR_DELAY, handler)
            }
        } else {
            runCatching { sensorManager.unregisterListener(listener) }
        }
    }

    private fun unregisterAll() {
        runCatching { sensorManager.unregisterListener(proximityListener) }
        runCatching { sensorManager.unregisterListener(lightListener) }
        runCatching { sensorManager.unregisterListener(shakeListener) }
        runCatching { sensorManager.unregisterListener(stepListener) }
    }

    // ---- event handling ---------------------------------------------------

    private fun handleReading(
        sensor: String,
        distanceCm: Float = 0f,
        lux: Float = 0f,
        shakeG: Float = 0f,
        stepDelta: Int = 0,
        maxRangeCm: Float = 0f
    ) {
        val snapshot = automations
        if (snapshot.isEmpty()) return
        val now = System.currentTimeMillis()
        val candidates = SensorTriggerMatcher.automationsFor(snapshot, sensor)
        if (candidates.isEmpty()) return

        scope.launch {
            candidates.forEach { automation ->
                val triggers = automation.triggers.filter {
                    it.type == com.nexaflow.domain.models.TriggerType.SENSOR &&
                        SensorTriggerMatcher.sensorOf(it.config) == sensor
                }
                val fired = triggers.any {
                    SensorTriggerMatcher.matches(
                        it.config, sensor, distanceCm, lux, shakeG, stepDelta, maxRangeCm
                    )
                }
                if (fired) {
                    val last = lastRunAt[automation.id] ?: 0L
                    if (now - last > automation.cooldownMillis) {
                        lastRunAt[automation.id] = now
                        activeStates[automation.id] = true
                        activeStore.markActive(SOURCE, automation.id)
                        executionEngine.runAutomation(automation)
                    }
                } else if (SensorTriggerMatcher.isStateful(sensor) &&
                    activeStates.remove(automation.id) != null
                ) {
                    // The condition ended (e.g. light dropped below threshold):
                    // fire the task's exit behavior.
                    activeStore.clearAutomation(SOURCE, automation.id)
                    executionEngine.runExit(automation)
                }
            }
        }
    }

    private val changeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            scope.launch { refresh() }
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
