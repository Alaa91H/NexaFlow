package com.nexaflow.core.database

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Room JSON columns were historically written by Gson. The converters now
 * use kotlinx.serialization; both stacks emit the same shape for these models
 * (field names as-is, enums by name, nulls omitted when defaulted), so rows
 * stored by older app versions keep parsing without a DB migration. This test
 * pins that contract with realistic legacy payloads.
 */
class ConvertersCompatTest {

    private val converters = Converters()

    // Gson output for a real trigger+action+constraint set (as produced by
    // Gson 2.x: HTML-escaped chars, enum names, null fields omitted).
    private val legacyTriggersJson =
        """[{"type":"TIME","config":{"hour":"8","minute":"30","days":"[1,3,5]"}},""" +
            """{"type":"BATTERY","config":{"direction":"BELOW","level":"20"}}]"""

    private val legacyActionsJson =
        """[{"type":"SYSTEM_WIFI","config":{"enabled":"true"}},""" +
            """{"type":"SYSTEM_VOLUME","config":{"stream":"music","value":"5"},""" +
            """"endBehavior":{"mode":"REVERT","config":{}}}]"""

    private val legacyConstraintsJson =
        """[{"type":"WIFI","config":{}},{"type":"BATTERY","config":{"direction":"ABOVE","level":"30"}}]"""

    @Test
    fun kotlinxParsesLegacyGsonTriggers() {
        val expected = listOf(
            Trigger(TriggerType.TIME, mapOf("hour" to "8", "minute" to "30", "days" to "[1,3,5]")),
            Trigger(TriggerType.BATTERY, mapOf("direction" to "BELOW", "level" to "20"))
        )
        assertEquals(expected, converters.toTriggerList(legacyTriggersJson))
    }

    @Test
    fun kotlinxParsesLegacyGsonActions() {
        val expected = listOf(
            Action(ActionType.SYSTEM_WIFI, mapOf("enabled" to "true")),
            Action(
                ActionType.SYSTEM_VOLUME,
                mapOf("stream" to "music", "value" to "5"),
                EndBehavior(EndMode.REVERT, emptyMap())
            )
        )
        assertEquals(expected, converters.toActionList(legacyActionsJson))
    }

    @Test
    fun kotlinxParsesLegacyGsonConstraints() {
        val expected = listOf(
            Constraint(ConstraintType.WIFI, emptyMap()),
            Constraint(ConstraintType.BATTERY, mapOf("direction" to "ABOVE", "level" to "30"))
        )
        assertEquals(expected, converters.toConstraintList(legacyConstraintsJson))
    }

    @Test
    fun gsonCanStillParseKotlinxOutput() {
        // Reverse direction: rows written by the new converters must remain
        // readable by the old Gson stack (e.g. for older app versions).
        val gson = Gson()
        val triggers = listOf(
            Trigger(TriggerType.SENSOR, mapOf("sensor" to "SHAKE", "sensitivity" to "14")),
            Trigger(TriggerType.WEBHOOK, mapOf("path" to "/hook", "method" to "POST"))
        )
        val type = object : TypeToken<List<Trigger>>() {}.type
        val json = converters.fromTriggerList(triggers)
        assertEquals(triggers, gson.fromJson<List<Trigger>>(json, type))
    }
}
