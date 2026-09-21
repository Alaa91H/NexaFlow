package com.nexaflow.core.execution

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerMatchMode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compatibility contract for the trigger combine rule: the field must default
 * to ANY (so every pre-existing automation keeps its exact historical
 * meaning), serialize by name, and decode old JSON that omits the field.
 */
class TriggerMatchSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun defaultIsAnySoOldAutomationsKeepTheirMeaning() {
        val automation = AutomationFixtures.simpleAutomation()
        assertEquals(TriggerMatchMode.ANY, automation.triggerMatch)
    }

    @Test
    fun jsonWithoutTriggerMatchFieldDecodesAsAny() {
        // A payload serialized by any earlier app version has no triggerMatch
        // key; it must decode to ANY, never null and never ALL.
        val legacy = """
            {"id":"a1","name":"Old task","description":"","icon":"wifi","iconColor":4284007888,
             "backgroundColor":4286068410,"category":"custom","priority":1,"enabled":true,
             "triggers":[{"type":"BATTERY","config":{}}],
             "actions":[{"type":"SYSTEM_WIFI","config":{}}],
             "cooldownSeconds":10,"createdAt":1,"updatedAt":2,"workflowVersion":1}
        """.trimIndent()
        val decoded = json.decodeFromString(Automation.serializer(), legacy)
        assertEquals(TriggerMatchMode.ANY, decoded.triggerMatch)
    }

    @Test
    fun allModeSurvivesARoundTrip() {
        val automation = AutomationFixtures.simpleAutomation()
            .copy(triggerMatch = TriggerMatchMode.ALL)
        val encoded = json.encodeToString(Automation.serializer(), automation)
        assertTrue(encoded.contains("\"triggerMatch\":\"ALL\""))
        val decoded = json.decodeFromString(Automation.serializer(), encoded)
        assertEquals(TriggerMatchMode.ALL, decoded.triggerMatch)
    }
}

/** Minimal valid fixtures shared by engine-gate tests. */
internal object AutomationFixtures {
    fun simpleAutomation(): Automation = Automation(
        id = "a1",
        name = "Task",
        description = "",
        icon = "wifi",
        iconColor = 0xFF0B57D0,
        backgroundColor = 0xFFE3EEFA,
        category = "custom",
        priority = 1,
        enabled = true,
        triggers = listOf(
            com.nexaflow.domain.models.Trigger(
                com.nexaflow.domain.models.TriggerType.BATTERY,
                mapOf("direction" to "ABOVE", "above" to "10")
            )
        ),
        actions = listOf(
            com.nexaflow.domain.models.Action(
                com.nexaflow.domain.models.ActionType.SYSTEM_WIFI,
                mapOf("enabled" to "true")
            )
        ),
        cooldownSeconds = 0,
        createdAt = 1L,
        updatedAt = 2L
    )
}
