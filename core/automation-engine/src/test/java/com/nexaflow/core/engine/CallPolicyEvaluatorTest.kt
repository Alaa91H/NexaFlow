package com.nexaflow.core.engine

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure decision-logic tests for the call-screening policy: number matching,
 * caller categories, schedule gating, emergency protection, and BLOCK >
 * SILENCE precedence across tasks.
 */
class CallPolicyEvaluatorTest {

    private fun callTask(
        from: String = "",
        matchMode: String = "ANY",
        category: String = "ANY",
        action: ActionType = ActionType.CALL_BLOCK,
        constraints: List<Constraint> = emptyList(),
        enabled: Boolean = true
    ): Automation = Automation(
        id = "t1",
        name = "call task",
        description = "",
        icon = "call",
        iconColor = 0,
        backgroundColor = 0,
        category = "custom",
        priority = 1,
        enabled = enabled,
        triggers = listOf(
            Trigger(TriggerType.INCOMING_CALL, mapOf("from" to from, "matchMode" to matchMode, "category" to category))
        ),
        actions = listOf(Action(action, emptyMap())),
        constraints = constraints,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `block intent rejects the call`() {
        val verdict = CallPolicyEvaluator.evaluate(
            automations = listOf(callTask(action = ActionType.CALL_BLOCK)),
            number = "+49123456",
            category = CallPolicyEvaluator.CATEGORY_UNKNOWN,
            isEmergency = false
        )
        assertEquals(CallPolicyEvaluator.Verdict.BLOCK, verdict)
    }

    @Test
    fun `silence intent silences the call`() {
        val verdict = CallPolicyEvaluator.evaluate(
            automations = listOf(callTask(action = ActionType.CALL_SILENCE)),
            number = "15550100",
            category = CallPolicyEvaluator.CATEGORY_UNKNOWN,
            isEmergency = false
        )
        assertEquals(CallPolicyEvaluator.Verdict.SILENCE, verdict)
    }

    @Test
    fun `no matching task rings normally`() {
        val verdict = CallPolicyEvaluator.evaluate(
            automations = listOf(callTask(from = "030", matchMode = "CONTAINS")),
            number = "+49177999",
            category = CallPolicyEvaluator.CATEGORY_UNKNOWN,
            isEmergency = false
        )
        assertEquals(CallPolicyEvaluator.Verdict.NONE, verdict)
    }

    @Test
    fun `emergency numbers are never screened`() {
        assertNull(
            CallPolicyEvaluator.verdictOf(
                callTask(), "112", CallPolicyEvaluator.CATEGORY_ANY, isEmergency = true
            )
        )
    }

    @Test
    fun `disabled tasks do not screen`() {
        assertNull(
            CallPolicyEvaluator.verdictOf(
                callTask(enabled = false), "123", CallPolicyEvaluator.CATEGORY_UNKNOWN, isEmergency = false
            )
        )
    }

    @Test
    fun `exact mode requires the full number`() {
        val task = callTask(from = "+49177999", matchMode = "EXACT")
        assertEquals(
            CallPolicyEvaluator.Verdict.BLOCK,
            CallPolicyEvaluator.evaluate(listOf(task), "+49177999", CallPolicyEvaluator.CATEGORY_UNKNOWN, false)
        )
        assertEquals(
            CallPolicyEvaluator.Verdict.NONE,
            CallPolicyEvaluator.evaluate(listOf(task), "+4917799911", CallPolicyEvaluator.CATEGORY_UNKNOWN, false)
        )
    }

    @Test
    fun `contains mode matches a substring of the number`() {
        val task = callTask(from = "177999", matchMode = "CONTAINS")
        assertEquals(
            CallPolicyEvaluator.Verdict.BLOCK,
            CallPolicyEvaluator.evaluate(listOf(task), "+4917799988", CallPolicyEvaluator.CATEGORY_UNKNOWN, false)
        )
    }

    @Test
    fun `category gate separates unknown private and contacts`() {
        val unknownOnly = callTask(category = "UNKNOWN")
        assertEquals(
            CallPolicyEvaluator.Verdict.BLOCK,
            CallPolicyEvaluator.evaluate(listOf(unknownOnly), "123456", CallPolicyEvaluator.CATEGORY_UNKNOWN, false)
        )
        assertEquals(
            CallPolicyEvaluator.Verdict.NONE,
            CallPolicyEvaluator.evaluate(listOf(unknownOnly), "123456", CallPolicyEvaluator.CATEGORY_CONTACT, false)
        )
        assertEquals(
            CallPolicyEvaluator.Verdict.NONE,
            CallPolicyEvaluator.evaluate(listOf(unknownOnly), "", CallPolicyEvaluator.CATEGORY_PRIVATE, false)
        )
    }

    @Test
    fun `schedule gate confines the rule to its window`() {
        val task = callTask(
            constraints = listOf(
                Constraint(ConstraintType.SCHEDULE, mapOf("days" to "", "start" to "22:00", "end" to "06:00"))
            )
        )
        // 23:00 on a Wednesday: inside the overnight window.
        assertEquals(
            CallPolicyEvaluator.Verdict.BLOCK,
            CallPolicyEvaluator.evaluate(
                listOf(task), "123456", CallPolicyEvaluator.CATEGORY_UNKNOWN, false,
                nowTime = LocalTime.of(23, 0), today = LocalDate.of(2026, 9, 9)
            )
        )
        // 12:00: outside the window, the rule is inactive.
        assertEquals(
            CallPolicyEvaluator.Verdict.NONE,
            CallPolicyEvaluator.evaluate(
                listOf(task), "123456", CallPolicyEvaluator.CATEGORY_UNKNOWN, false,
                nowTime = LocalTime.of(12, 0), today = LocalDate.of(2026, 9, 9)
            )
        )
    }

    @Test
    fun `day filter confines the rule to selected weekdays`() {
        val task = callTask(
            constraints = listOf(
                Constraint(ConstraintType.SCHEDULE, mapOf("days" to "6,7", "start" to "00:00", "end" to "23:59"))
            )
        )
        // 2026-09-05 is a Saturday (ISO day 6).
        assertEquals(
            CallPolicyEvaluator.Verdict.BLOCK,
            CallPolicyEvaluator.evaluate(
                listOf(task), "123456", CallPolicyEvaluator.CATEGORY_UNKNOWN, false,
                nowTime = LocalTime.of(10, 0), today = LocalDate.of(2026, 9, 5)
            )
        )
        // 2026-09-09 is a Wednesday (ISO day 3).
        assertEquals(
            CallPolicyEvaluator.Verdict.NONE,
            CallPolicyEvaluator.evaluate(
                listOf(task), "123456", CallPolicyEvaluator.CATEGORY_UNKNOWN, false,
                nowTime = LocalTime.of(10, 0), today = LocalDate.of(2026, 9, 9)
            )
        )
    }

    @Test
    fun `block wins over silence when both rules match`() {
        val verdict = CallPolicyEvaluator.evaluate(
            automations = listOf(
                callTask(action = ActionType.CALL_SILENCE, category = "UNKNOWN"),
                callTask(action = ActionType.CALL_BLOCK, category = "ANY")
            ),
            number = "123456",
            category = CallPolicyEvaluator.CATEGORY_UNKNOWN,
            isEmergency = false
        )
        assertEquals(CallPolicyEvaluator.Verdict.BLOCK, verdict)
    }

    @Test
    fun `observer tasks match but produce no verdict`() {
        val observer = callTask(action = ActionType.SYSTEM_SEND_NOTIFICATION)
        assertNull(
            CallPolicyEvaluator.verdictOf(observer, "123456", CallPolicyEvaluator.CATEGORY_UNKNOWN, false)
        )
    }

    @Test
    fun `tasks without the trigger are ignored`() {
        val unrelated = callTask().copy(
            triggers = listOf(Trigger(TriggerType.SMS, mapOf("from" to "")))
        )
        assertNull(CallPolicyEvaluator.verdictOf(unrelated, "123456", CallPolicyEvaluator.CATEGORY_UNKNOWN, false))
    }
}
