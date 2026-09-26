package com.nexaflow.core.automationcontrol

import com.nexaflow.core.automationcontrol.api.AgentActionDraftV1
import com.nexaflow.core.automationcontrol.api.AgentTaskDraftV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AutomationMutationFingerprintTest {

    @Test
    fun mapInsertionOrderDoesNotChangeFingerprint() {
        val first = AgentTaskDraftV1(
            name = "Task",
            actions = listOf(
                AgentActionDraftV1(
                    type = "TOAST",
                    config = linkedMapOf("message" to "hello", "duration" to "short")
                )
            )
        )
        val second = first.copy(
            actions = listOf(
                AgentActionDraftV1(
                    type = "TOAST",
                    config = linkedMapOf("duration" to "short", "message" to "hello")
                )
            )
        )

        assertEquals(
            AutomationMutationFingerprint.draft(
                AutomationMutationKind.CREATE,
                null,
                first
            ),
            AutomationMutationFingerprint.draft(
                AutomationMutationKind.CREATE,
                null,
                second
            )
        )
    }

    @Test
    fun operationAndPayloadArePartOfFingerprint() {
        val first = AgentTaskDraftV1(name = "Task")
        val changed = first.copy(name = "Different")

        assertNotEquals(
            AutomationMutationFingerprint.draft(
                AutomationMutationKind.CREATE,
                null,
                first
            ),
            AutomationMutationFingerprint.draft(
                AutomationMutationKind.UPDATE,
                "id",
                first
            )
        )
        assertNotEquals(
            AutomationMutationFingerprint.draft(
                AutomationMutationKind.CREATE,
                null,
                first
            ),
            AutomationMutationFingerprint.draft(
                AutomationMutationKind.CREATE,
                null,
                changed
            )
        )
    }
}
