package com.nexaflow.feature.settings

import com.nexaflow.core.airuntime.AiToolDefinition
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedAgentPolicyDefaultsTest {
    @Test
    fun selectedWriteAndUnknownToolsRequireApprovalByDefaultButReadToolsDoNot() {
        val read = AiToolDefinition("read", "Read", buildJsonObject {}, readOnly = true)
        val write = AiToolDefinition("write", "Write", buildJsonObject {})
        val unknown = AiToolDefinition("unknown", "Unknown", buildJsonObject {})

        val draft = ManagedAgentDraft()
            .toggleTool(read)
            .toggleTool(write)
            .toggleTool(unknown)

        assertEquals(setOf("read", "write", "unknown"), draft.allowedToolNames)
        assertEquals(setOf("write", "unknown"), draft.approvalRequiredToolNames)
        assertTrue(draft.approvalOptionalToolNames.isEmpty())
    }

    @Test
    fun optingOutOfWriteApprovalIsExplicitAndSurvivesDraftEdits() {
        val write = AiToolDefinition("write", "Write", buildJsonObject {})
        val selected = ManagedAgentDraft().toggleTool(write)
        val optedOut = selected.toggleApprovalRequirement("write")
        val removed = optedOut.toggleTool(write)
        val roundTrip = removed.toggleTool(write)

        assertEquals(setOf("write"), removed.approvalOptionalToolNames)
        assertTrue("write" !in optedOut.approvalRequiredToolNames)
        assertEquals(setOf("write"), roundTrip.approvalOptionalToolNames)
    }
}
