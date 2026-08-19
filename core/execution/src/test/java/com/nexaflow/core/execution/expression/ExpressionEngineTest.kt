package com.nexaflow.core.execution.expression

import com.nexaflow.domain.variables.RuntimeValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionEngineTest {

    private val variables = mapOf(
        "battery" to RuntimeValue.IntValue(85),
        "wifi" to RuntimeValue.BooleanValue(true),
        "name" to RuntimeValue.StringValue("NexaFlow"),
        "tags" to RuntimeValue.ListValue(listOf(RuntimeValue.StringValue("home"), RuntimeValue.StringValue("night"))),
        "config" to RuntimeValue.ObjectValue(mapOf("mode" to RuntimeValue.StringValue("safe")))
    )

    @Test
    fun `comparison and boolean precedence are deterministic`() {
        assertTrue(ExpressionEngine.evaluateBoolean("battery >= 80 AND wifi == true", variables))
        assertTrue(ExpressionEngine.evaluateBoolean("battery < 80 OR wifi == true AND name == 'NexaFlow'", variables))
        assertFalse(ExpressionEngine.evaluateBoolean("NOT wifi == true", variables))
    }

    @Test
    fun `string list and object functions evaluate without side effects`() {
        assertTrue(ExpressionEngine.evaluateBoolean("name startsWith 'Nexa'", variables))
        assertTrue(ExpressionEngine.evaluateBoolean("name endsWith 'Flow'", variables))
        assertTrue(ExpressionEngine.evaluateBoolean("tags contains 'night'", variables))
        assertTrue(ExpressionEngine.evaluateBoolean("config contains 'mode'", variables))
        assertTrue(ExpressionEngine.evaluateBoolean("contains(name, 'xaF')", variables))
        assertTrue(ExpressionEngine.evaluateBoolean("exists('battery')", variables))
        assertFalse(ExpressionEngine.evaluateBoolean("exists('missing')", variables))
        assertEquals(
            "nexaflow",
            (ExpressionEngine.evaluate("lower(name)", variables) as RuntimeValue.StringValue).value
        )
        assertEquals(
            2L,
            (ExpressionEngine.evaluate("length(tags)", variables) as RuntimeValue.LongValue).value
        )
    }

    @Test
    fun `invalid code like constructs and incompatible types are rejected`() {
        assertThrows(ExpressionException::class.java) {
            ExpressionEngine.evaluate("java.lang.Runtime.getRuntime()", variables)
        }
        assertThrows(ExpressionException::class.java) {
            ExpressionEngine.evaluateBoolean("wifi > 0", variables)
        }
        assertThrows(ExpressionException::class.java) {
            ExpressionEngine.evaluate("unknown == true", variables)
        }
        assertThrows(ExpressionException::class.java) {
            ExpressionEngine.evaluate("unsupported(name)", variables)
        }
    }
}
