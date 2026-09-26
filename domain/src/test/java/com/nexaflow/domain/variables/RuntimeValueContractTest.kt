package com.nexaflow.domain.variables

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RuntimeValueContractTest {

    @Test
    fun `codec round trips every runtime value variant`() {
        val values = listOf(
            RuntimeValue.NullValue,
            RuntimeValue.StringValue("hello"),
            RuntimeValue.BooleanValue(true),
            RuntimeValue.IntValue(7),
            RuntimeValue.LongValue(8L),
            RuntimeValue.DoubleValue(9.5),
            RuntimeValue.ListValue(
                listOf(RuntimeValue.StringValue("a"), RuntimeValue.IntValue(2))
            ),
            RuntimeValue.ObjectValue(
                mapOf(
                    "name" to RuntimeValue.StringValue("NexaFlow"),
                    "enabled" to RuntimeValue.BooleanValue(true)
                )
            )
        )

        values.forEach { value ->
            assertEquals(value, RuntimeValueCodec.decode(RuntimeValueCodec.encode(value)))
        }
    }

    @Test
    fun `display keeps legacy scalar substitution stable`() {
        assertEquals("", RuntimeValueCodec.display(RuntimeValue.NullValue))
        assertEquals("hello", RuntimeValueCodec.display(RuntimeValue.StringValue("hello")))
        assertEquals("true", RuntimeValueCodec.display(RuntimeValue.BooleanValue(true)))
        assertEquals("42", RuntimeValueCodec.display(RuntimeValue.IntValue(42)))
        assertEquals("43", RuntimeValueCodec.display(RuntimeValue.LongValue(43L)))
        assertEquals("1.25", RuntimeValueCodec.display(RuntimeValue.DoubleValue(1.25)))

        val list = RuntimeValue.ListValue(listOf(RuntimeValue.IntValue(1)))
        assertEquals(RuntimeValueCodec.encode(list), RuntimeValueCodec.display(list))
        val obj = RuntimeValue.ObjectValue(mapOf("x" to RuntimeValue.IntValue(1)))
        assertEquals(RuntimeValueCodec.encode(obj), RuntimeValueCodec.display(obj))
    }

    @Test
    fun `invalid doubles object keys names and versions are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeValue.DoubleValue(Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeValue.DoubleValue(Double.POSITIVE_INFINITY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeValue.ObjectValue(mapOf("" to RuntimeValue.NullValue))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeValue.ObjectValue(
                mapOf("x".repeat(RuntimeValue.MAX_OBJECT_KEY_LENGTH + 1) to RuntimeValue.NullValue)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeVariable(
                name = "1bad",
                value = RuntimeValue.IntValue(1),
                scope = VariableScope.GLOBAL
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeVariable(
                name = "good_name",
                value = RuntimeValue.IntValue(1),
                scope = VariableScope.GLOBAL,
                version = 0L
            )
        }
    }

    @Test
    fun `snapshot rejects duplicate logical names and invalid schema versions`() {
        val first = RuntimeVariable(
            name = "Token",
            value = RuntimeValue.StringValue("a"),
            scope = VariableScope.GLOBAL
        )
        val second = first.copy(name = "token", value = RuntimeValue.StringValue("b"))

        assertThrows(IllegalArgumentException::class.java) {
            VariableSnapshot(
                scope = VariableScope.GLOBAL,
                variables = listOf(first, second),
                capturedAt = 1L
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            VariableSnapshot(
                scope = VariableScope.GLOBAL,
                variables = listOf(first),
                capturedAt = 1L,
                schemaVersion = 0
            )
        }

        val valid = VariableSnapshot(
            scope = VariableScope.GLOBAL,
            variables = listOf(first),
            capturedAt = 2L
        )
        assertEquals(1, valid.schemaVersion)
        assertTrue(valid.variables.single().name == "Token")
    }

    @Test
    fun `legacy text fallback preserves undecodable stored values`() {
        val legacy = RuntimeValueCodec.decodeOrLegacyText("plain legacy value")
        assertEquals(RuntimeValue.StringValue("plain legacy value"), legacy)

        val encoded = RuntimeValueCodec.encode(RuntimeValue.BooleanValue(false))
        assertEquals(RuntimeValue.BooleanValue(false), RuntimeValueCodec.decodeOrLegacyText(encoded))
    }
}
