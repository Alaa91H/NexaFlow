package com.nexaflow.domain.workflow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionExpressionEvaluatorTest {

    @Test
    fun `empty and blank expressions evaluate to true`() {
        assertTrue(ConditionExpressionEvaluator.evaluate(""))
        assertTrue(ConditionExpressionEvaluator.evaluate("   "))
    }

    @Test
    fun `numeric comparisons work as expected`() {
        assertTrue(ConditionExpressionEvaluator.evaluate("15 < 20"))
        assertFalse(ConditionExpressionEvaluator.evaluate("25 < 20"))
        assertTrue(ConditionExpressionEvaluator.evaluate("20 <= 20"))
        assertTrue(ConditionExpressionEvaluator.evaluate("30 > 20"))
        assertFalse(ConditionExpressionEvaluator.evaluate("10 >= 20"))
        assertTrue(ConditionExpressionEvaluator.evaluate("100 == 100"))
        assertTrue(ConditionExpressionEvaluator.evaluate("100 != 200"))
    }

    @Test
    fun `floating point comparisons work`() {
        assertTrue(ConditionExpressionEvaluator.evaluate("3.14 > 3.0"))
        assertTrue(ConditionExpressionEvaluator.evaluate("99.9 <= 100.0"))
        assertFalse(ConditionExpressionEvaluator.evaluate("1.5 == 2.5"))
    }

    @Test
    fun `string comparisons work with and without quotes`() {
        assertTrue(ConditionExpressionEvaluator.evaluate("\"CONNECTED\" == \"CONNECTED\""))
        assertTrue(ConditionExpressionEvaluator.evaluate("CONNECTED == CONNECTED"))
        assertFalse(ConditionExpressionEvaluator.evaluate("CONNECTED == DISCONNECTED"))
        assertTrue(ConditionExpressionEvaluator.evaluate("\"hello world\" contains \"world\""))
        assertTrue(ConditionExpressionEvaluator.evaluate("+123456789 startsWith \"+1\""))
        assertTrue(ConditionExpressionEvaluator.evaluate("file.txt endsWith \".txt\""))
        assertTrue(ConditionExpressionEvaluator.evaluate("user123 matches \"^[a-z]+[0-9]+$\""))
    }

    @Test
    fun `logical and, or, not operators work`() {
        assertTrue(ConditionExpressionEvaluator.evaluate("10 < 20 && 30 > 20"))
        assertFalse(ConditionExpressionEvaluator.evaluate("10 < 20 && 30 < 20"))
        assertTrue(ConditionExpressionEvaluator.evaluate("10 < 5 || 30 > 20"))
        assertFalse(ConditionExpressionEvaluator.evaluate("10 < 5 || 30 < 20"))
        assertTrue(ConditionExpressionEvaluator.evaluate("!false"))
        assertTrue(ConditionExpressionEvaluator.evaluate("!(10 > 20)"))
        assertTrue(ConditionExpressionEvaluator.evaluate("NOT 10 > 20"))
    }

    @Test
    fun `functions isEmpty and isNotEmpty work`() {
        assertTrue(ConditionExpressionEvaluator.evaluate("isEmpty(\"\")"))
        assertTrue(ConditionExpressionEvaluator.evaluate("isEmpty()"))
        assertFalse(ConditionExpressionEvaluator.evaluate("isEmpty(\"hello\")"))
        assertTrue(ConditionExpressionEvaluator.evaluate("isNotEmpty(\"hello\")"))
        assertFalse(ConditionExpressionEvaluator.evaluate("isNotEmpty(\"\")"))
    }

    @Test
    fun `complex compound expressions evaluate correctly`() {
        val expr = "(%BATTERY < 20 || %CHARGER == \"OFF\") && %WIFI == \"CONNECTED\""
            .replace("%BATTERY", "15")
            .replace("%CHARGER", "OFF")
            .replace("%WIFI", "CONNECTED")

        assertTrue(ConditionExpressionEvaluator.evaluate(expr))

        val failingExpr = "(%BATTERY < 20 || %CHARGER == \"OFF\") && %WIFI == \"CONNECTED\""
            .replace("%BATTERY", "85")
            .replace("%CHARGER", "ON")
            .replace("%WIFI", "CONNECTED")

        assertFalse(ConditionExpressionEvaluator.evaluate(failingExpr))
    }
}
