package com.nexaflow.core.execution.capability

import com.nexaflow.domain.capability.CapabilityDescriptor
import com.nexaflow.domain.capability.CapabilityParameterSpec
import com.nexaflow.domain.capability.CapabilityParameterType
import com.nexaflow.domain.capability.CapabilityRequest

/** Machine-readable validation error; messages contain only parameter names, never values. */
data class CapabilityValidationIssue(
    val parameter: String?,
    val code: CapabilityValidationCode,
    val message: String
)

enum class CapabilityValidationCode {
    UNKNOWN_CAPABILITY,
    TOO_MANY_PARAMETERS,
    UNKNOWN_PARAMETER,
    REQUIRED_PARAMETER_MISSING,
    VALUE_TOO_LONG,
    CONTROL_CHARACTER,
    TYPE_MISMATCH,
    VALUE_NOT_ALLOWED,
    INTEGER_OUT_OF_RANGE
}

data class CapabilityValidationResult(val issues: List<CapabilityValidationIssue>) {
    val isValid: Boolean get() = issues.isEmpty()
}

/**
 * Deterministic request boundary. It does not inspect Android or execute any
 * operation; its only job is to keep backend APIs free of arbitrary strings.
 */
object CapabilityRequestValidator {
    private const val MAX_PARAMETERS = 32
    private val packageName = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
    /** Non-secret backend handle such as a persisted plug-in instance id, never a Bundle or command. */
    private val opaqueReference = Regex("[A-Za-z][A-Za-z0-9_.:/-]{0,191}")

    fun validate(descriptor: CapabilityDescriptor?, request: CapabilityRequest): CapabilityValidationResult {
        if (descriptor == null) {
            return CapabilityValidationResult(
                listOf(CapabilityValidationIssue(null, CapabilityValidationCode.UNKNOWN_CAPABILITY, "Capability is not registered"))
            )
        }
        val issues = mutableListOf<CapabilityValidationIssue>()
        if (request.parameters.size > MAX_PARAMETERS) {
            issues += CapabilityValidationIssue(null, CapabilityValidationCode.TOO_MANY_PARAMETERS, "Too many capability parameters")
        }
        val specs = descriptor.parameters.associateBy { it.name }
        request.parameters.forEach { (name, value) ->
            val spec = specs[name]
            if (spec == null) {
                issues += CapabilityValidationIssue(name, CapabilityValidationCode.UNKNOWN_PARAMETER, "Parameter is not allowed")
            } else {
                validateValue(spec, value, issues)
            }
        }
        descriptor.parameters.filter { it.required && it.name !in request.parameters }.forEach { spec ->
            issues += CapabilityValidationIssue(spec.name, CapabilityValidationCode.REQUIRED_PARAMETER_MISSING, "Required parameter is missing")
        }
        return CapabilityValidationResult(issues)
    }

    private fun validateValue(
        spec: CapabilityParameterSpec,
        value: String,
        issues: MutableList<CapabilityValidationIssue>
    ) {
        if (value.length > spec.maximumLength) {
            issues += CapabilityValidationIssue(spec.name, CapabilityValidationCode.VALUE_TOO_LONG, "Parameter value exceeds allowed length")
            return
        }
        if (value.any { it.isISOControl() }) {
            issues += CapabilityValidationIssue(spec.name, CapabilityValidationCode.CONTROL_CHARACTER, "Parameter contains a control character")
            return
        }
        val typeValid = when (spec.type) {
            CapabilityParameterType.STRING -> true
            CapabilityParameterType.BOOLEAN -> value == "true" || value == "false"
            CapabilityParameterType.INTEGER -> value.toLongOrNull() != null
            CapabilityParameterType.PACKAGE_NAME -> packageName.matches(value)
            CapabilityParameterType.HTTPS_URL -> value.startsWith("https://") && value.length > "https://".length
            CapabilityParameterType.CONTENT_URI -> value.startsWith("content://") && value.length > "content://".length
            CapabilityParameterType.OPAQUE_REFERENCE -> opaqueReference.matches(value)
        }
        if (!typeValid) {
            issues += CapabilityValidationIssue(spec.name, CapabilityValidationCode.TYPE_MISMATCH, "Parameter has an invalid ${spec.type} value")
            return
        }
        if (spec.allowedValues.isNotEmpty() && value !in spec.allowedValues) {
            issues += CapabilityValidationIssue(spec.name, CapabilityValidationCode.VALUE_NOT_ALLOWED, "Parameter value is not in the allowed set")
        }
        if (spec.type == CapabilityParameterType.INTEGER) {
            val numeric = value.toLongOrNull() ?: return
            val minimum = spec.minimumInteger
            val maximum = spec.maximumInteger
            if ((minimum != null && numeric < minimum) || (maximum != null && numeric > maximum)) {
                issues += CapabilityValidationIssue(spec.name, CapabilityValidationCode.INTEGER_OUT_OF_RANGE, "Integer parameter is outside the allowed range")
            }
        }
    }
}
