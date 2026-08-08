package com.nexaflow.domain.constraints

import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintSnapshot
import com.nexaflow.domain.models.ConstraintType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstraintEvaluatorTest {

    @Test
    fun `wifi constraint follows snapshot`() {
        val constraint = Constraint(ConstraintType.WIFI)
        assertTrue(ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(wifiConnected = true)))
        assertFalse(ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(wifiConnected = false)))
    }

    @Test
    fun `screen locked constraint follows snapshot`() {
        val constraint = Constraint(ConstraintType.SCREEN_LOCKED)
        assertTrue(ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(screenLocked = true)))
        assertFalse(ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(screenLocked = false)))
    }

    @Test
    fun `headset constraint follows snapshot`() {
        val constraint = Constraint(ConstraintType.HEADSET)
        assertTrue(ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(headsetConnected = true)))
        assertFalse(ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(headsetConnected = false)))
    }

    @Test
    fun `battery above passes only at or above threshold`() {
        val constraint = Constraint(ConstraintType.BATTERY, mapOf("direction" to "ABOVE", "level" to "80"))
        assertTrue(ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(batteryLevel = 80)))
        assertTrue(ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(batteryLevel = 99)))
        assertFalse(ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(batteryLevel = 79)))
    }

    @Test
    fun `battery below passes only at or below threshold`() {
        val constraint = Constraint(ConstraintType.BATTERY, mapOf("direction" to "BELOW", "level" to "20"))
        assertTrue(ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(batteryLevel = 20)))
        assertTrue(ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(batteryLevel = 5)))
        assertFalse(ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(batteryLevel = 21)))
    }

    @Test
    fun `battery constraint defaults to below 20 when config missing`() {
        val constraint = Constraint(ConstraintType.BATTERY)
        assertTrue(ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(batteryLevel = 20)))
        assertFalse(ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(batteryLevel = 21)))
    }

    @Test
    fun `battery constraint fails closed on unknown level`() {
        val constraint = Constraint(ConstraintType.BATTERY, mapOf("direction" to "ABOVE", "level" to "10"))
        assertFalse(
            "unreadable battery must never satisfy",
            ConstraintEvaluator.isSatisfied(constraint, ConstraintSnapshot(batteryLevel = -1))
        )
    }

    @Test
    fun `all constraints must pass (AND semantics)`() {
        val constraints = listOf(
            Constraint(ConstraintType.WIFI),
            Constraint(ConstraintType.BATTERY, mapOf("direction" to "ABOVE", "level" to "30"))
        )
        assertTrue(
            ConstraintEvaluator.allSatisfied(
                constraints,
                ConstraintSnapshot(wifiConnected = true, batteryLevel = 60)
            )
        )
        assertFalse(
            "one failing constraint must block the run",
            ConstraintEvaluator.allSatisfied(
                constraints,
                ConstraintSnapshot(wifiConnected = true, batteryLevel = 10)
            )
        )
        assertFalse(
            ConstraintEvaluator.allSatisfied(
                constraints,
                ConstraintSnapshot(wifiConnected = false, batteryLevel = 90)
            )
        )
    }

    @Test
    fun `empty constraint list always passes`() {
        assertTrue(ConstraintEvaluator.allSatisfied(emptyList(), ConstraintSnapshot()))
    }
}
