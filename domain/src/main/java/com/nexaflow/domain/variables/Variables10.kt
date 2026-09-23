package com.nexaflow.domain.variables

/**
 * Variables 1.0 contract (roadmap P0.3): typed declarations, deterministic
 * scope resolution and keystore-backed secret references, layered over the
 * existing [RuntimeVariable]/[VariableScope] models — no parallel system.
 *
 * The declaration is the authoring-time truth; the runtime ([ScopedDataRuntime]
 * in `core:execution`) keeps enforcing write scopes at run time. This file
 * adds the pieces the persisted document and the UI picker both need:
 * validation with stable error codes, resolution precedence as a single
 * testable function, and the secret-handle safety rules.
 */

/** A validated variable declaration used by the builder, documents and picker. */
data class VariableDeclaration(
    val name: String,
    val scope: VariableScope,
    /** Declared runtime type; null means inferred at first assignment. */
    val runtimeType: RuntimeValueType? = null,
    val defaultValue: RuntimeValue? = null,
    val description: String = "",
) {
    init {
        val problems = validate()
        require(problems.isEmpty()) {
            "Invalid variable declaration: " + problems.joinToString("; ") { it.code.name }
        }
    }

    /**
     * Full validation with stable machine-readable codes — surfaced by the
     * editor UI and enforced identically at import preflight.
     */
    fun validate(): List<VariableProblem> {
        val problems = mutableListOf<VariableProblem>()
        if (name.isBlank()) {
            problems += VariableProblem(VariableErrorCode.BLANK_NAME, "name")
            return problems
        }
        if (name.length > MAX_NAME_LENGTH) {
            problems += VariableProblem(VariableErrorCode.NAME_TOO_LONG, "name")
        }
        if (!NAME_REGEX.matches(name)) {
            problems += VariableProblem(VariableErrorCode.INVALID_NAME_CHARACTERS, "name")
        }
        if (defaultValue != null && runtimeType != null && !runtimeType.accepts(defaultValue)) {
            problems += VariableProblem(VariableErrorCode.DEFAULT_TYPE_MISMATCH, "defaultValue")
        }
        if (defaultValue is RuntimeValue.ObjectValue && defaultValue.values.size > MAX_OBJECT_ENTRIES) {
            problems += VariableProblem(VariableErrorCode.OBJECT_TOO_LARGE, "defaultValue")
        }
        return problems
    }

    companion object {
        const val MAX_NAME_LENGTH = 64
        const val MAX_OBJECT_ENTRIES = 256
        /** Letters, digits, underscore and dot — dot delimits namespaces like `device.battery`. */
        val NAME_REGEX = Regex("[A-Za-z][A-Za-z0-9_.]{0,63}")
    }
}

/** Stable validation error codes for declarations. */
enum class VariableErrorCode {
    BLANK_NAME,
    NAME_TOO_LONG,
    INVALID_NAME_CHARACTERS,
    DEFAULT_TYPE_MISMATCH,
    OBJECT_TOO_LARGE,
}

/** One declaration problem: which code, on which field. */
data class VariableProblem(val code: VariableErrorCode, val field: String)

/** Closed runtime type tags a declaration may pin. */
enum class RuntimeValueType {
    NULL, STRING, BOOLEAN, INT, LONG, DOUBLE, LIST, OBJECT, SECRET;

    /** Whether a concrete value satisfies this declared type. */
    fun accepts(value: RuntimeValue): Boolean = when (this) {
        NULL -> value is RuntimeValue.NullValue
        STRING -> value is RuntimeValue.StringValue
        BOOLEAN -> value is RuntimeValue.BooleanValue
        INT -> value is RuntimeValue.IntValue
        LONG -> value is RuntimeValue.LongValue || value is RuntimeValue.IntValue
        DOUBLE -> value is RuntimeValue.DoubleValue ||
            value is RuntimeValue.IntValue || value is RuntimeValue.LongValue
        LIST -> value is RuntimeValue.ListValue
        OBJECT -> value is RuntimeValue.ObjectValue
        SECRET -> value is RuntimeValue.StringValue // secret material is a string handle payload
    }
}

/**
 * Deterministic scope resolution precedence (the single truth both the
 * runtime and the debugger inspector must agree on):
 * ACTION → NODE → EXECUTION → WORKFLOW → GLOBAL.
 */
object VariableScopeResolution {

    /** Ordered narrowest-first. */
    val PRECEDENCE = listOf(
        VariableScope.ACTION,
        VariableScope.NODE,
        VariableScope.EXECUTION,
        VariableScope.WORKFLOW,
        VariableScope.GLOBAL,
    )

    /**
     * Resolves [name] across scoped layers. Layers are functions returning the
     * hit (or null); ACTION/NODE layers additionally key by their own id.
     * Pure, testable, and shared by execution and inspection paths.
     */
    fun resolve(
        name: String,
        actionLayer: (String) -> RuntimeVariable? = { null },
        nodeLayer: (String) -> RuntimeVariable? = { null },
        executionLayer: (String) -> RuntimeVariable? = { null },
        workflowLayer: (String) -> RuntimeVariable? = { null },
        globalLayer: (String) -> RuntimeVariable? = { null },
        actionId: String? = null,
        nodeId: String? = null,
    ): ScopedResolution? {
        if (actionId != null) actionLayer(name)?.let {
            return ScopedResolution(it, VariableScope.ACTION, actionId)
        }
        if (nodeId != null) nodeLayer(name)?.let {
            return ScopedResolution(it, VariableScope.NODE, nodeId)
        }
        executionLayer(name)?.let { return ScopedResolution(it, VariableScope.EXECUTION, null) }
        workflowLayer(name)?.let { return ScopedResolution(it, VariableScope.WORKFLOW, null) }
        globalLayer(name)?.let { return ScopedResolution(it, VariableScope.GLOBAL, null) }
        return null
    }
}

/** The variable found plus where it came from — needed by inspector UIs. */
data class ScopedResolution(
    val variable: RuntimeVariable,
    val scope: VariableScope,
    /** Owning action/node id for lexical scopes; null otherwise. */
    val ownerId: String?,
)

/**
 * Keystore-backed secret handles (NF-P0-024). The value stored anywhere
 * outside the vault is the [handle] only; material never enters documents,
 * backups, traces or AI prompts. Validation is structural — the vault owns
 * the cryptographic checks.
 */
object SecretReferenceRules {

    const val HANDLE_PREFIX = "vault:"
    const val MAX_HANDLE_LENGTH = 128

    /** Stable error codes for secret-handle violations. */
    enum class SecretErrorCode { BLANK_HANDLE, BAD_PREFIX, TOO_LONG, BAD_CHARACTERS }

    /**
     * Structural validation of a secret handle: `vault:<keystore-id>`.
     * Fails closed — an invalid handle is never treated as raw material.
     */
    fun validateHandle(handle: String): List<SecretErrorCode> {
        val problems = mutableListOf<SecretErrorCode>()
        if (handle.isBlank()) problems += SecretErrorCode.BLANK_HANDLE
        if (!handle.startsWith(HANDLE_PREFIX)) problems += SecretErrorCode.BAD_PREFIX
        if (handle.length > MAX_HANDLE_LENGTH) problems += SecretErrorCode.TOO_LONG
        if (handle.startsWith(HANDLE_PREFIX)) {
            val id = handle.removePrefix(HANDLE_PREFIX)
            if (!id.matches(Regex("[A-Za-z0-9._:-]{1,112}"))) problems += SecretErrorCode.BAD_CHARACTERS
        }
        return problems
    }

    /** True when [value] carries secret material that must be redacted in traces. */
    fun isSecretHandle(value: RuntimeValue?): Boolean =
        value is RuntimeValue.StringValue && value.value.startsWith(HANDLE_PREFIX)

    /**
     * Redaction rendering for any value that might be a secret handle — used
     * by the inspector and the trace exporter. Non-handles pass through.
     */
    fun redactIfHandle(value: RuntimeValue?): String = when {
        value == null -> "null"
        isSecretHandle(value) -> "$HANDLE_PREFIX[REDACTED]"
        else -> value.toString()
    }
}
