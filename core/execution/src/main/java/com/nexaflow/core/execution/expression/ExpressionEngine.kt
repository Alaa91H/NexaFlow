package com.nexaflow.core.execution.expression

import com.nexaflow.domain.variables.RuntimeValue

/** Structured parser/evaluator failure; callers can surface this before queueing. */
class ExpressionException(message: String) : IllegalArgumentException(message)

/**
 * Side-effect-free expression library for branches, conditions and policies.
 * It accepts a closed `RuntimeValue` algebra only; it does not load classes,
 * run reflection, invoke scripts, or access Android/network/file APIs.
 */
object ExpressionEngine {

    fun evaluate(expression: String, variables: Map<String, RuntimeValue>): RuntimeValue {
        val normalized = variables.entries.associate { (name, value) -> name.lowercase() to value }
        return Parser(Tokenizer(expression).tokens(), normalized).parse()
    }

    fun evaluateBoolean(expression: String, variables: Map<String, RuntimeValue>): Boolean {
        val result = evaluate(expression, variables)
        return (result as? RuntimeValue.BooleanValue)?.value
            ?: throw ExpressionException("Expression must evaluate to Boolean, got ${result.typeName()}")
    }

    private enum class TokenType {
        IDENTIFIER, STRING, NUMBER, TRUE, FALSE, NULL,
        LEFT_PAREN, RIGHT_PAREN, COMMA,
        EQUALS, NOT_EQUALS, GREATER, LESS, GREATER_OR_EQUALS, LESS_OR_EQUALS,
        AND, OR, NOT, CONTAINS, STARTS_WITH, ENDS_WITH, EOF
    }

    private data class Token(val type: TokenType, val lexeme: String, val offset: Int)

    private class Tokenizer(private val source: String) {
        private var position = 0

        fun tokens(): List<Token> {
            val result = mutableListOf<Token>()
            while (position < source.length) {
                when (val current = source[position]) {
                    ' ', '\t', '\n', '\r' -> position++
                    '(' -> result += one(TokenType.LEFT_PAREN)
                    ')' -> result += one(TokenType.RIGHT_PAREN)
                    ',' -> result += one(TokenType.COMMA)
                    '=' -> result += if (peek('=')) two(TokenType.EQUALS) else errorAt("Use == for equality")
                    '!' -> result += if (peek('=')) two(TokenType.NOT_EQUALS) else one(TokenType.NOT)
                    '>' -> result += if (peek('=')) two(TokenType.GREATER_OR_EQUALS) else one(TokenType.GREATER)
                    '<' -> result += if (peek('=')) two(TokenType.LESS_OR_EQUALS) else one(TokenType.LESS)
                    '\'', '"' -> result += string(current)
                    else -> when {
                        current.isDigit() || (current == '-' && peekDigit()) -> result += number()
                        current.isLetter() || current == '_' -> result += word()
                        else -> errorAt("Unexpected character '$current'")
                    }
                }
            }
            result += Token(TokenType.EOF, "", position)
            return result
        }

        private fun one(type: TokenType): Token = Token(type, source[position].toString(), position++)

        private fun two(type: TokenType): Token {
            val start = position
            position += 2
            return Token(type, source.substring(start, position), start)
        }

        private fun string(quote: Char): Token {
            val start = position++
            val out = StringBuilder()
            while (position < source.length && source[position] != quote) {
                val current = source[position++]
                if (current == '\\') {
                    if (position >= source.length) errorAt("Unterminated escape", start)
                    out.append(
                        when (val escaped = source[position++]) {
                            '\\' -> '\\'
                            '\'', '"' -> escaped
                            'n' -> '\n'
                            't' -> '\t'
                            else -> errorAt("Unsupported escape \\$escaped", position - 2)
                        }
                    )
                } else {
                    out.append(current)
                }
            }
            if (position >= source.length) errorAt("Unterminated string", start)
            position++
            return Token(TokenType.STRING, out.toString(), start)
        }

        private fun number(): Token {
            val start = position
            if (source[position] == '-') position++
            while (position < source.length && source[position].isDigit()) position++
            var isDecimal = false
            if (position < source.length && source[position] == '.') {
                isDecimal = true
                position++
                if (position >= source.length || !source[position].isDigit()) errorAt("Invalid decimal", start)
                while (position < source.length && source[position].isDigit()) position++
            }
            return Token(if (isDecimal) TokenType.NUMBER else TokenType.NUMBER, source.substring(start, position), start)
        }

        private fun word(): Token {
            val start = position
            position++
            while (position < source.length && (source[position].isLetterOrDigit() || source[position] == '_')) position++
            val lexeme = source.substring(start, position)
            return Token(
                when (lexeme.uppercase()) {
                    "TRUE" -> TokenType.TRUE
                    "FALSE" -> TokenType.FALSE
                    "NULL" -> TokenType.NULL
                    "AND" -> TokenType.AND
                    "OR" -> TokenType.OR
                    "NOT" -> TokenType.NOT
                    "CONTAINS" -> TokenType.CONTAINS
                    "STARTSWITH" -> TokenType.STARTS_WITH
                    "ENDSWITH" -> TokenType.ENDS_WITH
                    else -> TokenType.IDENTIFIER
                },
                lexeme,
                start
            )
        }

        private fun peek(expected: Char): Boolean = position + 1 < source.length && source[position + 1] == expected
        private fun peekDigit(): Boolean = position + 1 < source.length && source[position + 1].isDigit()
        private fun errorAt(message: String, offset: Int = position): Nothing =
            throw ExpressionException("$message at position $offset")
    }

    private class Parser(
        private val tokens: List<Token>,
        private val variables: Map<String, RuntimeValue>
    ) {
        private var cursor = 0

        fun parse(): RuntimeValue {
            val result = parseOr()
            expect(TokenType.EOF, "Unexpected token")
            return result
        }

        private fun parseOr(): RuntimeValue {
            var value = parseAnd()
            while (match(TokenType.OR)) value = RuntimeValue.BooleanValue(asBoolean(value) || asBoolean(parseAnd()))
            return value
        }

        private fun parseAnd(): RuntimeValue {
            var value = parseComparison()
            while (match(TokenType.AND)) value = RuntimeValue.BooleanValue(asBoolean(value) && asBoolean(parseComparison()))
            return value
        }

        private fun parseComparison(): RuntimeValue {
            var value = parseUnary()
            while (current().type in COMPARISON_TOKENS) {
                val operation = advance().type
                val right = parseUnary()
                value = RuntimeValue.BooleanValue(compare(operation, value, right))
            }
            return value
        }

        private fun parseUnary(): RuntimeValue =
            if (match(TokenType.NOT)) RuntimeValue.BooleanValue(!asBoolean(parseUnary())) else parsePrimary()

        private fun parsePrimary(): RuntimeValue {
            val token = advance()
            return when (token.type) {
                TokenType.STRING -> RuntimeValue.StringValue(token.lexeme)
                TokenType.TRUE -> RuntimeValue.BooleanValue(true)
                TokenType.FALSE -> RuntimeValue.BooleanValue(false)
                TokenType.NULL -> RuntimeValue.NullValue
                TokenType.NUMBER -> parseNumber(token)
                TokenType.IDENTIFIER, TokenType.CONTAINS -> if (match(TokenType.LEFT_PAREN)) function(token) else {
                    variables[token.lexeme.lowercase()]
                        ?: throw ExpressionException("Unknown variable '${token.lexeme}' at position ${token.offset}")
                }
                TokenType.LEFT_PAREN -> parseOr().also { expect(TokenType.RIGHT_PAREN, "Expected ')' after expression") }
                else -> throw ExpressionException("Expected value at position ${token.offset}")
            }
        }

        private fun function(name: Token): RuntimeValue {
            val arguments = mutableListOf<RuntimeValue>()
            if (!match(TokenType.RIGHT_PAREN)) {
                do {
                    arguments += parseOr()
                } while (match(TokenType.COMMA))
                expect(TokenType.RIGHT_PAREN, "Expected ')' after function arguments")
            }
            return when (name.lexeme.lowercase()) {
                "length" -> RuntimeValue.LongValue(length(oneArgument(name, arguments)))
                "lower" -> RuntimeValue.StringValue(asString(oneArgument(name, arguments)).lowercase())
                "upper" -> RuntimeValue.StringValue(asString(oneArgument(name, arguments)).uppercase())
                "contains" -> RuntimeValue.BooleanValue(contains(twoArguments(name, arguments)[0], twoArguments(name, arguments)[1]))
                "exists" -> {
                    val lookup = asString(oneArgument(name, arguments))
                    RuntimeValue.BooleanValue(lookup.lowercase() in variables)
                }
                else -> throw ExpressionException("Unsupported function '${name.lexeme}' at position ${name.offset}")
            }
        }

        private fun compare(operation: TokenType, left: RuntimeValue, right: RuntimeValue): Boolean = when (operation) {
            TokenType.EQUALS -> equal(left, right)
            TokenType.NOT_EQUALS -> !equal(left, right)
            TokenType.GREATER -> ordered(left, right) > 0
            TokenType.LESS -> ordered(left, right) < 0
            TokenType.GREATER_OR_EQUALS -> ordered(left, right) >= 0
            TokenType.LESS_OR_EQUALS -> ordered(left, right) <= 0
            TokenType.CONTAINS -> contains(left, right)
            TokenType.STARTS_WITH -> asString(left).startsWith(asString(right))
            TokenType.ENDS_WITH -> asString(left).endsWith(asString(right))
            else -> error("Not a comparison")
        }

        private fun equal(left: RuntimeValue, right: RuntimeValue): Boolean {
            val leftNumber = left.numberOrNull()
            val rightNumber = right.numberOrNull()
            return if (leftNumber != null && rightNumber != null) leftNumber == rightNumber else left == right
        }

        private fun ordered(left: RuntimeValue, right: RuntimeValue): Int {
            val leftNumber = left.numberOrNull()
            val rightNumber = right.numberOrNull()
            if (leftNumber != null && rightNumber != null) return leftNumber.compareTo(rightNumber)
            if (left is RuntimeValue.StringValue && right is RuntimeValue.StringValue) return left.value.compareTo(right.value)
            throw ExpressionException("Ordering requires two numeric values or two strings")
        }

        private fun length(value: RuntimeValue): Long = when (value) {
            is RuntimeValue.StringValue -> value.value.length.toLong()
            is RuntimeValue.ListValue -> value.values.size.toLong()
            is RuntimeValue.ObjectValue -> value.values.size.toLong()
            else -> throw ExpressionException("length requires String, List or Object")
        }

        private fun contains(container: RuntimeValue, needle: RuntimeValue): Boolean = when (container) {
            is RuntimeValue.StringValue -> container.value.contains(asString(needle))
            is RuntimeValue.ListValue -> container.values.any { equal(it, needle) }
            is RuntimeValue.ObjectValue -> needle.let { it as? RuntimeValue.StringValue }?.value in container.values
            else -> throw ExpressionException("contains requires String, List or Object container")
        }

        private fun parseNumber(token: Token): RuntimeValue {
            return if ('.' in token.lexeme) {
                RuntimeValue.DoubleValue(token.lexeme.toDouble())
            } else {
                token.lexeme.toIntOrNull()?.let(RuntimeValue::IntValue)
                    ?: token.lexeme.toLongOrNull()?.let(RuntimeValue::LongValue)
                    ?: throw ExpressionException("Invalid numeric literal '${token.lexeme}'")
            }
        }

        private fun oneArgument(name: Token, arguments: List<RuntimeValue>): RuntimeValue {
            if (arguments.size != 1) throw ExpressionException("${name.lexeme} expects one argument")
            return arguments.single()
        }

        private fun twoArguments(name: Token, arguments: List<RuntimeValue>): List<RuntimeValue> {
            if (arguments.size != 2) throw ExpressionException("${name.lexeme} expects two arguments")
            return arguments
        }

        private fun asBoolean(value: RuntimeValue): Boolean =
            (value as? RuntimeValue.BooleanValue)?.value
                ?: throw ExpressionException("Expected Boolean, got ${value.typeName()}")

        private fun asString(value: RuntimeValue): String =
            (value as? RuntimeValue.StringValue)?.value
                ?: throw ExpressionException("Expected String, got ${value.typeName()}")

        private fun current(): Token = tokens[cursor]
        private fun advance(): Token = tokens[cursor++]
        private fun match(type: TokenType): Boolean = if (current().type == type) {
            cursor++
            true
        } else {
            false
        }
        private fun expect(type: TokenType, message: String) {
            if (!match(type)) throw ExpressionException("$message at position ${current().offset}")
        }
    }

    private fun RuntimeValue.numberOrNull(): Double? = when (this) {
        is RuntimeValue.IntValue -> value.toDouble()
        is RuntimeValue.LongValue -> value.toDouble()
        is RuntimeValue.DoubleValue -> value
        else -> null
    }

    private fun RuntimeValue.typeName(): String = when (this) {
        RuntimeValue.NullValue -> "Null"
        is RuntimeValue.StringValue -> "String"
        is RuntimeValue.BooleanValue -> "Boolean"
        is RuntimeValue.IntValue -> "Int"
        is RuntimeValue.LongValue -> "Long"
        is RuntimeValue.DoubleValue -> "Double"
        is RuntimeValue.ListValue -> "List"
        is RuntimeValue.ObjectValue -> "Object"
    }

    private val COMPARISON_TOKENS = setOf(
        TokenType.EQUALS,
        TokenType.NOT_EQUALS,
        TokenType.GREATER,
        TokenType.LESS,
        TokenType.GREATER_OR_EQUALS,
        TokenType.LESS_OR_EQUALS,
        TokenType.CONTAINS,
        TokenType.STARTS_WITH,
        TokenType.ENDS_WITH
    )
}
