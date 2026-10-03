package com.nexaflow.feature.settings

import com.nexaflow.core.airuntime.AiToolDefinition
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
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
    }
}
