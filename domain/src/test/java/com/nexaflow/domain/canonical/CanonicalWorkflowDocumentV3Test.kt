package com.nexaflow.domain.canonical

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
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
