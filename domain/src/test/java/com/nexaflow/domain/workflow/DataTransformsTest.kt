package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.NumericSensors
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class DataTransformsTest {
    private fun transform(type: ActionType, op: String, input: String, vararg config: Pair<String, String>) =
        DataTransforms.apply(type, mapOf("operation" to op) + config, input)

    @Test fun unicodeTextAndLiteralReplacement() {
        assertEquals(3, transform(ActionType.DATA_TEXT, "LENGTH", "a😀b"))
        assertEquals("😀", transform(ActionType.DATA_TEXT, "SUBSTRING", "a😀b", "start" to "1", "end" to "2"))
        assertEquals("a-b", transform(ActionType.DATA_TEXT, "REPLACE", "a.b", "argument" to ".", "replacement" to "-"))
        assertEquals(listOf("a", "b"), transform(ActionType.DATA_TEXT, "SPLIT", "a|b", "argument" to "|"))
    }

    @Test fun encodingsRoundTripUnicode() {
        for ((encode, decode) in listOf("BASE64_ENCODE" to "BASE64_DECODE", "URL_ENCODE" to "URL_DECODE", "HEX_ENCODE" to "HEX_DECODE")) {
            val encoded = transform(ActionType.DATA_ENCODING, encode, "مرحبا 😀 +") as String
            assertEquals("مرحبا 😀 +", transform(ActionType.DATA_ENCODING, decode, encoded))
        }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", transform(ActionType.DATA_HASH, "SHA-256", "abc"))
    }

    @Test fun exactDecimalMathAndDates() {
        assertEquals("0.3", transform(ActionType.DATA_MATH, "ADD", "0.1", "argument" to "0.2"))
        assertEquals("1.24", transform(ActionType.DATA_MATH, "ROUND", "1.235", "argument" to "2"))
        assertEquals(0L, transform(ActionType.DATA_DATE_TIME, "PARSE", "1970-01-01T00:00:00Z"))
        assertEquals("1970-01-01T00:01:00Z", transform(ActionType.DATA_DATE_TIME, "ADD_SECONDS", "1970-01-01T00:00:00Z", "argument" to "60"))
        assertEquals("01:00", transform(ActionType.DATA_DATE_TIME, "FORMAT", "1970-01-01T00:00:00Z", "argument" to "HH:mm", "zone" to "Europe/Berlin"))
    }

    @Test fun jsonPointersArraysAndContextSerialization() {
        assertEquals(7L, transform(ActionType.DATA_JSON, "POINTER", "{\"a/b\":[7]}", "argument" to "/a~1b/0"))
        assertEquals(listOf(1L, 2L), transform(ActionType.DATA_ARRAY, "UNIQUE", "[1,2,1]"))
        assertEquals("a/b", transform(ActionType.DATA_ARRAY, "JOIN", "[\"a\",\"b\"]", "argument" to "/"))
        val nested = DataTransforms.inputText(mapOf("items" to listOf("x", "y")))
        assertEquals("y", transform(ActionType.DATA_JSON, "POINTER", nested, "argument" to "/items/1"))
    }

    @Test fun randomBoundsAndEntropyShape() {
        val tokens = (1..100).map { transform(ActionType.DATA_RANDOM, "TOKEN", "") as String }
        assertEquals(100, tokens.toSet().size)
        assertTrue(tokens.all { it.length == 43 && it.matches(Regex("[A-Za-z0-9_-]+")) })
        repeat(100) {
            assertTrue((transform(ActionType.DATA_RANDOM, "INTEGER", "", "min" to "-5", "max" to "2") as Int) in -5..2)
        }
        assertEquals(Int.MIN_VALUE, transform(ActionType.DATA_RANDOM, "INTEGER", "", "min" to Int.MIN_VALUE.toString(), "max" to Int.MIN_VALUE.toString()))
        assertTrue(transform(ActionType.DATA_RANDOM, "INTEGER", "", "min" to Int.MIN_VALUE.toString(), "max" to Int.MAX_VALUE.toString()) is Int)
    }

    @Test fun hostileInputsFailBeforeExpansion() {
        val cases = listOf<() -> Any?>(
            { transform(ActionType.DATA_JSON, "COMPACT", "[".repeat(33) + "0" + "]".repeat(33)) },
            { transform(ActionType.DATA_MATH, "ADD", "1e99999999", "argument" to "1") },
            { transform(ActionType.DATA_TEXT, "REPLACE", "x".repeat(16000), "argument" to "x", "replacement" to "a".repeat(16000)) },
            { transform(ActionType.DATA_ENCODING, "HEX_DECODE", "xyz") },
            { transform(ActionType.DATA_TEXT, "UPPER", "x".repeat(16385)) }
        )
        cases.forEach { action -> assertTrue(runCatching(action).isFailure) }
    }

    @Test fun allDeterministicOperationsHaveAnExecutableContract() {
        data class Case(val type: ActionType, val operation: String, val input: String, val expected: Any?, val config: Map<String, String> = emptyMap())
        val cases = listOf(
            Case(ActionType.DATA_TEXT, "TRIM", " x ", "x"),
            Case(ActionType.DATA_TEXT, "UPPER", "Straße", "STRASSE"),
            Case(ActionType.DATA_TEXT, "LOWER", "ABC", "abc"),
            Case(ActionType.DATA_TEXT, "REPLACE", "a b", "a-b", mapOf("argument" to " ", "replacement" to "-")),
            Case(ActionType.DATA_TEXT, "SPLIT", "a b", listOf("a", "b"), mapOf("argument" to " ")),
            Case(ActionType.DATA_TEXT, "LENGTH", "😀x", 2),
            Case(ActionType.DATA_TEXT, "SUBSTRING", "😀x", "x", mapOf("start" to "1")),
            Case(ActionType.DATA_ENCODING, "BASE64_ENCODE", "abc", "YWJj"),
            Case(ActionType.DATA_ENCODING, "BASE64_DECODE", "YWJj", "abc"),
            Case(ActionType.DATA_ENCODING, "URL_ENCODE", "a +", "a+%2B"),
            Case(ActionType.DATA_ENCODING, "URL_DECODE", "a+%2B", "a +"),
            Case(ActionType.DATA_ENCODING, "HEX_ENCODE", "é", "c3a9"),
            Case(ActionType.DATA_ENCODING, "HEX_DECODE", "C3A9", "é"),
            Case(ActionType.DATA_HASH, "SHA-256", "abc", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
            Case(ActionType.DATA_HASH, "SHA-512", "abc", "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f"),
            Case(ActionType.DATA_MATH, "ADD", "1.1", "3.3", mapOf("argument" to "2.2")),
            Case(ActionType.DATA_MATH, "SUBTRACT", "1.1", "-1.1", mapOf("argument" to "2.2")),
            Case(ActionType.DATA_MATH, "MULTIPLY", "1.1", "2.42", mapOf("argument" to "2.2")),
            Case(ActionType.DATA_MATH, "DIVIDE", "4.2", "2.1", mapOf("argument" to "2")),
            Case(ActionType.DATA_MATH, "MIN", "1.1", "1.1", mapOf("argument" to "2.2")),
            Case(ActionType.DATA_MATH, "MAX", "1.1", "2.2", mapOf("argument" to "2.2")),
            Case(ActionType.DATA_MATH, "ABS", "-1.1", "1.1"),
            Case(ActionType.DATA_MATH, "ROUND", "1.235", "1.24", mapOf("argument" to "2")),
            Case(ActionType.DATA_DATE_TIME, "FORMAT", "1970-01-01T00:00:00Z", "1970-01-01 00:00:00"),
            Case(ActionType.DATA_DATE_TIME, "PARSE", "1970-01-01T00:00:00Z", 0L),
            Case(ActionType.DATA_DATE_TIME, "ADD_SECONDS", "1970-01-01T00:00:00Z", "1970-01-01T00:00:02Z", mapOf("argument" to "2")),
            Case(ActionType.DATA_JSON, "POINTER", "{\"a~b\":null}", null, mapOf("argument" to "/a~0b")),
            Case(ActionType.DATA_JSON, "COMPACT", " { \"a\" : 1 } ", "{\"a\":1}"),
            Case(ActionType.DATA_JSON, "PRETTY", "{\"a\":1}", "{\n    \"a\": 1\n}"),
            Case(ActionType.DATA_JSON, "KEYS", "{\"a\":1}", listOf("a")),
            Case(ActionType.DATA_ARRAY, "LENGTH", "[1,2]", 2),
            Case(ActionType.DATA_ARRAY, "FIRST", "[1,2]", 1L),
            Case(ActionType.DATA_ARRAY, "LAST", "[1,2]", 2L),
            Case(ActionType.DATA_ARRAY, "REVERSE", "[1,2]", listOf(2L, 1L)),
            Case(ActionType.DATA_ARRAY, "UNIQUE", "[1,2,1]", listOf(1L, 2L)),
            Case(ActionType.DATA_ARRAY, "JOIN", "[1,2]", "1 2", mapOf("argument" to " ")),
            Case(ActionType.DATA_ARRAY, "SORT_NUMERIC", "[10,-1,2.5]", listOf(BigDecimal("-1"), BigDecimal("2.5"), BigDecimal("10")))
        )
        cases.forEach { case ->
            assertEquals("${case.type}/${case.operation}", case.expected,
                DataTransforms.apply(case.type, case.config + ("operation" to case.operation), case.input))
        }
        val covered = cases.map { it.type to it.operation }.toSet() + setOf(
            ActionType.DATA_RANDOM to "UUID", ActionType.DATA_RANDOM to "TOKEN", ActionType.DATA_RANDOM to "INTEGER",
            ActionType.DATA_DATE_TIME to "NOW")
        assertEquals(DataTransforms.operations.flatMap { (type, ops) -> ops.map { type to it } }.toSet(), covered)
        UUID.fromString(transform(ActionType.DATA_RANDOM, "UUID", "") as String)
        val before = Instant.now()
        val now = Instant.parse(transform(ActionType.DATA_DATE_TIME, "NOW", "") as String)
        assertFalse(now.isBefore(before))
        assertFalse(now.isAfter(Instant.now()))
    }

    @Test fun numericJsonRemainsExactAndRejectsUnsupportedMagnitude() {
        val precise = "0.123456789012345678901234567890123456789"
        assertEquals(BigDecimal(precise), transform(ActionType.DATA_JSON, "POINTER", precise))
        assertEquals(precise, DataTransforms.inputText(transform(ActionType.DATA_JSON, "POINTER", precise)))
        assertTrue(runCatching { transform(ActionType.DATA_JSON, "POINTER", "1e999999999") }.isFailure)
        assertTrue(runCatching { transform(ActionType.DATA_JSON, "POINTER", "[1]", "argument" to "/00") }.isFailure)
    }

    @Test fun expansionAndRecursiveContextBudgetsRejectAdversarialInputs() {
        val prettyBomb = "[".repeat(31) + List(1024) { "0" }.joinToString(",", "[", "]") + "]".repeat(31)
        var nested: Any? = 1
        repeat(33) { nested = listOf(nested) }
        val cases = listOf<() -> Any?>(
            { transform(ActionType.DATA_ENCODING, "URL_ENCODE", "漢".repeat(16_384)) },
            { transform(ActionType.DATA_ENCODING, "HEX_ENCODE", "漢".repeat(16_384)) },
            { transform(ActionType.DATA_JSON, "PRETTY", prettyBomb) },
            { transform(ActionType.DATA_TEXT, "SPLIT", "a,".repeat(1024), "argument" to ",") },
            { DataTransforms.inputText(mapOf("large" to "x".repeat(16_384))) },
            { DataTransforms.inputText(nested) },
            { DataTransforms.inputText(listOf(Double.NaN)) },
            { transform(ActionType.DATA_ENCODING, "BASE64_DECODE", "/w==") },
            { transform(ActionType.DATA_ENCODING, "HEX_DECODE", "ff") },
            { transform(ActionType.DATA_ENCODING, "URL_DECODE", "%FF") },
            { transform(ActionType.DATA_RANDOM, "TOKEN", "", "argument" to "oops") },
            { transform(ActionType.DATA_RANDOM, "INTEGER", "", "min" to "oops") },
            { transform(ActionType.DATA_TEXT, "SUBSTRING", "abc", "start" to "oops") },
            { transform(ActionType.DATA_MATH, "ROUND", "1", "argument" to "oops") }
        )
        cases.forEachIndexed { index, operation -> assertTrue("Rejection $index", runCatching(operation).isFailure) }
        // A long replacement is harmless when the search text is absent.
        assertEquals("x".repeat(16_000), transform(ActionType.DATA_TEXT, "REPLACE", "x".repeat(16_000), "argument" to "y", "replacement" to "z".repeat(16_000)))
    }

    @Test fun sensorComparisonsRejectInvalidAndNonFiniteValues() {
        assertTrue(NumericSensors.matches(mapOf("threshold" to "10", "event" to "AT_LEAST"), 10f))
        assertFalse(NumericSensors.matches(mapOf("threshold" to "10", "event" to "ABOVE"), 10f))
        assertTrue(NumericSensors.matches(mapOf("threshold" to "-5", "upperThreshold" to "5", "event" to "BETWEEN"), 0f))
        assertFalse(NumericSensors.matches(mapOf("threshold" to "NaN"), 1f))
        assertFalse(NumericSensors.matches(mapOf("threshold" to "0"), Float.POSITIVE_INFINITY))
        assertFalse(NumericSensors.matches(mapOf("threshold" to "2", "upperThreshold" to "1", "event" to "BETWEEN"), 1.5f))
    }
}
