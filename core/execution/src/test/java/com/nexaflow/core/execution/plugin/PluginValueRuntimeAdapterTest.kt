package com.nexaflow.core.execution.plugin

import com.nexaflow.core.pluginsdk.PluginValue
import com.nexaflow.domain.variables.RuntimeValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginValueRuntimeAdapterTest {

    @Test
    fun convertsPrimitiveListAndMapIntoTypedRuntimeValue() {
        val conversion = PluginValueRuntimeAdapter.toRuntime(
            PluginValue.MapValue(
                linkedMapOf(
                    "count" to PluginValue.IntegerValue(3),
                    "enabled" to PluginValue.BooleanValue(true),
                    "labels" to PluginValue.ListValue(listOf(PluginValue.StringValue("one"), PluginValue.StringValue("two")))
                )
            )
        )

        assertTrue(conversion.isSuccess)
        val objectValue = conversion.value as RuntimeValue.ObjectValue
        assertEquals(RuntimeValue.IntValue(3), objectValue.values["count"])
        assertEquals(RuntimeValue.BooleanValue(true), objectValue.values["enabled"])
        assertEquals(
            RuntimeValue.ListValue(listOf(RuntimeValue.StringValue("one"), RuntimeValue.StringValue("two"))),
            objectValue.values["labels"]
        )
    }

    @Test
    fun rejectsPayloadBeyondConfiguredDepth() {
        val conversion = PluginValueRuntimeAdapter.toRuntime(
            PluginValue.ListValue(
                listOf(PluginValue.ListValue(listOf(PluginValue.StringValue("deep"))))
            ),
            maxDepth = 2
        )

        assertFalse(conversion.isSuccess)
        assertEquals(PluginValueConversionIssue.MAX_DEPTH_EXCEEDED, conversion.issue)
    }

    @Test
    fun rejectsInvalidMapKeyBeforeRuntimeObjectConstruction() {
        val conversion = PluginValueRuntimeAdapter.toRuntime(
            PluginValue.MapValue(mapOf(" " to PluginValue.StringValue("value")))
        )

        assertFalse(conversion.isSuccess)
        assertEquals(PluginValueConversionIssue.INVALID_MAP_KEY, conversion.issue)
    }
}
