package com.nexaflow.domain.variables

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the Variables 1.0 domain (roadmap P0.3): typed
 * declarations with stable error codes, deterministic scope resolution
 * precedence shared by execution and inspection, and fail-closed secret
 * handle rules.
 */
class Variables10Test {

    // ------------------------------------------------------------------
    // Declaration validation
    // ------------------------------------------------------------------

    @Test
    fun validDeclarationPasses() {
        val declaration = VariableDeclaration(
            name = "car.volume",
            scope = VariableScope.WORKFLOW,
            runtimeType = RuntimeValueType.INT,
            defaultValue = RuntimeValue.IntValue(70),
        )
        assertTrue(declaration.validate().isEmpty())
    }

    @Test
    fun blankNameRejected() {
        val error = runCatching {
            VariableDeclaration(name = " ", scope = VariableScope.GLOBAL)
        }.exceptionOrNull()
        assertEquals(VariableErrorCode.BLANK_NAME, firstCode(error))
    }

    @Test
    fun invalidNameCharactersRejected() {
        val error = runCatching {
            VariableDeclaration(name = "my var!", scope = VariableScope.GLOBAL)
        }.exceptionOrNull()
        assertEquals(VariableErrorCode.INVALID_NAME_CHARACTERS, firstCode(error))
    }

    @Test
    fun nameTooLongRejected() {
        val error = runCatching {
            VariableDeclaration(name = "a".repeat(65), scope = VariableScope.GLOBAL)
        }.exceptionOrNull()
        assertTrue(error?.message?.contains(VariableErrorCode.NAME_TOO_LONG.name) == true)
    }

    @Test
    fun defaultTypeMismatchRejected() {
        val error = runCatching {
            VariableDeclaration(
                name = "level",
                scope = VariableScope.EXECUTION,
                runtimeType = RuntimeValueType.INT,
                defaultValue = RuntimeValue.StringValue("high"),
            )
        }.exceptionOrNull()
        assertEquals(VariableErrorCode.DEFAULT_TYPE_MISMATCH, firstCode(error))
    }

    /** Constructor failures list codes in the message; grab the first. */
    private fun firstCode(error: Throwable?): VariableErrorCode? =
        VariableErrorCode.entries.firstOrNull { error?.message?.contains(it.name) == true }

    @Test
    fun numericWideningAccepted() {
        assertTrue(
            VariableDeclaration(
                name = "d",
                scope = VariableScope.EXECUTION,
                runtimeType = RuntimeValueType.DOUBLE,
                defaultValue = RuntimeValue.IntValue(3),
            ).validate().isEmpty()
        )
        assertTrue(
            VariableDeclaration(
                name = "l",
                scope = VariableScope.EXECUTION,
                runtimeType = RuntimeValueType.LONG,
                defaultValue = RuntimeValue.IntValue(3),
            ).validate().isEmpty()
        )
    }

    @Test
    fun secretDeclarationAcceptsOnlyVaultHandles() {
        val declaration = VariableDeclaration(
            name = "api.token",
            scope = VariableScope.WORKFLOW,
            runtimeType = RuntimeValueType.SECRET,
            defaultValue = RuntimeValue.StringValue("vault:workflow-api-token"),
        )
        assertTrue(declaration.validate().isEmpty())
    }

    @Test
    fun secretDeclarationRejectsPlaintextDefault() {
        val error = runCatching {
            VariableDeclaration(
                name = "api.token",
                scope = VariableScope.WORKFLOW,
                runtimeType = RuntimeValueType.SECRET,
                defaultValue = RuntimeValue.StringValue("raw-secret-value"),
            )
        }.exceptionOrNull()
        assertEquals(VariableErrorCode.INVALID_SECRET_HANDLE, firstCode(error))
    }

    // ------------------------------------------------------------------
    // Scope resolution precedence
    // ------------------------------------------------------------------

    private fun variable(scope: VariableScope, value: String) = RuntimeVariable(
        name = "x",
        value = RuntimeValue.StringValue(value),
        scope = scope,
    )

    @Test
    fun resolutionPrecedenceIsActionFirst() {
        val resolution = VariableScopeResolution.resolve(
            name = "x",
            actionLayer = { variable(VariableScope.ACTION, "from-action") },
            nodeLayer = { variable(VariableScope.NODE, "from-node") },
            executionLayer = { variable(VariableScope.EXECUTION, "from-exec") },
            workflowLayer = { variable(VariableScope.WORKFLOW, "from-wf") },
            globalLayer = { variable(VariableScope.GLOBAL, "from-global") },
            actionId = "a1",
            nodeId = "n1",
        )!!
        assertEquals(VariableScope.ACTION, resolution.scope)
        assertEquals("from-action", (resolution.variable.value as RuntimeValue.StringValue).value)
    }

    @Test
    fun lexicalLayersRequireTheirOwnerId() {
        // No actionId: ACTION layer skipped even though it would hit.
        val resolution = VariableScopeResolution.resolve(
            name = "x",
            actionLayer = { variable(VariableScope.ACTION, "from-action") },
            nodeLayer = { variable(VariableScope.NODE, "from-node") },
            nodeId = "n1",
        )!!
        assertEquals(VariableScope.NODE, resolution.scope)
    }

    @Test
    fun fallbackCascadesDownToGlobal() {
        val resolution = VariableScopeResolution.resolve(
            name = "x",
            globalLayer = { variable(VariableScope.GLOBAL, "from-global") },
        )!!
        assertEquals(VariableScope.GLOBAL, resolution.scope)
        assertEquals(null, resolution.ownerId)
    }

    @Test
    fun unresolvedNameReturnsNull() {
        assertEquals(null, VariableScopeResolution.resolve(name = "missing"))
    }

    @Test
    fun precedenceOrderIsNarrowestFirst() {
        assertEquals(
            listOf(
                VariableScope.ACTION,
                VariableScope.NODE,
                VariableScope.EXECUTION,
                VariableScope.WORKFLOW,
                VariableScope.GLOBAL,
            ),
            VariableScopeResolution.PRECEDENCE,
        )
    }

    // ------------------------------------------------------------------
    // Secret handles
    // ------------------------------------------------------------------

    @Test
    fun validHandlePassesStructuralValidation() {
        assertTrue(SecretReferenceRules.validateHandle("vault:keystore-1").isEmpty())
    }

    @Test
    fun handleViolationsCarryStableCodes() {
        assertEquals(
            SecretReferenceRules.SecretErrorCode.BAD_PREFIX,
            SecretReferenceRules.validateHandle("raw-material").first(),
        )
        assertTrue(
            SecretReferenceRules.validateHandle("").contains(
                SecretReferenceRules.SecretErrorCode.BLANK_HANDLE
            )
        )
        assertTrue(
            SecretReferenceRules.validateHandle("vault:bad chars!").contains(
                SecretReferenceRules.SecretErrorCode.BAD_CHARACTERS
            )
        )
    }

    @Test
    fun secretHandlesDetectedAndRedacted() {
        val handle = RuntimeValue.StringValue("vault:keystore-1")
        val plain = RuntimeValue.StringValue("hello")
        assertTrue(SecretReferenceRules.isSecretHandle(handle))
        assertTrue(!SecretReferenceRules.isSecretHandle(plain))
        assertEquals("vault:[REDACTED]", SecretReferenceRules.redactIfHandle(handle))
        assertEquals("hello", SecretReferenceRules.redactIfHandle(plain).removePrefix("StringValue(value=").removeSuffix(")"))
    }
}
