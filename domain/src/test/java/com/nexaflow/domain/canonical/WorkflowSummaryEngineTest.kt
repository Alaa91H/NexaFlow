package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowSummaryEngineTest {

    private val wifiSchema = NodeSchema(
        schemaId = "core.schema.wifi.set_state",
        kind = NodeSchemaKind.ACTION,
        target = TargetId("core.connectivity.wifi"),
        operation = OperationId("core.operation.set_state"),
        title = "Wi-Fi",
        summaryTemplate = "Wi-Fi {enabled}",
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("enabled"),
                type = NodeFieldType.BOOLEAN,
                alwaysRequired = true,
            ),
        ),
    )

    private val bluetoothSchema = NodeSchema(
        schemaId = "core.schema.bluetooth.set_state",
        kind = NodeSchemaKind.ACTION,
        target = TargetId("core.connectivity.bluetooth"),
        operation = OperationId("core.operation.set_state"),
        title = "Bluetooth",
        summaryTemplate = "Bluetooth {enabled}",
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("enabled"),
                type = NodeFieldType.BOOLEAN,
                alwaysRequired = true,
            ),
        ),
    )

    private fun triggerSchema(target: TargetId, label: String) = NodeSchema(
        schemaId = "core.schema.${target.value}.state",
        kind = NodeSchemaKind.TRIGGER,
        target = target,
        predicate = PredicateId("core.predicate.match_state"),
        title = target.value,
        summaryTemplate = "$label {enabled}",
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("enabled"),
                type = NodeFieldType.BOOLEAN,
                alwaysRequired = true,
            ),
        ),
    )

    private fun node(
        id: String,
        schema: NodeSchema,
        enabled: Boolean,
    ) = WorkflowDraftNode(
        nodeId = id,
        schema = schema,
        values = listOf(
            NodeFieldValue(CanonicalFieldId("enabled"), BooleanValue(enabled)),
        ),
    )

    @Test
    fun triggerAndActionLinesRenderThroughTheSharedFormatter() {
        val summary = WorkflowSummaryEngine.summarize(
            nodes = listOf(
                node("t1", triggerSchema(TargetId("core.connectivity.wifi_network"), "Wi-Fi network"), true),
                node("a1", wifiSchema, false),
            ),
        )

        assertEquals("Wi-Fi network On", summary.triggerLine)
        assertEquals(listOf("Wi-Fi Off"), summary.actionLines)
        assertNull(conditionLine(summary))
        assertTrue(summary.text.contains("Wi-Fi Off"))
    }

    @Test
    fun twoNetworkConditionsRenderWithTheirDeclaredLogic() {
        val summary = WorkflowSummaryEngine.summarize(
            nodes = listOf(
                node("t1", triggerSchema(TargetId("core.connectivity.wifi_network"), "Wi-Fi network"), true),
                node("t2", triggerSchema(TargetId("core.connectivity.vpn"), "VPN"), true),
                node("a1", wifiSchema, true),
            ),
            semantics = WorkflowDraftSemantics(
                conditionLogic = ConditionLogic.ALL,
                executionMode = ExecutionMode.SINGLE,
            ),
        )

        assertEquals(
            "Wi-Fi network On + VPN On • ALL",
            conditionLine(summary),
        )
        assertEquals("conditions:ALL • mode:SINGLE", summary.semanticsLine)
    }

    @Test
    fun semanticsLineListsOnlyDeclaredDimensions() {
        val summary = WorkflowSummaryEngine.summarize(
            nodes = listOf(node("a1", wifiSchema, true)),
            semantics = WorkflowDraftSemantics(failurePolicy = FailurePolicy.CONTINUE_ON_ERROR),
        )

        assertEquals("failure:CONTINUE_ON_ERROR", summary.semanticsLine)
    }

    @Test
    fun semanticsRequireAtLeastOneDeclaredDimension() {
        try {
            WorkflowDraftSemantics()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun compactPreviewAddsAnOverflowCounter() {
        val compact = WorkflowSummaryEngine.summarizeCompact(
            nodes = listOf(
                node("a1", wifiSchema, true),
                node("a2", bluetoothSchema, true),
                node("a3", wifiSchema, false),
            ),
        )

        assertEquals("Wi-Fi On → +2", compact)
    }

    @Test
    fun duplicateNodeIdsAreRejected() {
        try {
            WorkflowSummaryEngine.summarize(
                nodes = listOf(
                    node("a1", wifiSchema, true),
                    node("a1", bluetoothSchema, false),
                ),
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun renderingIsDeterministic() {
        val nodes = listOf(
            node("t1", triggerSchema(TargetId("core.connectivity.wifi_network"), "Wi-Fi network"), true),
            node("a1", wifiSchema, true),
            node("a2", bluetoothSchema, false),
        )
        val semantics = WorkflowDraftSemantics(
            eventLogic = EventLogic.ANY_OF,
            executionMode = ExecutionMode.ORDERED,
            failurePolicy = FailurePolicy.FAIL_FAST,
        )

        assertEquals(
            WorkflowSummaryEngine.summarize(nodes, semantics),
            WorkflowSummaryEngine.summarize(nodes, semantics),
        )
        assertEquals(
            WorkflowSummaryEngine.summarizeCompact(nodes, semantics),
            WorkflowSummaryEngine.summarizeCompact(nodes, semantics),
        )
    }

    private fun conditionLine(summary: WorkflowSummary): String? = summary.conditionLine

    private fun assertNull(value: String?) {
        if (value != null) throw AssertionError("Expected null but was $value")
    }
}
