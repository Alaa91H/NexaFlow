package com.nexaflow.domain.models

/** Android sensor type identifiers are stable; availability is probed on the device. */
object NumericSensors {
    data class Spec(val type: Int, val unit: String, val vector: Boolean = false)
    val specs = mapOf(
        "PRESSURE" to Spec(6, "hPa"),
        "TEMPERATURE" to Spec(13, "°C"),
        "HUMIDITY" to Spec(12, "%"),
        "MAGNETIC" to Spec(2, "µT", true),
        "ACCELERATION" to Spec(10, "m/s²", true),
        "GYROSCOPE" to Spec(4, "rad/s", true),
        "GRAVITY" to Spec(9, "m/s²", true),
        "HINGE" to Spec(36, "°")
    )

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
        val clean = previous - setOf("event", "threshold", "upperThreshold", "sensitivity")
        val defaults = when (kind) {
            "PROXIMITY" -> mapOf("event" to "COVERED")
            "LIGHT" -> mapOf("event" to "ABOVE", "threshold" to "200")
            "SHAKE" -> mapOf("sensitivity" to "14")
            "STEP" -> emptyMap()
            in specs -> mapOf("event" to "ABOVE", "threshold" to when (kind) {
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
        if (!value.isFinite() || !threshold.isFinite()) return false
        return when (config["event"] ?: "ABOVE") {
            "ABOVE" -> value > threshold
            "BELOW" -> value < threshold
            "AT_LEAST" -> value >= threshold
            "AT_MOST" -> value <= threshold
            "BETWEEN" -> config["upperThreshold"]?.toFloatOrNull()?.let {
                it.isFinite() && it >= threshold && value in threshold..it
            } ?: false
            else -> false
        }
    }
}
