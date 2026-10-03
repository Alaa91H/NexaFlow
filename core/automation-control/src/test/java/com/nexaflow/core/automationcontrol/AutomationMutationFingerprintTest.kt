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

    @Test
    fun canonicalNodeConfigurationIsPartOfMutationFingerprint() {
        val field = com.nexaflow.domain.canonical.CanonicalFieldId("duration_ms")
        val schema = com.nexaflow.domain.canonical.CanonicalNativeNodeSchemaRegistry.delay
        fun nativeNode(durationMs: Long) = run {
            val duration = com.nexaflow.domain.canonical.DurationValue(durationMs)
            com.nexaflow.domain.canonical.CanonicalWorkflowNode(
                kind = com.nexaflow.domain.canonical.NodeSchemaKind.ACTION,
                definitionId = schema.schemaId,
                schema = schema,
                node = com.nexaflow.domain.canonical.WaitNode(
                    com.nexaflow.domain.canonical.CanonicalNodeId("native.action.fingerprint"), duration,
                ),
                arguments = listOf(com.nexaflow.domain.canonical.NodeFieldValue(field, duration)),
            )
        }
        val first = AgentTaskDraftV1(name = "Task", canonicalNodes = listOf(nativeNode(1_000L)))
        val changed = first.copy(canonicalNodes = listOf(nativeNode(2_000L)))
        assertNotEquals(
            AutomationMutationFingerprint.draft(AutomationMutationKind.CREATE, null, first),
            AutomationMutationFingerprint.draft(AutomationMutationKind.CREATE, null, changed),
        )
    }
}
