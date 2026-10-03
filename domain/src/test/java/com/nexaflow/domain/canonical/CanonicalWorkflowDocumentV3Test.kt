package com.nexaflow.domain.canonical

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.models.ConditionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalWorkflowDocumentV3Test {

    @Test
    fun v3UsesCatalogTypedDefaultsLikeTheRuntime() {
        val document = CanonicalWorkflowV3Codec.documentFor(
            automation(
                Action(ActionType.SYSTEM_RINGER_MODE, emptyMap()),
            ),
        )
        val node = document.actions.single().node as SetValueNode
        assertEquals(
            EnumTokenValue("compat.system_ringer_mode.mode", "NORMAL"),
            node.value,
        )
        assertEquals(
            EnumTokenValue("compat.system_ringer_mode.mode", "NORMAL"),
            node.arguments[CanonicalFieldId("mode")],
        )
        assertFalse(document.requiresLegacyFallback)
        assertEquals(emptyList<String>(), document.actions.single().suppliedConfigKeys)
        assertEquals("core.audio.ringer_mode", document.actions.single().targetId)
        assertEquals("core.operation.set_value", document.actions.single().semanticId)
    }

    @Test
    fun v3IdentityFieldsRemainOptionalWhenReadingExistingDocuments() {
        val legacyV3 = """{"schemaVersion":3,"workflowId":"old","conditionLogic":"ANY","triggers":[],"actions":[{"sourceType":"SYSTEM_BRIGHTNESS","node":{"canonicalType":"set_value","id":"v3.action.0","target":"core.display.brightness","value":{"canonicalType":"integer","value":40,"kind":"INTEGER"},"arguments":{"entries":[]},"primitive":"SET_VALUE"}}],"exitActions":[],"requiresLegacyFallback":false}"""

        val decoded = CanonicalWorkflowV3Codec.decode(legacyV3)

        assertEquals(null, decoded.actions.single().targetId)
        assertEquals(null, decoded.actions.single().semanticId)
    }

    @Test
    fun rawWifiPasswordNeverEntersCanonicalJsonAndFallbackIsExplicit() {
        val rawSecret = "sup3r-secret-never-serialize"
        val workflow = automation(
            Action(
                ActionType.SYSTEM_WIFI_CONNECT,
                mapOf(
                    "ssid" to "HomeNet",
                    "password" to rawSecret,
                ),
            ),
        )

        val document = CanonicalWorkflowV3Codec.documentFor(workflow)
        val persisted = document.actions.single()
        val json = CanonicalWorkflowV3Codec.encode(workflow)

        assertTrue(document.requiresLegacyFallback)
        assertTrue(persisted.legacyFallbackRequired)
        assertFalse(json.contains(rawSecret))
        assertFalse(persisted.preservedConfig.any { it.key == "password" })
        assertEquals(listOf("password", "ssid"), persisted.suppliedConfigKeys)
        val node = persisted.node as InvokeNode
        assertTrue(node.arguments[CanonicalFieldId("password")] is SecretReferenceValue)
    }

    @Test
    fun validatedBrightnessBoundsApplyToV3WritesToo() {
        try {
            CanonicalWorkflowV3Codec.documentFor(
                automation(
                    Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("value" to "999")),
                ),
            )
            throw AssertionError("Expected invalid brightness to fail V3 preparation")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("cutover refused"))
        }
    }

    @Test
    fun untrustedCanonicalSchemaCannotPersistRawText() {
        val target = TargetId("core.native.sample")
        val operation = OperationId("core.native.sample.run")
        val field = CanonicalFieldId("value")
        val schema = NodeSchema(
            schemaId = "core.native.sample.run",
            kind = NodeSchemaKind.ACTION,
            target = target,
            operation = operation,
            title = "Sample",
            fields = listOf(NodeSchemaField(field, NodeFieldType.TEXT, alwaysRequired = true)),
            summaryTemplate = "{value}",
        )
        val node = CanonicalWorkflowNode(
            kind = NodeSchemaKind.ACTION,
            definitionId = schema.schemaId,
            schema = schema,
            node = InvokeNode(
                CanonicalNodeId("native.action.sample"), target, operation,
                CanonicalArguments(listOf(CanonicalArgument(field, TextValue("typed")))),
            ),
            arguments = listOf(NodeFieldValue(field, TextValue("typed"))),
            sequenceIndex = 3,
        )
        val rawSecret = "agent-supplied-secret-must-not-persist"
        val automation = automation(Action(ActionType.SYSTEM_RINGER_MODE, emptyMap())).copy(
            id = "native-roundtrip",
            canonicalNodes = listOf(node),
        )
        val hostile = automation.copy(canonicalNodes = listOf(node.copy(
            node = InvokeNode(
                CanonicalNodeId("native.action.sample"), target, operation,
                CanonicalArguments(listOf(CanonicalArgument(field, TextValue(rawSecret)))),
            ),
            arguments = listOf(NodeFieldValue(field, TextValue(rawSecret))),
        )))

        val prepared = CanonicalWorkflowV3Codec.prepareWrite(hostile)
        assertEquals(CanonicalV3WriteState.LEGACY_ONLY_DEGRADED, prepared.state)
        assertEquals(null, prepared.payload)
        assertFalse(prepared.payload.orEmpty().contains(rawSecret))

        val forgedDocument = CanonicalWorkflowDocumentV3(
            workflowId = "forged-native-node",
            conditionLogic = ConditionLogic.ANY,
            triggers = emptyList(),
            actions = emptyList(),
            exitActions = emptyList(),
            canonicalNodes = listOf(hostile.canonicalNodes.single()),
        )
        val forgedJson = CanonicalWorkflowV3Codec.json.encodeToString(
            CanonicalWorkflowDocumentV3.serializer(), forgedDocument,
        )
        assertTrue(runCatching { CanonicalWorkflowV3Codec.decode(forgedJson) }.isFailure)

        val trustedDelay = CanonicalWorkflowNode(
            kind = NodeSchemaKind.ACTION,
            definitionId = CanonicalNativeNodeSchemaRegistry.DELAY_ID,
            schema = CanonicalNativeNodeSchemaRegistry.delay,
            node = WaitNode(CanonicalNodeId("native.action.end-behavior"), DurationValue(1_000L)),
            arguments = listOf(NodeFieldValue(CanonicalFieldId("duration_ms"), DurationValue(1_000L))),
            endBehavior = CanonicalEndBehaviorV3(
                mode = "unknown",
                config = listOf(CanonicalLegacyEntryV3("value", rawSecret)),
            ),
        )
        val endBehaviorWrite = CanonicalWorkflowV3Codec.prepareWrite(
            automation.copy(canonicalNodes = listOf(trustedDelay)),
        )
        assertEquals(CanonicalV3WriteState.LEGACY_ONLY_DEGRADED, endBehaviorWrite.state)
        assertEquals(null, endBehaviorWrite.payload)
    }

    @Test
    fun canonicalDelayWaitNodeRoundTripsTypedDurationAndOrder() {
        val duration = DurationValue(42_000L)
        val field = CanonicalFieldId("duration_ms")
        val schema = CanonicalNativeNodeSchemaRegistry.delay
        val wait = CanonicalWorkflowNode(
            kind = NodeSchemaKind.ACTION,
            definitionId = schema.schemaId,
            schema = schema,
            node = WaitNode(CanonicalNodeId("native.action.delay.test"), duration),
            arguments = listOf(NodeFieldValue(field, duration)),
            sequenceIndex = 2,
        )
        val encoded = CanonicalWorkflowV3Codec.encode(
            automation(Action(ActionType.SYSTEM_RINGER_MODE, emptyMap())).copy(
                id = "canonical-delay-persist",
                canonicalNodes = listOf(wait),
            ),
        )
        val decoded = CanonicalWorkflowV3Codec.decode(encoded)
        val restored = decoded.canonicalNodes.single()
        assertEquals(2, restored.sequenceIndex)
        assertEquals(wait, restored)
        assertEquals(DurationValue(42_000L), (restored.node as WaitNode).duration)
    }

    @Test
    fun canonicalTriggerEvaluationFailsClosedForMissingCapabilityAndDedupesEvents() = kotlinx.coroutines.runBlocking {
        val target = TargetId("core.events.sample")
        val predicate = PredicateId("core.events.sample.arrived")
        val schema = NodeSchema(
            schemaId = "core.events.sample.arrived",
            kind = NodeSchemaKind.TRIGGER,
            target = target,
            predicate = predicate,
            title = "Sample event",
            fields = emptyList(),
            summaryTemplate = "Sample event",
        )
        val node = CanonicalWorkflowNode(
            kind = NodeSchemaKind.TRIGGER,
            definitionId = schema.schemaId,
            schema = schema,
            node = ObserveNode(CanonicalNodeId("native.trigger.sample"), target, predicate),
            arguments = emptyList(),
        )
        val evaluator = CanonicalTriggerEvaluator(CanonicalTriggerSourceRegistry(listOf(
            CanonicalTriggerSourceContract(
                definitionId = node.definitionId,
                schema = schema,
                sourceKind = CanonicalTriggerSourceKind.EVENT,
                capabilityRequirement = CapabilityRequirement.Capability(
                    com.nexaflow.domain.capability.CapabilityId.DEVICE_STATE_READ,
                ),
            ),
        )), nowMs = { 100_000L })
        val providers = mapOf(node.definitionId to suspend { _: CanonicalWorkflowNode -> ConditionResult.Satisfied })
        assertEquals(
            ConditionResult.Unavailable,
            evaluator.evaluate(
                listOf(node),
                ConditionLogic.ANY,
                "event-1",
                providers,
                com.nexaflow.domain.capability.CapabilitySnapshot(),
            ).decision,
        )
    }

    @Test
    fun canonicalEventTriggerDeduplicatesTheSameOccurrenceWithinItsWindow() = kotlinx.coroutines.runBlocking {
        val target = TargetId("core.events.dedupe")
        val predicate = PredicateId("core.events.dedupe.arrived")
        val schema = NodeSchema(
            schemaId = "core.events.dedupe.arrived",
            kind = NodeSchemaKind.TRIGGER,
            target = target,
            predicate = predicate,
            title = "Deduped event",
            fields = emptyList(),
            summaryTemplate = "Deduped event",
        )
        val node = CanonicalWorkflowNode(
            kind = NodeSchemaKind.TRIGGER,
            definitionId = schema.schemaId,
            schema = schema,
            node = ObserveNode(CanonicalNodeId("native.trigger.dedupe"), target, predicate),
            arguments = emptyList(),
        )
        val evaluator = CanonicalTriggerEvaluator(
            CanonicalTriggerSourceRegistry(listOf(CanonicalTriggerSourceContract(
                definitionId = node.definitionId,
                schema = schema,
                sourceKind = CanonicalTriggerSourceKind.EVENT,
                capabilityRequirement = CapabilityRequirement.None,
            ))),
            nowMs = { 100_000L },
        )
        val providers = mapOf(node.definitionId to suspend { _: CanonicalWorkflowNode -> ConditionResult.Satisfied })
        assertEquals(ConditionResult.Satisfied,
            evaluator.evaluate(listOf(node), ConditionLogic.ANY, "same-event", providers).decision)
        val duplicate = evaluator.evaluate(listOf(node), ConditionLogic.ANY, "same-event", providers)
        assertEquals(ConditionResult.Unsatisfied, duplicate.decision)
        assertTrue(duplicate.duplicate)
    }


    private fun automation(action: Action): Automation = Automation(
        id = "v3-test",
        name = "V3 test",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "test",
        priority = 0,
        enabled = true,
        triggers = emptyList(),
        actions = listOf(action),
        createdAt = 1L,
        updatedAt = 1L,
    )
}
