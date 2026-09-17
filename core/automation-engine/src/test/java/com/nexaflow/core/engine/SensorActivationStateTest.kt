package com.nexaflow.core.engine

import org.junit.Assert.*
import org.junit.Test

class SensorActivationStateTest {
    @Test fun unrelatedSensorCannotEndAnActiveCondition() {
        val state = SensorActivationState()
        state.add("task", "PRESSURE")
        assertFalse(state.remove("task", "TEMPERATURE"))
        assertTrue(state.isActive("task"))
        assertTrue(state.remove("task", "PRESSURE"))
        assertFalse(state.isActive("task"))
        assertFalse(state.remove("task", "PRESSURE"))
    }

    @Test fun exitWaitsForLastActiveSensorAndKeepsTasksIndependent() {
        val state = SensorActivationState()
        state.add("a", "PRESSURE")
        state.add("a", "LIGHT")
        state.add("b", "PRESSURE")
        assertFalse(state.remove("a", "PRESSURE"))
        assertTrue(state.isActive("a"))
        assertTrue(state.remove("a", "LIGHT"))
        assertTrue(state.isActive("b"))
    }

    @Test fun editsAndDisablePruneOnlyStaleActivations() {
        val state = SensorActivationState()
        state.add("a", "PRESSURE")
        state.add("a", "LIGHT")
        state.add("disabled", "GRAVITY")
        state.retain(mapOf("a" to setOf("LIGHT")))
        assertFalse(state.contains("a", "PRESSURE"))
        assertFalse(state.isActive("disabled"))
        assertTrue(state.remove("a", "LIGHT"))
        state.add("a", "HINGE")
        state.clear()
        assertFalse(state.isActive("a"))
    }
}
