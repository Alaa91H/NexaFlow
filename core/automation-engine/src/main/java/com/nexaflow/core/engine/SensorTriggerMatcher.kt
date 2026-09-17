package com.nexaflow.core.engine

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.NumericSensors
import java.util.Locale
import com.nexaflow.domain.models.TriggerType

/**
 * Sensor trigger matching, kept pure so it is unit-testable without a real
 * [android.hardware.SensorManager]. The monitor feeds live sensor readings
 * into [matches]; this object decides whether a reading satisfies a trigger's
 * configured conditions and whether the condition *ended* (so the engine can
 * fire the task's exit behavior).
 *
 * Config keys (see [TriggerType.SENSOR]):
 *  - `sensor`: PROXIMITY | SHAKE | LIGHT | STEP | numeric modes from [NumericSensors]
 *  - `event`: COVERED/UNCOVERED (proximity), ABOVE/BELOW (light)
 *  - `threshold`: lux value for LIGHT (int)
 *  - `sensitivity`: shake g-force threshold (default 14)
 */
object SensorTriggerMatcher {

    /** The sensor kind a trigger watches, or null for unknown config. */
    fun sensorOf(config: Map<String, String>): String =
        config["sensor"].orEmpty().uppercase(Locale.ROOT)

    /**
     * Returns true when a fresh reading satisfies the trigger's condition.
     * Proximity: covered when `distance < maxRange * 0.5` (maxRange 0 means a
     * digital sensor → treat any distance < 1 cm as covered).
     * Light: above/below [threshold] lux.
     * Shake: |g| over [sensitivity] (transient — see [isStateful]).
     * Step: any positive step delta (transient).
     */
    fun matches(
        config: Map<String, String>,
        sensor: String,
        distanceCm: Float,
        lux: Float,
        shakeG: Float,
        stepDelta: Int,
        maxRangeCm: Float,
        value: Float = 0f
    ): Boolean = validReading(sensor, distanceCm, lux, shakeG, maxRangeCm, value) && when (sensor) {
        "PROXIMITY" -> {
            val event = config["event"] ?: "COVERED"
            val covered = if (maxRangeCm <= 0f) distanceCm < 1f else distanceCm < maxRangeCm * 0.5f
            if (event == "UNCOVERED") !covered else covered
        }
        "LIGHT" -> {
            val event = config["event"] ?: "ABOVE"
            val threshold = (config["threshold"]?.toFloatOrNull() ?: 200f)
            if (event == "BELOW") lux < threshold else lux > threshold
        }
        "SHAKE" -> {
            val sensitivity = config["sensitivity"]?.toFloatOrNull() ?: 14f
            shakeG > sensitivity
        }
        "STEP" -> stepDelta > 0
        else -> sensor in NumericSensors.specs && NumericSensors.matches(config, value)
    }

    /** Whether the trigger's condition can *end* (drives exit behavior). */
    fun isStateful(sensor: String): Boolean = sensor == "PROXIMITY" || sensor == "LIGHT" || sensor in NumericSensors.specs

    /**
     * Returns true when the condition that [config] watches has ended, i.e.
     * the opposite of [matches] for stateful sensors. Transient sensors
     * (shake/step) never "end" — their exit behavior is a no-op.
     */
    fun ended(
        config: Map<String, String>,
        sensor: String,
        distanceCm: Float,
        lux: Float,
        shakeG: Float,
        stepDelta: Int,
        maxRangeCm: Float,
        value: Float = 0f
    ): Boolean = isStateful(sensor) && validReading(sensor, distanceCm, lux, shakeG, maxRangeCm, value) && !matches(
        config, sensor, distanceCm, lux, shakeG, stepDelta, maxRangeCm, value
    )

    private fun validReading(sensor: String, distanceCm: Float, lux: Float, shakeG: Float, maxRangeCm: Float, value: Float): Boolean =
        when (sensor) {
            "PROXIMITY" -> distanceCm.isFinite() && maxRangeCm.isFinite()
            "LIGHT" -> lux.isFinite()
            "SHAKE" -> shakeG.isFinite()
            "STEP" -> true
            else -> value.isFinite()
        }

    /** Automations (enabled) with at least one SENSOR trigger of [sensor]. */
    fun automationsFor(
        automations: List<Automation>,
        sensor: String
    ): List<Automation> = automations.filter { automation ->
        automation.enabled && automation.triggers.any { trigger ->
            trigger.type == TriggerType.SENSOR && sensorOf(trigger.config) == sensor
        }
    }
}
