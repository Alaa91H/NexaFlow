package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.CapabilityIdempotency
import com.nexaflow.domain.capability.CapabilityParameterSpec
import com.nexaflow.domain.capability.CapabilityParameterType
import com.nexaflow.domain.capability.CapabilityRetrySafety
import com.nexaflow.domain.capability.CapabilitySideEffectLevel
import com.nexaflow.domain.capability.VerificationMode
import com.nexaflow.domain.capability.operation.DeviceFeature
import com.nexaflow.domain.capability.operation.OperationSpec
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NF-P0-003 contract tests: every [CapabilityParameterType] must enforce its
 * exact grammar (type, range, allowlist, length) at the single validation
 * point, so malformed values fail with INVALID_CONFIGURATION before any
 * strategy probe — never as a mysterious downstream transport failure.
 */
class OperationParameterValidatorTest {

    private fun spec(
        id: SemanticOperationId,
        parameters: List<CapabilityParameterSpec>,
        write: Boolean = true
    ) = OperationSpec(
        id = id,
        displayName = "test",
        parameters = parameters,
        idempotency = CapabilityIdempotency.IDEMPOTENT,
        retrySafety = CapabilityRetrySafety.SAFE,
        sideEffectLevel = if (write) CapabilitySideEffectLevel.REVERSIBLE else CapabilitySideEffectLevel.NONE,
        verificationMode = if (write) VerificationMode.REQUIRED else VerificationMode.NONE,
        strategies = listOf(StrategyId.ANDROID_PUBLIC_API)
    )

    private fun booleanSpec() = spec(
        SemanticOperationId.WIFI_SET_STATE,
        listOf(CapabilityParameterSpec("enabled", CapabilityParameterType.BOOLEAN, required = true))
    )

    private fun integerSpec(min: Long? = null, max: Long? = null) = spec(
        SemanticOperationId.BRIGHTNESS_SET,
        listOf(
            CapabilityParameterSpec(
                "value", CapabilityParameterType.INTEGER, required = true,
                minimumInteger = min, maximumInteger = max
            )
        )
    )

    private fun packageSpec() = spec(
        SemanticOperationId.PACKAGE_FORCE_STOP,
        listOf(CapabilityParameterSpec("packageName", CapabilityParameterType.PACKAGE_NAME, required = true))
    )

    private fun stringSpec(allowed: List<String> = emptyList(), maxLength: Int = 512) = spec(
        SemanticOperationId.WIFI_GET_STATE,
        listOf(
            CapabilityParameterSpec(
                "label", CapabilityParameterType.STRING, required = true,
                maximumLength = maxLength, allowedValues = allowed
            )
        ),
        write = false
    )

    private fun httpsSpec() = spec(
        SemanticOperationId.WIFI_GET_STATE,
        listOf(CapabilityParameterSpec("url", CapabilityParameterType.HTTPS_URL, required = true)),
        write = false
    )

    private fun contentUriSpec() = spec(
        SemanticOperationId.WIFI_GET_STATE,
        listOf(CapabilityParameterSpec("target", CapabilityParameterType.CONTENT_URI, required = true)),
        write = false
    )

    private fun opaqueSpec(allowed: List<String> = emptyList()) = spec(
        SemanticOperationId.WIFI_GET_STATE,
        listOf(
            CapabilityParameterSpec(
                "reference", CapabilityParameterType.OPAQUE_REFERENCE,
                required = true, allowedValues = allowed
            )
        ),
        write = false
    )

    // ---- required / unknown -------------------------------------------------

    @Test
    fun missingRequiredParameterIsRejected() {
        val violation = OperationParameterValidator.firstViolation(booleanSpec(), emptyMap())
        assertTrue(violation!!.contains("Missing required parameter 'enabled'"))
    }

    @Test
    fun blankRequiredParameterIsRejectedLikeMissing() {
        val violation = OperationParameterValidator.firstViolation(booleanSpec(), mapOf("enabled" to "  "))
        assertTrue(violation!!.contains("Missing required parameter 'enabled'"))
    }

    @Test
    fun unknownParameterIsRejected() {
        val violation = OperationParameterValidator.firstViolation(
            booleanSpec(), mapOf("enabled" to "true", "extra" to "x")
        )
        assertTrue(violation!!.contains("Unknown parameter 'extra'"))
    }

    @Test
    fun fullyValidRequestProducesNoViolations() {
        assertTrue(OperationParameterValidator.validate(booleanSpec(), mapOf("enabled" to "true")).isEmpty())
    }

    // ---- BOOLEAN ------------------------------------------------------------

    @Test
    fun booleanAcceptsCanonicalAndWireForms() {
        val s = booleanSpec()
        for (value in listOf("true", "false", "1", "0")) {
            assertNull(OperationParameterValidator.firstViolation(s, mapOf("enabled" to value)))
        }
    }

    @Test
    fun booleanRejectsNonCanonicalSpellings() {
        val s = booleanSpec()
        for (value in listOf("TRUE", "True", "yes", "on", "enabled=true", "true ")) {
            val violation = OperationParameterValidator.firstViolation(s, mapOf("enabled" to value))
            assertTrue("'$value' must be rejected", violation!!.contains("must be a boolean"))
        }
    }

    // ---- INTEGER ------------------------------------------------------------

    @Test
    fun integerAcceptsPlainValues() {
        assertNull(OperationParameterValidator.firstViolation(integerSpec(), mapOf("value" to "128")))
        assertNull(OperationParameterValidator.firstViolation(integerSpec(), mapOf("value" to "-5")))
    }

    @Test
    fun integerRejectsNonNumeric() {
        // Blank is already covered by the required-check; here every value is
        // non-blank but not a valid integer.
        for (value in listOf("abc", "12.5", "0x10", "1 2", "+5", "1_000")) {
            val violation = OperationParameterValidator.firstViolation(integerSpec(), mapOf("value" to value))
            assertTrue("'$value' must be rejected", violation!!.contains("must be an integer"))
        }
    }

    @Test
    fun integerEnforcesMinimumAndMaximum() {
        val s = integerSpec(min = 0, max = 255)
        assertTrue(
            OperationParameterValidator.firstViolation(s, mapOf("value" to "-1"))!!
                .contains("must be >= 0")
        )
        assertTrue(
            OperationParameterValidator.firstViolation(s, mapOf("value" to "256"))!!
                .contains("must be <= 255")
        )
        assertNull(OperationParameterValidator.firstViolation(s, mapOf("value" to "0")))
        assertNull(OperationParameterValidator.firstViolation(s, mapOf("value" to "255")))
    }

    // ---- PACKAGE_NAME -------------------------------------------------------

    @Test
    fun packageNameAcceptsStandardNames() {
        val s = packageSpec()
        for (value in listOf("com.example.app", "org.nexaflow.Core_2", "a.b")) {
            assertNull("'$value' must be accepted", OperationParameterValidator.firstViolation(s, mapOf("packageName" to value)))
        }
    }

    @Test
    fun packageNameRejectsInjectionAndMalformedNames() {
        val s = packageSpec()
        for (value in listOf(
            "com example", // space
            "com;rm", // shell metacharacter
            "com&&id",
            "com..example", // empty label
            ".com.example", // leading dot
            "com.example.", // trailing dot
            "1com.example", // label starts with a digit
            "com/ex../example", // path characters
            "\"com.example\""
        )) {
            val violation = OperationParameterValidator.firstViolation(s, mapOf("packageName" to value))
            assertTrue("'$value' must be rejected", violation!!.contains("not a valid package name"))
        }
    }

    // ---- STRING -------------------------------------------------------------

    @Test
    fun stringEnforcesMaximumLength() {
        val s = stringSpec(maxLength = 8)
        assertNull(OperationParameterValidator.firstViolation(s, mapOf("label" to "12345678")))
        assertTrue(
            OperationParameterValidator.firstViolation(s, mapOf("label" to "123456789"))!!
                .contains("maximum length of 8")
        )
    }

    @Test
    fun stringEnforcesAllowlistWhenPresent() {
        val s = stringSpec(allowed = listOf("low", "high"))
        assertNull(OperationParameterValidator.firstViolation(s, mapOf("label" to "low")))
        assertTrue(
            OperationParameterValidator.firstViolation(s, mapOf("label" to "medium"))!!
                .contains("must be one of")
        )
    }

    @Test
    fun stringWithoutAllowlistAcceptsArbitraryBoundedText() {
        val s = stringSpec()
        assertNull(OperationParameterValidator.firstViolation(s, mapOf("label" to "any text — even unicode")))
    }

    // ---- HTTPS_URL ----------------------------------------------------------

    @Test
    fun httpsUrlAcceptsWellFormedUrls() {
        val s = httpsSpec()
        assertNull(OperationParameterValidator.firstViolation(s, mapOf("url" to "https://example.com/path")))
        assertNull(OperationParameterValidator.firstViolation(s, mapOf("url" to "HTTPS://Example.COM")))
    }

    @Test
    fun httpsUrlRejectsOtherSchemesAndMalformedValues() {
        val s = httpsSpec()
        for (value in listOf(
            "http://example.com", // plaintext scheme
            "file:///etc/passwd",
            "javascript:alert(1)",
            "ftp://example.com",
            "example.com/path", // no scheme
            "https://", // no host
            "https://user:pass@example.com" // embedded credentials
        )) {
            val violation = OperationParameterValidator.firstViolation(s, mapOf("url" to value))
            assertTrue("'$value' must be rejected", violation != null)
        }
    }

    // ---- CONTENT_URI --------------------------------------------------------

    @Test
    fun contentUriAcceptsProviderUris() {
        val s = contentUriSpec()
        assertNull(
            OperationParameterValidator.firstViolation(
                s, mapOf("target" to "content://com.example.provider/docs/1")
            )
        )
    }

    @Test
    fun contentUriRejectsNonProviderSchemes() {
        val s = contentUriSpec()
        for (value in listOf(
            "file:///data/local/tmp",
            "/storage/emulated/0/x",
            "https://example.com",
            "content://"
        )) {
            val violation = OperationParameterValidator.firstViolation(s, mapOf("target" to value))
            assertTrue("'$value' must be rejected", violation!!.contains("content://"))
        }
    }

    // ---- OPAQUE_REFERENCE ---------------------------------------------------

    @Test
    fun opaqueReferenceAcceptsBoundedTokens() {
        val s = opaqueSpec()
        assertNull(OperationParameterValidator.firstViolation(s, mapOf("reference" to "device-123_ABC")))
    }

    @Test
    fun opaqueReferenceRejectsWhitespaceAndMetacharacters() {
        val s = opaqueSpec()
        for (value in listOf(
            "two words",
            "line\nbreak",
            "semi;colon",
            "quote\"inside",
            "dollar\$sign",
            "pipe|char",
            "back`tick"
        )) {
            val violation = OperationParameterValidator.firstViolation(s, mapOf("reference" to value))
            assertTrue("'$value' must be rejected", violation != null)
        }
    }

    @Test
    fun opaqueReferenceEnforcesAllowlistWhenPresent() {
        val s = opaqueSpec(allowed = listOf("slot-a", "slot-b"))
        assertNull(OperationParameterValidator.firstViolation(s, mapOf("reference" to "slot-a")))
        assertTrue(
            OperationParameterValidator.firstViolation(s, mapOf("reference" to "slot-c"))!!
                .contains("must be one of")
        )
    }

    // ---- router integration -------------------------------------------------

    @Test
    fun routerRejectsMalformedPackageBeforeAnyStrategyProbe() = kotlinx.coroutines.test.runTest {
        val strategy = TestRouters.CountingStrategy(
            setOf(SemanticOperationId.PACKAGE_FORCE_STOP),
            id = StrategyId.ROOT_SHELL
        )
        val router = TestRouters.router(strategy)
        val outcome = router.execute(
            TypedOperationRequest(
                operation = SemanticOperationId.PACKAGE_FORCE_STOP,
                parameters = mapOf("packageName" to "com; rm -rf /")
            )
        )
        assertEquals(CapabilityErrorCode.INVALID_CONFIGURATION, outcome.errorCode)
        assertEquals("No strategy may be probed for an invalid request", 0, strategy.availabilityProbes)
        assertEquals(0, strategy.executions)
    }

    @Test
    fun routerRejectsOutOfRangeIntegerBeforeAnyStrategyProbe() = kotlinx.coroutines.test.runTest {
        val strategy = TestRouters.CountingStrategy(setOf(SemanticOperationId.BRIGHTNESS_SET))
        val router = TestRouters.router(strategy)
        val outcome = router.execute(
            TypedOperationRequest(
                operation = SemanticOperationId.BRIGHTNESS_SET,
                parameters = mapOf("value" to "99999")
            )
        )
        assertEquals(CapabilityErrorCode.INVALID_CONFIGURATION, outcome.errorCode)
        assertEquals(0, strategy.executions)
    }

    @Test
    fun routerRejectsNonBooleanEnabledBeforeAnyStrategyProbe() = kotlinx.coroutines.test.runTest {
        val strategy = TestRouters.CountingStrategy(setOf(SemanticOperationId.WIFI_SET_STATE))
        val router = TestRouters.router(strategy)
        val outcome = router.execute(
            TypedOperationRequest(
                operation = SemanticOperationId.WIFI_SET_STATE,
                parameters = mapOf("enabled" to "TRUE ")
            )
        )
        assertEquals(CapabilityErrorCode.INVALID_CONFIGURATION, outcome.errorCode)
        assertEquals(0, strategy.executions)
    }

    @Test
    fun routerReportsAllViolationsTogether() = kotlinx.coroutines.test.runTest {
        val strategy = TestRouters.CountingStrategy(
            setOf(SemanticOperationId.PACKAGE_SET_ENABLED_STATE),
            id = StrategyId.ROOT_SHELL
        )
        val router = TestRouters.router(strategy)
        val outcome = router.execute(
            TypedOperationRequest(
                operation = SemanticOperationId.PACKAGE_SET_ENABLED_STATE,
                parameters = mapOf("packageName" to "bad name", "enabled" to "perhaps")
            )
        )
        assertEquals(CapabilityErrorCode.INVALID_CONFIGURATION, outcome.errorCode)
        assertTrue(outcome.message.contains("packageName"))
        assertTrue(outcome.message.contains("enabled"))
        assertEquals(0, strategy.executions)
    }

    @Test
    fun routerAcceptsFullyValidPackageRequest() = kotlinx.coroutines.test.runTest {
        val strategy = TestRouters.CountingStrategy(
            setOf(SemanticOperationId.PACKAGE_FORCE_STOP),
            id = StrategyId.ROOT_SHELL,
            // Root strategy is privileged: the request must opt in, and the
            // assertion below proves the valid request actually reached it.
            outcome = com.nexaflow.core.execution.capability.semantic.OperationOutcome(
                operation = SemanticOperationId.PACKAGE_FORCE_STOP,
                status = com.nexaflow.core.execution.capability.semantic.OperationOutcomeStatus.SUCCESS,
                strategy = StrategyId.ROOT_SHELL,
                message = "force-stopped"
            )
        )
        val router = TestRouters.router(strategy)
        val outcome = router.execute(
            TypedOperationRequest(
                operation = SemanticOperationId.PACKAGE_FORCE_STOP,
                parameters = mapOf("packageName" to "com.example.app"),
                allowPrivilegedStrategies = true
            )
        )
        assertFalse(outcome.errorCode == CapabilityErrorCode.INVALID_CONFIGURATION)
        assertEquals("A valid request must reach the strategy", 1, strategy.executions)
    }
}
