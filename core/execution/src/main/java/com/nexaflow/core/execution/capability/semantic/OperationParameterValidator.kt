package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.CapabilityParameterSpec
import com.nexaflow.domain.capability.CapabilityParameterType
import com.nexaflow.domain.capability.operation.OperationSpec

/**
 * The single validation point for semantic operation parameters (roadmap
 * NF-P0-003). Presence-only checks let a malformed value — `"enabled" to
 * "TRUE "`, `"value" to "999999"`, `"packageName" to "com example"` — reach a
 * strategy and become a silent transport failure; this validator rejects
 * them all before any dispatch, with an explainable per-parameter reason.
 *
 * Validation is total per [CapabilityParameterType]: every type declares
 * exactly what a valid value looks like, so adding a type forces adding its
 * rule here (the exhaustive `when` fails compilation otherwise).
 */
object OperationParameterValidator {

    /** One rejected parameter, safe for history and diagnostics. */
    data class Violation(val parameter: String, val reason: String)

    /**
     * Validates [parameters] against the spec's schema. Returns the list of
     * violations in spec order (empty when valid). Order is deterministic so
     * tests and history stay stable.
     */
    fun validate(spec: OperationSpec, parameters: Map<String, String>): List<Violation> {
        val violations = mutableListOf<Violation>()

        // 1. Required parameters must be present and non-blank.
        // Track these so a blank required value reports one precise violation,
        // not both "missing" and a second type/grammar error.
        val missingOrBlank = mutableSetOf<String>()
        for (param in spec.parameters) {
            if (!param.required) continue
            val value = parameters[param.name]
            if (value == null || value.isBlank()) {
                missingOrBlank += param.name
                violations += Violation(
                    param.name,
                    "Missing required parameter '${param.name}' for ${spec.id.name}"
                )
            }
        }

        // 2. Every supplied parameter must be declared. Unknown keys have no
        // spec order, so sort them for deterministic diagnostics.
        parameters.keys
            .filter { spec.parameterSchema(it) == null }
            .sorted()
            .forEach { name ->
                violations += Violation(name, "Unknown parameter '$name' for ${spec.id.name}")
            }

        // 3. Full typed validation of every supplied value in spec order.
        for (param in spec.parameters) {
            val value = parameters[param.name] ?: continue
            if (param.name in missingOrBlank) continue
            validateValue(param, value)?.let { violations += Violation(param.name, it) }
        }
        return violations
    }

    /** Convenience gate: null when valid, otherwise the first violation reason. */
    fun firstViolation(spec: OperationSpec, parameters: Map<String, String>): String? =
        validate(spec, parameters).firstOrNull()?.reason

    private fun validateValue(param: CapabilityParameterSpec, value: String): String? {
        if (value.length > param.maximumLength) {
            return "Parameter '${param.name}' exceeds its maximum length of ${param.maximumLength}"
        }
        return when (param.type) {
            CapabilityParameterType.BOOLEAN -> validateBoolean(param, value)
            CapabilityParameterType.INTEGER -> validateInteger(param, value)
            CapabilityParameterType.PACKAGE_NAME -> validatePackageName(param, value)
            CapabilityParameterType.STRING -> validateAllowedValues(param, value)
            CapabilityParameterType.HTTPS_URL -> validateHttpsUrl(param, value)
            CapabilityParameterType.CONTENT_URI -> validateContentUri(param, value)
            CapabilityParameterType.OPAQUE_REFERENCE -> validateOpaqueReference(param, value)
        }
    }

    private fun validateBoolean(param: CapabilityParameterSpec, value: String): String? {
        // Strict: only the two canonical spellings (plus the historical 1/0
        // wire form used by persisted configs). "TRUE ", "yes", "on" fail.
        if (value == "true" || value == "false" || value == "1" || value == "0") {
            return null
        }
        return "Parameter '${param.name}' must be a boolean (true/false), got '$value'"
    }

    private fun validateInteger(param: CapabilityParameterSpec, value: String): String? {
        // Canonical digits only: toLongOrNull accepts forms like "+5" that
        // settings providers and users do not agree on; the strict grammar
        // here is an optional minus sign followed by ASCII digits.
        if (!Regex("-?[0-9]+").matches(value)) {
            return "Parameter '${param.name}' must be an integer, got '$value'"
        }
        val parsed = value.toLongOrNull()
            ?: return "Parameter '${param.name}' must be an integer, got '$value'"
        val min = param.minimumInteger
        if (min != null && parsed < min) {
            return "Parameter '${param.name}' must be >= $min, got $parsed"
        }
        val max = param.maximumInteger
        if (max != null && parsed > max) {
            return "Parameter '${param.name}' must be <= $max, got $parsed"
        }
        return null
    }

    private fun validatePackageName(param: CapabilityParameterSpec, value: String): String? {
        // The Android package-name grammar, enforced (not just suggested):
        // labels of [A-Za-z0-9_] joined by dots, each label starting with a
        // letter. Rejects shell metacharacters, spaces, leading digits and
        // "..", so a package name can never fragment into extra argv words.
        val label = "[A-Za-z][A-Za-z0-9_]*"
        val pattern = Regex("$label(\\.$label)*")
        if (!pattern.matches(value)) {
            return "Parameter '${param.name}' is not a valid package name"
        }
        return validateAllowedValues(param, value)
    }

    private fun validateHttpsUrl(param: CapabilityParameterSpec, value: String): String? {
        // Scheme-exact and host-required: no userinfo, no credentials in URL,
        // no other schemes sneaking through (http/file/javascript…).
        val parsed = runCatching { java.net.URI(value) }.getOrNull()
        when {
            parsed == null || parsed.scheme == null ->
                return "Parameter '${param.name}' must be an https:// URL"
            parsed.scheme.lowercase() != "https" ->
                return "Parameter '${param.name}' must use the https scheme"
            parsed.host.isNullOrBlank() ->
                return "Parameter '${param.name}' must include a host"
            parsed.userInfo != null ->
                return "Parameter '${param.name}' must not embed credentials"
        }
        return null
    }

    private fun validateContentUri(param: CapabilityParameterSpec, value: String): String? {
        // Only content:// provider URIs are acceptable; anything else (file://,
        // raw paths, intents) is rejected so the parameter cannot become a path
        // traversal vector.
        val parsed = runCatching { java.net.URI(value) }.getOrNull()
        if (parsed == null || !parsed.scheme.equals("content", ignoreCase = true) ||
            parsed.isOpaque || parsed.rawAuthority.isNullOrBlank() ||
            parsed.schemeSpecificPart.isNullOrBlank()
        ) {
            return "Parameter '${param.name}' must be a content:// URI with a provider authority"
        }
        return null
    }

    private fun validateOpaqueReference(param: CapabilityParameterSpec, value: String): String? {
        // Bounded, shell-safe token: printable, no whitespace, no metacharacters
        // that could fragment argv or inject quotes.
        if (value.isBlank() || value.any { it.isWhitespace() }) {
            return "Parameter '${param.name}' must be a single bounded token"
        }
        if (value.any { it in "\"'`$&|;<>(){}[]!\\*" }) {
            return "Parameter '${param.name}' contains forbidden characters"
        }
        return validateAllowedValues(param, value)
    }

    /** Shared allowlist check; empty list means unconstrained within the type. */
    private fun validateAllowedValues(param: CapabilityParameterSpec, value: String): String? {
        if (param.allowedValues.isNotEmpty() && value !in param.allowedValues) {
            return "Parameter '${param.name}' must be one of: ${param.allowedValues.joinToString()}"
        }
        return null
    }
}
