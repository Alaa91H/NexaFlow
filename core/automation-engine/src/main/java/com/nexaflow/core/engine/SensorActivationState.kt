package com.nexaflow.core.engine

/** Each sensor owns its activation; another sensor cannot end that activation. */
internal class SensorActivationState {
    private val active = mutableMapOf<String, MutableSet<String>>()

    fun contains(automationId: String, sensor: String): Boolean = active[automationId]?.contains(sensor) == true
    fun isActive(automationId: String): Boolean = active[automationId]?.isNotEmpty() == true
    fun sensorsFor(automationId: String): Set<String> = active[automationId]?.toSet().orEmpty()
    fun add(automationId: String, sensor: String): Boolean = active.getOrPut(automationId) { mutableSetOf() }.add(sensor)

    /** True only when removing this sensor ends the final active sensor condition. */
    fun remove(automationId: String, sensor: String): Boolean {
        val sensors = active[automationId] ?: return false
        if (!sensors.remove(sensor)) return false
        if (sensors.isNotEmpty()) return false
        active.remove(automationId)
        return true
    }

    fun removeAutomation(automationId: String) {
        active.remove(automationId)
    }

    fun retain(allowed: Map<String, Set<String>>) {
        active.keys.toList().forEach { id ->
            active[id]?.retainAll(allowed[id].orEmpty())
            if (active[id].isNullOrEmpty()) active.remove(id)
        }
    }

    fun clear() = active.clear()
}
