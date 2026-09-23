package com.nexaflow.feature.dashboard

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineCardDetailsTest {


    private fun readinessAutomation(
        enabled: Boolean = true,
        triggerMatch: TriggerMatchMode = TriggerMatchMode.ALL,
        triggers: List<Trigger> = listOf(
            Trigger(TriggerType.DARK_MODE, mapOf("state" to "ON"))
        ),
        constraints: List<Constraint> = emptyList(),
        actions: List<Action> = emptyList(),
        revertOnExit: Boolean = false
    ) = Automation(
        id = "readiness",
        name = "Readiness",
        description = "",
        icon = "bolt",
        iconColor = 0,
        backgroundColor = 0,
        category = "custom",
        priority = 0,
        enabled = enabled,
        triggers = triggers,
        actions = actions,
        triggerMatch = triggerMatch,
        constraints = constraints,
        revertOnExit = revertOnExit,
        createdAt = 0,
        updatedAt = 0
    )

    @Test
    fun readinessIsReadyWhenAllReadableGatesPass() {
        val automation = readinessAutomation(
            constraints = listOf(Constraint(ConstraintType.WIFI))
        )

        val state = routineReadinessState(
            automation,
            RoutineConditionSnapshot(
                triggerStates = listOf(ConditionResult.Satisfied),
                constraintStates = listOf(true)
            )
        )

        assertEquals(RoutineReadinessState.READY, state)
    }

    @Test
    fun readinessWaitsWhenAnyRequiredGateIsFalse() {
        val automation = readinessAutomation(
            constraints = listOf(Constraint(ConstraintType.WIFI))
        )

        val triggerBlocked = routineReadinessState(
            automation,
            RoutineConditionSnapshot(
                triggerStates = listOf(ConditionResult.Unsatisfied),
                constraintStates = listOf(true)
            )
        )
        val constraintBlocked = routineReadinessState(
            automation,
            RoutineConditionSnapshot(
                triggerStates = listOf(ConditionResult.Satisfied),
                constraintStates = listOf(false)
            )
        )

        assertEquals(RoutineReadinessState.WAITING, triggerBlocked)
        assertEquals(RoutineReadinessState.WAITING, constraintBlocked)
    }

    @Test
    fun anyTriggerModeIsReadyWhenOneTriggerMatches() {
        val automation = readinessAutomation(
            triggerMatch = TriggerMatchMode.ANY,
            triggers = listOf(
                Trigger(TriggerType.DARK_MODE, mapOf("state" to "ON")),
                Trigger(TriggerType.NFC_TAG_SCANNED, emptyMap())
            )
        )

        val state = routineReadinessState(
            automation,
            RoutineConditionSnapshot(
                triggerStates = listOf(
                    ConditionResult.Satisfied,
                    ConditionResult.Unknown
                ),
                constraintStates = emptyList()
            )
        )

        assertEquals(RoutineReadinessState.READY, state)
    }

    @Test
    fun eventOnlyOrUnreadableGateIsUnknownInsteadOfFailed() {
        val automation = readinessAutomation(
            triggers = listOf(Trigger(TriggerType.NFC_TAG_SCANNED, emptyMap()))
        )

        val state = routineReadinessState(
            automation,
            RoutineConditionSnapshot(
                triggerStates = listOf(ConditionResult.Unknown),
                constraintStates = emptyList()
            )
        )

        assertEquals(RoutineReadinessState.UNKNOWN, state)
    }

    @Test
    fun disabledTaskReportsDisabledEvenWhenConditionsMatch() {
        val automation = readinessAutomation(enabled = false)

        val state = routineReadinessState(
            automation,
            RoutineConditionSnapshot(
                triggerStates = listOf(ConditionResult.Satisfied),
                constraintStates = emptyList()
            )
        )

        assertEquals(RoutineReadinessState.DISABLED, state)
    }

    @Test
    fun oneTimeScheduleWithEndBehaviorReportsConfigurationError() {
        val automation = readinessAutomation(
            triggers = listOf(
                Trigger(
                    TriggerType.TIME,
                    mapOf("timeMode" to "POINT", "time" to "12:00")
                )
            ),
            actions = listOf(
                Action(ActionType.SYSTEM_SCREEN_ROTATION, mapOf("autoRotate" to "true"))
            ),
            revertOnExit = true
        )

        val state = routineReadinessState(
            automation,
            RoutineConditionSnapshot(
                triggerStates = listOf(ConditionResult.Satisfied),
                constraintStates = emptyList()
            )
        )

        assertEquals(RoutineReadinessState.CONFIGURATION_ERROR, state)
    }

    @Test
    fun configFactsHideCredentialsAndOpaquePayloads() {
        val facts = routineConfigFacts(
            mapOf(
                "state" to "ON",
                "ssid" to "Home",
                "password" to "super-secret",
                "token" to "abc123",
                "headers" to "Authorization: Bearer secret",
                "bundleJson" to "{sensitive:true}"
            )
        )

        assertEquals(listOf("state", "ssid"), facts.map { it.key })
        assertFalse(facts.any { it.value.contains("secret", ignoreCase = true) })
    }

    @Test
    fun configFactsStayCompactAndUseStablePriority() {
        val facts = routineConfigFacts(
            mapOf(
                "packages" to "a,b",
                "threshold" to "75",
                "mode" to "AUTO",
                "event" to "CONNECTED",
                "state" to "ON",
                "seconds" to "30"
            )
        )

        assertEquals(4, facts.size)
        assertEquals(listOf("state", "event", "mode", "threshold"), facts.map { it.key })
    }


    @Test
    fun screenRotationTargetIsIncludedInCardFacts() {
        val facts = routineConfigFacts(mapOf("autoRotate" to "true"))

        assertEquals(listOf("autoRotate"), facts.map { it.key })
        assertEquals("true", facts.single().value)
    }

    @Test
    fun actionResultsAreMappedByExecutionOrderEvenForDuplicateTypes() {
        val first = ActionExecutionResult("SYSTEM_WIFI", true, "first", 10)
        val second = ActionExecutionResult("SYSTEM_WIFI", false, "second", 20)
        val record = ExecutionRecord(
            id = "run",
            automationId = "task",
            automationName = "Task",
            success = false,
            message = "partial",
            executedAt = 1L,
            actionResults = listOf(first, second)
        )

        assertEquals(first, record.actionResultAt(0, "SYSTEM_WIFI"))
        assertEquals(second, record.actionResultAt(1, "SYSTEM_WIFI"))
        assertNull(record.actionResultAt(2, "SYSTEM_WIFI"))
        assertNull(record.actionResultAt(0, "SYSTEM_BLUETOOTH"))
        assertNull((null as ExecutionRecord?).actionResultAt(0, "SYSTEM_WIFI"))
    }
}
