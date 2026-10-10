package com.nexaflow.domain.models

/** Android sensor type identifiers are stable; availability is probed on the device. */
object NumericSensors {
    data class Spec(
        val type: Int,
        val unit: String,
        val vector: Boolean = false,
        val calibrationLimit: Float = 1_000f
    )
    val specs = mapOf(
        "PRESSURE" to Spec(6, "hPa", calibrationLimit = 100f),
        "TEMPERATURE" to Spec(13, "°C", calibrationLimit = 50f),
        "HUMIDITY" to Spec(12, "%", calibrationLimit = 50f),
        "MAGNETIC" to Spec(2, "µT", vector = true, calibrationLimit = 1_000f),
        "ACCELERATION" to Spec(10, "m/s²", vector = true, calibrationLimit = 20f),
        "GYROSCOPE" to Spec(4, "rad/s", vector = true, calibrationLimit = 20f),
        "GRAVITY" to Spec(9, "m/s²", vector = true, calibrationLimit = 20f),
        "HINGE" to Spec(36, "°", calibrationLimit = 180f)
    )

    const val CALIBRATION_OFFSET_KEY = "calibrationOffset"
    const val SAMPLE_PERIOD_KEY = "samplePeriodUs"
    const val DEFAULT_SAMPLE_PERIOD_US = 200_000
    val samplePeriodsUs = setOf(20_000, 60_000, 200_000)

    fun samplePeriodUs(config: Map<String, String>): Int =
        config[SAMPLE_PERIOD_KEY]?.toIntOrNull()?.takeIf { it in samplePeriodsUs }
            ?: DEFAULT_SAMPLE_PERIOD_US

    val comparisons = setOf("ABOVE", "BELOW", "AT_LEAST", "AT_MOST", "BETWEEN")

    /** Read a scalar or vector magnitude without accepting incomplete/non-finite samples. */
    fun reading(kind: String, values: FloatArray): Float? {
        val spec = specs[kind] ?: return null
        val count = if (spec.vector) 3 else 1
        if (values.size < count || (0 until count).any { !values[it].isFinite() }) return null
        val value = if (spec.vector) {
            kotlin.math.sqrt((0 until count).sumOf { values[it].toDouble() * values[it] }).toFloat()
        } else values[0]
        return value.takeIf { it.isFinite() }
    }

    /** Changing physical quantities must not retain another sensor's event or unit. */
    fun configurationFor(kind: String, previous: Map<String, String>): Map<String, String> {
        if (previous["sensor"] == kind) return previous
        val clean = previous - setOf("event", "threshold", "upperThreshold", "sensitivity", CALIBRATION_OFFSET_KEY)
        val defaults = when (kind) {
            "PROXIMITY" -> mapOf("event" to "COVERED")
            "LIGHT" -> mapOf("event" to "ABOVE", "threshold" to "200")
            "SHAKE" -> mapOf("sensitivity" to "14")
            "STEP" -> emptyMap()
            in specs -> mapOf("event" to "ABOVE", SAMPLE_PERIOD_KEY to DEFAULT_SAMPLE_PERIOD_US.toString(), "threshold" to when (kind) {
                "PRESSURE" -> "1013.25"
                "TEMPERATURE" -> "25"
                "HUMIDITY" -> "60"
                "MAGNETIC" -> "50"
                "ACCELERATION" -> "2"
                "GYROSCOPE" -> "1"
                "GRAVITY" -> "9.81"
                "HINGE" -> "90"
                else -> "0"
            })
            else -> emptyMap()
        }
        return clean + defaults + ("sensor" to kind)
    }

    fun matches(config: Map<String, String>, value: Float): Boolean {
        val threshold = config["threshold"]?.toFloatOrNull() ?: return false
        val calibrated = calibratedValue(config["sensor"].orEmpty(), config, value) ?: return false
        if (!calibrated.isFinite() || !threshold.isFinite()) return false
        return when (config["event"] ?: "ABOVE") {
            "ABOVE" -> calibrated > threshold
            "BELOW" -> calibrated < threshold
            "AT_LEAST" -> calibrated >= threshold
            "AT_MOST" -> calibrated <= threshold
            "BETWEEN" -> config["upperThreshold"]?.toFloatOrNull()?.let {
                it.isFinite() && it >= threshold && calibrated in threshold..it
            } ?: false
            else -> false
        }
    }

    /** Applies a unit-specific offset to the already-normalized sensor value. */
    fun calibratedValue(kind: String, config: Map<String, String>, value: Float): Float? {
        if (!value.isFinite()) return null
        val rawOffset = config[CALIBRATION_OFFSET_KEY] ?: return value
        val offset = rawOffset.toFloatOrNull() ?: return null
        val limit = specs[kind]?.calibrationLimit ?: return null
        if (!offset.isFinite() || kotlin.math.abs(offset) > limit) return null
        return (value + offset).takeIf { it.isFinite() }
    }
}
