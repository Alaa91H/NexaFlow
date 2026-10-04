package com.nexaflow.feature.builder

import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Test

class WaitDurationTest {
    @Test
    fun convertsSecondsIntoHoursMinutesAndSeconds() {
        assertEquals(WaitDuration.Parts(1, 2, 3), WaitDuration.fromSeconds(3_723))
    }

    @Test
    fun clampsEachCounterAndTotalToTwentyFourHours() {
        assertEquals(WaitDuration.Parts(24, 0, 0), WaitDuration.fromSeconds(Long.MAX_VALUE))
        assertEquals(86_400L, WaitDuration.toSeconds(24, 59, 59))
        assertEquals(0L, WaitDuration.toSeconds(-1, -1, -1))
        assertEquals(3_599L, WaitDuration.toSeconds(0, 60, 59))
    }

    @Test
    fun migratesCanonicalWaitsAfterLegacyActionsInTheirPreviousOrder() {
        val brightness = actionDraft(ActionType.SYSTEM_BRIGHTNESS)
        val waitOption = actionOptions.first { it.actionType == ActionType.SYSTEM_WAIT }
        val laterDelay = com.nexaflow.core.execution.canonical.CanonicalDelayDefinition
            .node(15_000L, "delay-later", 1)
        val earlierDelay = com.nexaflow.core.execution.canonical.CanonicalDelayDefinition
            .node(5_000L, "delay-earlier", 0)
        val migrated = BuilderActionSequence.migrateCanonicalWaits(
            actions = listOf(brightness),
            canonicalActions = listOf(laterDelay, earlierDelay),
            waitOption = waitOption
        )

        assertEquals(
            listOf(ActionType.SYSTEM_BRIGHTNESS, ActionType.SYSTEM_WAIT, ActionType.SYSTEM_WAIT),
            migrated.actions.map { it.option.actionType }
        )
        assertEquals(listOf("delay-earlier", "delay-later"), migrated.actions.drop(1).map { it.id })
        assertEquals(emptyList<Any>(), migrated.remainingCanonicalActions)
        assertEquals(listOf("5", "15"), migrated.actions.drop(1).map { it.config["seconds"] })
    }

    private fun actionDraft(type: ActionType) = ActionDraft(
        option = actionOptions.first { it.actionType == type }
    )
}
