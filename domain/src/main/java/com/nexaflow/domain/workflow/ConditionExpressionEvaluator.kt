package com.nexaflow.domain.workflow

import java.util.Locale

/**
 * Safe, deterministic, bounded boolean expression evaluator for NexaFlow workflows.
 *
 * Supports conditional branching, per-action conditional gating, and filter expressions
 * without arbitrary code execution or reflection.
 *
 * Syntax examples:
 * - `%BATTERY < 20`
 * - `%WIFI_STATE == "CONNECTED"`
 * - `%CTX.$.fetch.status == 200 && %CTX.$.fetch.body contains "ok"`
 * - `isEmpty(%CALLER_NAME) || %CALLER_NUMBER startsWith "+1"`
 * - `!isEmpty(%TEXT)`
 */
object ConditionExpressionEvaluator {

    private const val MAX_EXPRESSION_LENGTH = 2048

    /**
     * Evaluates [expression] to a boolean value.
     * Returns true when the expression is blank or empty (default pass-through).
     * Throws [IllegalArgumentException] on invalid syntax when [throwOnError] is true,
     * or returns false otherwise.
     */
    fun evaluate(expression: String, throwOnError: Boolean = false): Boolean {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.length > MAX_EXPRESSION_LENGTH) {
            if (throwOnError) throw IllegalArgumentException("Expression exceeds maximum length of $MAX_EXPRESSION_LENGTH")
            return false
        }
        return try {
            evalOr(trimmed)
        } catch (e: Exception) {
            if (throwOnError) throw e
            false
        }
    }

    private fun evalOr(expr: String): Boolean {
        val tokens = splitByTopLevelOperator(expr, listOf("||", " OR ", " or "))
        if (tokens.size > 1) {
            return tokens.any { evalAnd(it) }
        }
        return evalAnd(expr)
    }

    private fun evalAnd(expr: String): Boolean {
        val tokens = splitByTopLevelOperator(expr, listOf("&&", " AND ", " and "))
        if (tokens.size > 1) {
            return tokens.all { evalNot(it) }
        }
        return evalNot(expr)
    }

    private fun evalNot(expr: String): Boolean {
        val trimmed = expr.trim()
        if (trimmed.startsWith("!") && !trimmed.startsWith("!=")) {
            return !evalNot(trimmed.substring(1))
        }
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.startsWith("not ") && lower.length > 4) {
            return !evalNot(trimmed.substring(4))
        }
        return evalComparisonOrPrimary(trimmed)
    }

    private fun evalComparisonOrPrimary(expr: String): Boolean {
        var trimmed = expr.trim()
        // Strip surrounding parentheses if they wrap the entire expression
        if (trimmed.startsWith("(") && trimmed.endsWith(")") && isBalanced(trimmed.substring(1, trimmed.length - 1))) {
            return evalOr(trimmed.substring(1, trimmed.length - 1))
        }

        // Functions
        if (trimmed.startsWith("isEmpty(", ignoreCase = true) && trimmed.endsWith(")")) {
            val inner = trimmed.substring(8, trimmed.length - 1).trim()
            val value = unquote(inner)
            return value.isEmpty()
        }
        if (trimmed.startsWith("isNotEmpty(", ignoreCase = true) && trimmed.endsWith(")")) {
            val inner = trimmed.substring(11, trimmed.length - 1).trim()
            val value = unquote(inner)
            return value.isNotEmpty()
        }

        // Binary comparison operators
        val operators = listOf(
            "==", "!=", "<=", ">=", "<", ">",
            " contains ", " CONTAINS ",
            " startsWith ", " STARTSWITH ",
            " endsWith ", " ENDSWITH ",
            " matches ", " MATCHES "
        )

        for (op in operators) {
            val parts = splitByTopLevelBinaryOp(trimmed, op)
            if (parts != null) {
                val (leftStr, rightStr) = parts
                val left = unquote(leftStr.trim())
                val right = unquote(rightStr.trim())
                return compare(left, op.trim().lowercase(Locale.ROOT), right)
            }
        }

        // Single literal or boolean evaluation
        return parseBooleanTruthiness(unquote(trimmed))
    }

    private fun compare(left: String, op: String, right: String): Boolean {
        val leftNum = left.toDoubleOrNull()
        val rightNum = right.toDoubleOrNull()

        if (leftNum != null && rightNum != null) {
            return when (op) {
                "==" -> leftNum == rightNum
                "!=" -> leftNum != rightNum
                "<" -> leftNum < rightNum
                "<=" -> leftNum <= rightNum
                ">" -> leftNum > rightNum
                ">=" -> leftNum >= rightNum
                else -> compareStrings(left, op, right)
            }
        }
        return compareStrings(left, op, right)
    }

    private fun compareStrings(left: String, op: String, right: String): Boolean {
        return when (op) {
            "==" -> left.equals(right, ignoreCase = false)
            "!=" -> !left.equals(right, ignoreCase = false)
            "<" -> left < right
            "<=" -> left <= right
            ">" -> left > right
            ">=" -> left >= right
            "contains" -> left.contains(right, ignoreCase = false)
            "startswith" -> left.startsWith(right, ignoreCase = false)
            "endswith" -> left.endsWith(right, ignoreCase = false)
            "matches" -> runCatching { Regex(right).containsMatchIn(left) }.getOrDefault(false)
            else -> false
        }
    }

    private fun parseBooleanTruthiness(value: String): Boolean {
        return when (value.trim().lowercase(Locale.ROOT)) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off", "" -> false
            else -> value.isNotBlank()
        }
    }

    private fun unquote(str: String): String {
        val trimmed = str.trim()
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))
        ) {
            if (trimmed.length >= 2) {
                return trimmed.substring(1, trimmed.length - 1)
            }
        }
        return trimmed
    }

    private fun isBalanced(str: String): Boolean {
        var depth = 0
        for (ch in str) {
            if (ch == '(') depth++
            else if (ch == ')') {
                depth--
                if (depth < 0) return false
            }
        }
        return depth == 0
    }

    private fun splitByTopLevelOperator(expr: String, candidateOps: List<String>): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var inQuotes = false
        var quoteChar = ' '
        val current = StringBuilder()
        var i = 0

        while (i < expr.length) {
            val ch = expr[i]
            if ((ch == '"' || ch == '\'') && (i == 0 || expr[i - 1] != '\\')) {
                if (inQuotes && ch == quoteChar) {
                    inQuotes = false
                } else if (!inQuotes) {
                    inQuotes = true
                    quoteChar = ch
                }
            } else if (!inQuotes) {
                if (ch == '(') depth++
                else if (ch == ')') depth--
            }

            if (!inQuotes && depth == 0) {
                var matchedOp: String? = null
                for (op in candidateOps) {
                    if (expr.startsWith(op, i)) {
                        matchedOp = op
                        break
                    }
                }
                if (matchedOp != null) {
                    parts.add(current.toString())
                    current.setLength(0)
                    i += matchedOp.length
                    continue
                }
            }
            current.append(ch)
            i++
        }
        if (current.isNotEmpty()) {
            parts.add(current.toString())
        }
        return if (parts.size > 1) parts else listOf(expr)
    }

    private fun splitByTopLevelBinaryOp(expr: String, op: String): Pair<String, String>? {
        var depth = 0
        var inQuotes = false
        var quoteChar = ' '
        var i = 0

        while (i < expr.length) {
            val ch = expr[i]
            if ((ch == '"' || ch == '\'') && (i == 0 || expr[i - 1] != '\\')) {
                if (inQuotes && ch == quoteChar) {
                    inQuotes = false
                } else if (!inQuotes) {
                    inQuotes = true
                    quoteChar = ch
                }
            } else if (!inQuotes) {
                if (ch == '(') depth++
                else if (ch == ')') depth--
            }

            if (!inQuotes && depth == 0 && expr.startsWith(op, i)) {
                val left = expr.substring(0, i)
                val right = expr.substring(i + op.length)
                return left to right
            }
            i++
        }
        return null
    }
}
