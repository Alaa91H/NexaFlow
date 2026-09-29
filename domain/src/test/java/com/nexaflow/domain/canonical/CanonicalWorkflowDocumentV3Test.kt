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
