package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.ActionType
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.json.*

/** Bounded, deterministic transforms except explicitly random/time operations. No script evaluation. */
object DataTransforms {
    const val MAX_INPUT = 16_384
    const val MAX_OUTPUT = 65_536
    private const val MAX_ITEMS = 1024
    private const val MAX_DEPTH = 32
    val operations: Map<ActionType, List<String>> = mapOf(
        ActionType.DATA_TEXT to listOf("TRIM", "UPPER", "LOWER", "REPLACE", "SPLIT", "LENGTH", "SUBSTRING"),
        ActionType.DATA_ENCODING to listOf("BASE64_ENCODE", "BASE64_DECODE", "URL_ENCODE", "URL_DECODE", "HEX_ENCODE", "HEX_DECODE"),
        ActionType.DATA_HASH to listOf("SHA-256", "SHA-512"),
        ActionType.DATA_RANDOM to listOf("UUID", "TOKEN", "INTEGER"),
        ActionType.DATA_MATH to listOf("ADD", "SUBTRACT", "MULTIPLY", "DIVIDE", "MIN", "MAX", "ABS", "ROUND"),
        ActionType.DATA_DATE_TIME to listOf("FORMAT", "PARSE", "ADD_SECONDS", "NOW"),
        ActionType.DATA_JSON to listOf("POINTER", "PRETTY", "COMPACT", "KEYS"),
        ActionType.DATA_ARRAY to listOf("LENGTH", "FIRST", "LAST", "REVERSE", "UNIQUE", "JOIN", "SORT_NUMERIC")
    )

    fun defaultInput(type: ActionType): String = when (type) {
        ActionType.DATA_MATH -> "0"
        ActionType.DATA_DATE_TIME -> "1970-01-01T00:00:00Z"
        ActionType.DATA_JSON -> "{}"
        ActionType.DATA_ARRAY -> "[]"
        else -> ""
    }

    fun defaultField(type: ActionType, operation: String, key: String): String = when (key) {
        "input" -> defaultInput(type)
        "outputPath" -> "$.data.result"
        "zone" -> "UTC"
        "min", "start" -> "0"
        "max" -> "100"
        "argument" -> when (operation) {
            "TOKEN" -> "32"
            "FORMAT" -> "uuuu-MM-dd HH:mm:ss"
            "DIVIDE", "MULTIPLY" -> "1"
            "ADD", "SUBTRACT", "MIN", "MAX", "ROUND", "ADD_SECONDS" -> "0"
            else -> ""
        }
        else -> ""
    }

    fun apply(type: ActionType, config: Map<String, String>, input: String): Any? {
        require(input.length <= MAX_INPUT && config.size <= 100 && config.values.all { it.length <= MAX_INPUT })
        val op = config["operation"]?.takeIf { it.isNotBlank() } ?: operations.getValue(type).first()
        require(op in operations.getValue(type))
        val argument = config["argument"] ?: defaultField(type, op, "argument")
        val result: Any? = when (type) {
            ActionType.DATA_TEXT -> when (op) {
                "TRIM" -> input.trim()
                "UPPER" -> input.uppercase(Locale.ROOT)
                "LOWER" -> input.lowercase(Locale.ROOT)
                "REPLACE" -> {
                    require(argument.isNotEmpty())
                    val replacement = config["replacement"].orEmpty()
                    val count = occurrences(input, argument)
                    require(input.length.toLong() + count.toLong() * (replacement.length - argument.length) <= MAX_OUTPUT)
                    input.replace(argument, replacement)
                }
                "SPLIT" -> {
                    require(argument.isNotEmpty() && occurrences(input, argument) < MAX_ITEMS)
                    input.split(argument)
                }
                "LENGTH" -> input.codePointCount(0, input.length)
                "SUBSTRING" -> {
                    val start = integer(config, "start", 0)
                    val count = input.codePointCount(0, input.length)
                    val end = integer(config, "end", count)
                    require(start in 0..count && end in start..count)
                    input.substring(input.offsetByCodePoints(0, start), input.offsetByCodePoints(0, end))
                }
                else -> error("INVALID_OPERATION")
            }
            ActionType.DATA_ENCODING -> when (op) {
                "BASE64_ENCODE" -> Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8))
                "BASE64_DECODE" -> utf8(Base64.getDecoder().decode(input))
                "URL_ENCODE" -> {
                    val bytes = input.toByteArray(Charsets.UTF_8)
                    require(bytes.sumOf { if (urlSafe(it.toInt() and 255)) 1 else 3 } <= MAX_OUTPUT)
                    URLEncoder.encode(input, "UTF-8")
                }
                "URL_DECODE" -> decodeUrl(input)
                "HEX_ENCODE" -> input.toByteArray(Charsets.UTF_8).also { require(it.size * 2 <= MAX_OUTPUT) }.toHex()
                "HEX_DECODE" -> {
                    require(input.length % 2 == 0 && input.all { it.digitToIntOrNull(16) != null })
                    utf8(ByteArray(input.length / 2) { i -> input.substring(i * 2, i * 2 + 2).toInt(16).toByte() })
                }
                else -> error("INVALID_OPERATION")
            }
            ActionType.DATA_HASH -> MessageDigest.getInstance(op).digest(input.toByteArray(Charsets.UTF_8))
                .toHex()
            ActionType.DATA_RANDOM -> when (op) {
                "UUID" -> UUID.randomUUID().toString()
                "TOKEN" -> ByteArray(integer(config, "argument", 32).also { require(it in 16..256) })
                    .also { SecureRandom().nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
                "INTEGER" -> {
                    val min = integer(config, "min", 0)
                    val max = integer(config, "max", 100)
                    val range = max.toLong() - min + 1
                    require(range > 0)
                    val random = SecureRandom()
                    val limit = (1L shl 32) / range * range
                    var sample: Long
                    do { sample = random.nextInt().toLong() - Int.MIN_VALUE } while (sample >= limit)
                    (min + sample % range).toInt()
                }
                else -> error("INVALID_OPERATION")
            }
            ActionType.DATA_MATH -> {
                require(input.length <= 256 && argument.length <= 256)
                val a = decimal(input)
                val b = if (op in listOf("ABS", "ROUND")) BigDecimal.ZERO else decimal(argument.ifBlank { defaultField(type, op, "argument") })
                when (op) {
                    "ADD" -> a.add(b, MathContext.DECIMAL128)
                    "SUBTRACT" -> a.subtract(b, MathContext.DECIMAL128)
                    "MULTIPLY" -> a.multiply(b, MathContext.DECIMAL128)
                    "DIVIDE" -> a.divide(b, MathContext.DECIMAL128)
                    "MIN" -> a.min(b)
                    "MAX" -> a.max(b)
                    "ABS" -> a.abs()
                    "ROUND" -> a.setScale(integer(config, "argument", 0).also { require(it in 0..32) }, java.math.RoundingMode.HALF_UP)
                    else -> error("INVALID_OPERATION")
                }.stripTrailingZeros().toPlainString()
            }
            ActionType.DATA_DATE_TIME -> when (op) {
                "NOW" -> Instant.now().toString()
                "PARSE" -> Instant.parse(input).toEpochMilli()
                "ADD_SECONDS" -> Instant.parse(input).plusSeconds(argument.toLong()).toString()
                "FORMAT" -> {
                    require(argument.length <= 128)
                    DateTimeFormatter.ofPattern(argument.ifBlank { "uuuu-MM-dd HH:mm:ss" }, Locale.ROOT)
                        .withZone(ZoneId.of(config["zone"].orEmpty().ifBlank { "UTC" })).format(Instant.parse(input))
                }
                else -> error("INVALID_OPERATION")
            }
            ActionType.DATA_JSON -> {
                val json = parseJson(input)
                when (op) {
                    "POINTER" -> native(pointer(json, argument))
                    "PRETTY" -> boundedJson(json, MAX_OUTPUT, pretty = true)
                    "COMPACT" -> json.toString()
                    "KEYS" -> (json as JsonObject).keys.toList()
                    else -> error("INVALID_OPERATION")
                }
            }
            ActionType.DATA_ARRAY -> {
                val array = parseJson(input) as JsonArray
                require(array.size <= MAX_ITEMS)
                when (op) {
                    "LENGTH" -> array.size
                    "FIRST" -> native(array.firstOrNull() ?: JsonNull)
                    "LAST" -> native(array.lastOrNull() ?: JsonNull)
                    "REVERSE" -> array.reversed().map(::native)
                    "UNIQUE" -> array.distinct().map(::native)
                    "JOIN" -> {
                        require(input.length.toLong() + argument.length.toLong() * maxOf(0, array.size - 1) <= MAX_OUTPUT)
                        array.joinToString(argument) { (it as? JsonPrimitive)?.content ?: it.toString() }
                    }
                    "SORT_NUMERIC" -> array.map { decimal((it as JsonPrimitive).content) }.sorted()
                    else -> error("INVALID_OPERATION")
                }
            }
            else -> error("UNSUPPORTED_TRANSFORM")
        }
        if (result is String) require(result.length <= MAX_OUTPUT)
        else boundedJson(result, MAX_OUTPUT)
        return result
    }

    private fun parseJson(input: String): JsonElement {
        var depth = 0
        var quoted = false
        var escaped = false
        for (c in input) {
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= MAX_DEPTH) }
                '}', ']' -> depth--
            }
        }
        return Json.parseToJsonElement(input)
    }

    private fun pointer(root: JsonElement, path: String): JsonElement {
        if (path.isEmpty()) return root
        require(path.startsWith('/'))
        var node = root
        for (part in path.drop(1).split('/')) {
            require(!Regex("~(?![01])").containsMatchIn(part))
            val key = part.replace("~1", "/").replace("~0", "~")
            node = when (val current = node) {
                is JsonObject -> current[key] ?: error("MISSING_POINTER")
                is JsonArray -> {
                    require(key.matches(Regex("0|[1-9][0-9]*")))
                    current[key.toInt()]
                }
                else -> error("INVALID_POINTER")
            }
        }
        return node
    }

    fun inputText(value: Any?): String = if (value is String) value.also { require(it.length <= MAX_INPUT) }
        else boundedJson(value, MAX_INPUT)

    private fun native(value: JsonElement): Any? = when (value) {
        JsonNull -> null
        is JsonObject -> value.mapValues { native(it.value) }
        is JsonArray -> value.map(::native)
        is JsonPrimitive -> if (value.isString) value.content else value.booleanOrNull ?: value.longOrNull ?: decimal(value.content)
    }

    private fun integer(config: Map<String, String>, key: String, default: Int): Int =
        config[key]?.takeIf { it.isNotBlank() }?.let { requireNotNull(it.toIntOrNull()) } ?: default

    private fun decimal(value: String): BigDecimal {
        require(value.length <= 256)
        return value.toBigDecimal().also { require(kotlin.math.abs(it.scale().toLong()) <= 1024) }
    }

    private fun numberText(value: Number): String {
        when (value) {
            is BigDecimal -> require(value.precision() <= 256 && kotlin.math.abs(value.scale().toLong()) <= 1024)
            is BigInteger -> require(value.bitLength() <= 850)
            is Double -> require(value.isFinite())
            is Float -> require(value.isFinite())
            is Byte, is Short, is Int, is Long -> Unit
            else -> throw IllegalArgumentException("UNSUPPORTED_NUMBER_TYPE")
        }
        return value.toString().also { decimal(it) }
    }

    private fun occurrences(input: String, delimiter: String): Int {
        var count = 0
        var offset = 0
        while (true) {
            val next = input.indexOf(delimiter, offset)
            if (next < 0) return count
            count++
            offset = next + delimiter.length
        }
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 255
            append("0123456789abcdef"[value ushr 4])
            append("0123456789abcdef"[value and 15])
        }
    }

    private fun utf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (exception: java.nio.charset.CharacterCodingException) {
        throw IllegalArgumentException("INVALID_UTF8", exception)
    }

    private fun urlSafe(byte: Int) = byte in 65..90 || byte in 97..122 || byte in 48..57 || byte in listOf(32, 45, 95, 46, 42)

    /** application/x-www-form-urlencoded decoding, rejecting malformed UTF-8 instead of replacing it. */
    private fun decodeUrl(input: String): String {
        val bytes = java.io.ByteArrayOutputStream(input.length)
        var offset = 0
        while (offset < input.length) {
            when (input[offset]) {
                '%' -> {
                    require(offset + 2 < input.length)
                    val high = requireNotNull(input[offset + 1].digitToIntOrNull(16))
                    val low = requireNotNull(input[offset + 2].digitToIntOrNull(16))
                    bytes.write(high * 16 + low)
                    offset += 3
                }
                '+' -> { bytes.write(32); offset++ }
                else -> {
                    val codePoint = input.codePointAt(offset)
                    bytes.write(String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8))
                    offset += Character.charCount(codePoint)
                }
            }
        }
        return utf8(bytes.toByteArray())
    }

    /** Emits into a fixed-budget buffer; checks every append before allocating any expanded output. */
    private fun boundedJson(value: Any?, limit: Int, pretty: Boolean = false): String {
        val out = StringBuilder(limit)
        fun append(text: String) { require(text.length <= limit - out.length); out.append(text) }
        fun quoted(text: String) {
            append("\"")
            for (char in text) when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u" + char.code.toString(16).padStart(4, '0')) else append(char.toString())
            }
            append("\"")
        }
        fun indent(depth: Int) { if (pretty) { append("\n"); append("    ".repeat(depth)) } }
        fun emit(item: Any?, depth: Int) {
            require(depth <= MAX_DEPTH)
            when (item) {
                null, JsonNull -> append("null")
                is JsonPrimitive -> if (item.isString) quoted(item.content) else {
                    item.booleanOrNull ?: decimal(item.content)
                    append(item.content)
                }
                is String -> quoted(item)
                is Boolean -> append(item.toString())
                is Number -> append(numberText(item))
                is Map<*, *> -> {
                    require(item.size <= MAX_ITEMS)
                    append("{")
                    item.entries.forEachIndexed { index, (key, child) ->
                        require(key is String)
                        if (index > 0) append(",")
                        indent(depth + 1)
                        quoted(key)
                        append(if (pretty) ": " else ":")
                        emit(child, depth + 1)
                    }
                    if (item.isNotEmpty()) indent(depth)
                    append("}")
                }
                is List<*> -> {
                    require(item.size <= MAX_ITEMS)
                    append("[")
                    item.forEachIndexed { index, child ->
                        if (index > 0) append(",")
                        indent(depth + 1)
                        emit(child, depth + 1)
                    }
                    if (item.isNotEmpty()) indent(depth)
                    append("]")
                }
                else -> throw IllegalArgumentException("UNSUPPORTED_INPUT_TYPE")
            }
        }
        emit(value, 0)
        return out.toString()
    }
}
