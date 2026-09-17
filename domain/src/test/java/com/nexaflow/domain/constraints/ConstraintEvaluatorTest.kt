package com.nexaflow.domain.constraints

import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintSnapshot
import com.nexaflow.domain.models.ConstraintType
import java.time.LocalDate
import java.time.LocalTime
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
    fun `schedule passes on selected weekday inside window`() {
        val constraint = Constraint(
            ConstraintType.SCHEDULE,
            mapOf("days" to "3", "start" to "09:00", "end" to "17:00")
        )
        // 2026-09-09 is a Wednesday (ISO day 3).
        assertTrue(
            ConstraintEvaluator.scheduleSatisfied(
                constraint.config,
                nowTime = LocalTime.of(12, 0),
                today = LocalDate.of(2026, 9, 9)
            )
        )
    }

    @Test
    fun `schedule fails on unselected weekday`() {
        val constraint = Constraint(
            ConstraintType.SCHEDULE,
            mapOf("days" to "6,7", "start" to "00:00", "end" to "23:59")
        )
        // 2026-09-09 is a Wednesday (ISO day 3), not in {6,7}.
        assertFalse(
            ConstraintEvaluator.scheduleSatisfied(
                constraint.config,
                nowTime = LocalTime.of(12, 0),
                today = LocalDate.of(2026, 9, 9)
            )
        )
    }

    @Test
    fun `schedule passes every day when day list is empty`() {
        val constraint = Constraint(
            ConstraintType.SCHEDULE,
            mapOf("days" to "", "start" to "00:00", "end" to "23:59")
        )
        assertTrue(
            ConstraintEvaluator.scheduleSatisfied(
                constraint.config,
                nowTime = LocalTime.of(3, 0),
                today = LocalDate.of(2026, 9, 9)
            )
        )
    }

    @Test
    fun `schedule window is start-inclusive end-exclusive`() {
        val constraint = Constraint(
            ConstraintType.SCHEDULE,
            mapOf("days" to "", "start" to "09:00", "end" to "17:00")
        )
        assertTrue(
            ConstraintEvaluator.scheduleSatisfied(
                constraint.config, nowTime = LocalTime.of(9, 0), today = LocalDate.of(2026, 9, 9)
            )
        )
        assertFalse(
            ConstraintEvaluator.scheduleSatisfied(
                constraint.config, nowTime = LocalTime.of(17, 0), today = LocalDate.of(2026, 9, 9)
            )
        )
    }

    @Test
    fun `overnight schedule window spans midnight`() {
        val constraint = Constraint(
            ConstraintType.SCHEDULE,
            mapOf("days" to "", "start" to "22:00", "end" to "06:00")
        )
        assertTrue(
            "23:00 is inside the overnight window",
            ConstraintEvaluator.scheduleSatisfied(
                constraint.config, nowTime = LocalTime.of(23, 0), today = LocalDate.of(2026, 9, 9)
            )
        )
        assertTrue(
            "05:59 is inside the overnight window",
            ConstraintEvaluator.scheduleSatisfied(
                constraint.config, nowTime = LocalTime.of(5, 59), today = LocalDate.of(2026, 9, 9)
            )
        )
        assertFalse(
            "12:00 is outside the overnight window",
            ConstraintEvaluator.scheduleSatisfied(
                constraint.config, nowTime = LocalTime.of(12, 0), today = LocalDate.of(2026, 9, 9)
            )
        )
    }

    @Test
    fun `corrupt schedule config fails closed`() {
        assertFalse(
            ConstraintEvaluator.scheduleSatisfied(
                mapOf("days" to "", "start" to "not-a-time", "end" to "17:00"),
                nowTime = LocalTime.of(12, 0),
                today = LocalDate.of(2026, 9, 9)
            )
        )
        assertFalse(
            ConstraintEvaluator.scheduleSatisfied(
                emptyMap(),
                nowTime = LocalTime.of(12, 0),
                today = LocalDate.of(2026, 9, 9)
            )
        )
    }

    @Test
    fun `out-of-range day numbers are ignored`() {
        val constraint = Constraint(
            ConstraintType.SCHEDULE,
            mapOf("days" to "0,8,3", "start" to "00:00", "end" to "23:59")
        )
        assertTrue(
            "only valid day 3 applies",
            ConstraintEvaluator.scheduleSatisfied(
                constraint.config,
                nowTime = LocalTime.of(12, 0),
                today = LocalDate.of(2026, 9, 9)
            )
        )
    }
}
