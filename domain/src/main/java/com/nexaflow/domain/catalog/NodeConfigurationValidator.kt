package com.nexaflow.domain.catalog

/**
 * Stable machine-readable validation codes. UI layers localize these codes;
 * execution/history must never embed sensitive config values in an issue.
 */
enum class NodeConfigIssueCode {
    MISSING_REQUIRED,
    UNKNOWN_KEY,
    INVALID_BOOLEAN,
    INVALID_INTEGER,
    INVALID_DECIMAL,
    INVALID_ENUM,
    OUT_OF_RANGE
}

data class NodeConfigValidationIssue(
    val key: String,
    val code: NodeConfigIssueCode
)

/**
 * Pure validator for the legacy Map<String,String> boundary.
 *
 * Expression-capable fields deliberately skip primitive validation while they
 * contain a runtime token. The expression engine is responsible for validating
 * the resolved value before execution; this validator only checks literal
 * editor/persistence values.
 */
object NodeConfigurationValidator {

    fun validate(
        schema: NodeConfigurationSchema,
        config: Map<String, String>
    ): List<NodeConfigValidationIssue> {
        val issues = mutableListOf<NodeConfigValidationIssue>()

        schema.fields.forEach { field ->
            val value = config[field.key]
            if (field.required && value.isNullOrBlank()) {
                issues += NodeConfigValidationIssue(field.key, NodeConfigIssueCode.MISSING_REQUIRED)
                return@forEach
            }
            if (value.isNullOrBlank() || isDynamic(field, value)) return@forEach

            issues += validateLiteral(field, value)
        }

        if (!schema.acceptsUnknownKeys) {
            (config.keys - schema.knownKeys).forEach { key ->
                issues += NodeConfigValidationIssue(key, NodeConfigIssueCode.UNKNOWN_KEY)
            }
        }

        return issues
    }

    private fun validateLiteral(
        field: NodeConfigField,
        value: String
    ): List<NodeConfigValidationIssue> {
        val issues = mutableListOf<NodeConfigValidationIssue>()
        when (field.valueType) {
            NodeConfigValueType.BOOLEAN -> {
                if (!value.equals("true", ignoreCase = true) &&
                    !value.equals("false", ignoreCase = true)
                ) {
                    issues += NodeConfigValidationIssue(field.key, NodeConfigIssueCode.INVALID_BOOLEAN)
                }
            }
            NodeConfigValueType.INTEGER -> {
                val number = value.toLongOrNull()
                if (number == null) {
                    issues += NodeConfigValidationIssue(field.key, NodeConfigIssueCode.INVALID_INTEGER)
                } else if (!withinRange(number.toDouble(), field)) {
                    issues += NodeConfigValidationIssue(field.key, NodeConfigIssueCode.OUT_OF_RANGE)
                }
            }
            NodeConfigValueType.DECIMAL,
            NodeConfigValueType.DURATION_SECONDS,
            NodeConfigValueType.COORDINATE -> {
                val number = value.toDoubleOrNull()
                if (number == null) {
                    issues += NodeConfigValidationIssue(field.key, NodeConfigIssueCode.INVALID_DECIMAL)
                } else if (!withinRange(number, field)) {
                    issues += NodeConfigValidationIssue(field.key, NodeConfigIssueCode.OUT_OF_RANGE)
                }
            }
            NodeConfigValueType.ENUM -> {
                if (value !in field.allowedValues) {
                    issues += NodeConfigValidationIssue(field.key, NodeConfigIssueCode.INVALID_ENUM)
                }
            }
            NodeConfigValueType.STRING,
            NodeConfigValueType.TIME,
            NodeConfigValueType.DATE,
            NodeConfigValueType.PACKAGE,
            NodeConfigValueType.URL,
            NodeConfigValueType.SECRET,
            NodeConfigValueType.JSON -> Unit
        }
        return issues
    }

    private fun withinRange(value: Double, field: NodeConfigField): Boolean {
        if (field.minValue != null && value < field.minValue) return false
        if (field.maxValue != null && value > field.maxValue) return false
        return true
    }

    /**
     * Supports both the existing %variable form and the planned {{expression}}
     * form without teaching the domain validator expression grammar.
     */
    private fun isDynamic(field: NodeConfigField, value: String): Boolean =
        field.expressionCapable && (
            value.contains('%') ||
                (value.contains("{{") && value.contains("}}"))
            )
}
