package com.nexaflow.domain.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTimeEndBehaviorPolicyTest {

    @Test
    fun `single time with an explicit exit action requires a time range`() {
        val automation = automation(
            timeConfig = mapOf("time" to "22:00", "timeMode" to "SINGLE"),
            exitActions = listOf(Action(ActionType.SYSTEM_RINGER_MODE, mapOf("mode" to "NORMAL")))
        )

        assertTrue(automation.hasExecutableEndBehavior)
        assertTrue(automation.requiresTimeRangeForEndBehavior)
    }

    @Test
    fun `single time with a per action end value requires a time range`() {
        val automation = automation(
            timeConfig = mapOf("time" to "22:00"),
            actionEndBehavior = EndBehavior(EndMode.SET_VALUE, mapOf("mode" to "NORMAL"))
        )

        assertTrue(automation.hasExecutableEndBehavior)
        assertTrue(automation.requiresTimeRangeForEndBehavior)
    }

    @Test
    fun `single time with leave as is has no end lifecycle requirement`() {
        val automation = automation(
            timeConfig = mapOf("time" to "22:00"),
            actionEndBehavior = EndBehavior(EndMode.LEAVE)
        )

        assertFalse(automation.hasExecutableEndBehavior)
        assertFalse(automation.requiresTimeRangeForEndBehavior)
    }

    @Test
    fun `time range supports an executable end behavior including overnight ranges`() {
        val automation = automation(
            timeConfig = mapOf(
                "timeMode" to "RANGE",
                "rangeStart" to "22:00",
                "rangeEnd" to "06:00"
            ),
            actionEndBehavior = EndBehavior(EndMode.SET_VALUE, mapOf("mode" to "NORMAL"))
        )

        assertTrue(automation.hasExecutableEndBehavior)
        assertFalse(automation.requiresTimeRangeForEndBehavior)
    }

    @Test
    fun `event trigger with an end behavior does not require a time range`() {
        val automation = Automation(
            id = "event-with-end",
            name = "Event with end",
            description = "",
            icon = "bolt",
            iconColor = 0L,
            backgroundColor = 0L,
            category = "test",
            priority = 0,
            enabled = true,
            triggers = listOf(Trigger(TriggerType.SMS, emptyMap())),
            actions = listOf(
                Action(
                    ActionType.SYSTEM_RINGER_MODE,
                    mapOf("mode" to "SILENT"),
                    EndBehavior(EndMode.SET_VALUE, mapOf("mode" to "NORMAL"))
                )
            ),
            createdAt = 0L,
            updatedAt = 0L
        )

        assertTrue(automation.hasExecutableEndBehavior)
        assertFalse(automation.requiresTimeRangeForEndBehavior)
    }

    private fun automation(
        timeConfig: Map<String, String>,
        exitActions: List<Action> = emptyList(),
        actionEndBehavior: EndBehavior? = null
    ): Automation = Automation(
        id = "time-policy",
        name = "Time policy",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "test",
        priority = 0,
        enabled = true,
        triggers = listOf(Trigger(TriggerType.TIME, timeConfig)),
        actions = listOf(
            Action(
                type = ActionType.SYSTEM_RINGER_MODE,
                config = mapOf("mode" to "SILENT"),
                endBehavior = actionEndBehavior
            )
        ),
        exitActions = exitActions,
        createdAt = 0L,
        updatedAt = 0L
    )
}
