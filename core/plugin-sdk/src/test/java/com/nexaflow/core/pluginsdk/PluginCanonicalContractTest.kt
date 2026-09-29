package com.nexaflow.core.pluginsdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T31 — Canonical plugin SDK contract tests: the typed surface the canonical
 * platform uses to observe plugin events and invoke plugin actions fails
 * closed and matches the canonical AST identities exactly.
 */
class PluginCanonicalContractTest {

    private fun host(
        lifecycleActive: Boolean = true,
        trustGranted: Boolean = true,
    ) = PluginCanonicalContract.HostState(
        lifecycleActive = lifecycleActive,
        trustGranted = trustGranted,
    )

    private val schema = PluginCanonicalContract.PayloadSchema(
        listOf(
            PluginCanonicalContract.PayloadSlot("apiKey", PluginCanonicalContract.PayloadKind.STRING, required = true),
            PluginCanonicalContract.PayloadSlot("retries", PluginCanonicalContract.PayloadKind.INTEGER),
        ),
    )

    // ------------------------------------------------------------------
    // Canonical identity constants
    // ------------------------------------------------------------------

    @Test
    fun canonicalIdentitiesMatchThePinnedTableEntries() {
        assertEquals("plugin.event", PluginCanonicalContract.TARGET_EVENT)
        assertEquals("plugin.action", PluginCanonicalContract.TARGET_ACTION)
        assertEquals(
            "core.predicate.match_event_filter",
            PluginCanonicalContract.PREDICATE_MATCH_EVENT_FILTER,
        )
        assertEquals("core.operation.invoke", PluginCanonicalContract.OPERATION_INVOKE)
        assertEquals("pluginId", PluginCanonicalContract.ARG_PLUGIN_ID)
    }

    // ------------------------------------------------------------------
    // Event matching
    // ------------------------------------------------------------------

    @Test
    fun eventMatchesOnPluginIdAndExactFilterEntries() {
        val event = PluginCanonicalContract.PluginEvent(
            pluginId = "com.example.plugin",
            eventPayload = mapOf("beacon" to "kitchen", "rssi" to "-60"),
        )

        assertTrue(
            PluginCanonicalContract.eventMatches(
                filterPluginId = "com.example.plugin",
                filterPayload = mapOf("beacon" to "kitchen"),
                event = event,
            ),
        )
        // Wildcard: no filter entries beyond the plugin id.
        assertTrue(
            PluginCanonicalContract.eventMatches("com.example.plugin", emptyMap(), event),
        )
        // Exact entry mismatch refuses.
        assertFalse(
            PluginCanonicalContract.eventMatches(
                filterPluginId = "com.example.plugin",
                filterPayload = mapOf("beacon" to "garage"),
                event = event,
            ),
        )
        // Plugin id mismatch refuses.
        assertFalse(
            PluginCanonicalContract.eventMatches("com.other.plugin", emptyMap(), event),
        )
    }

    @Test
    fun eventMatcherRefusesInvalidFilterIdsInsteadOfThrowing() {
        assertFalse(
            PluginCanonicalContract.eventMatches(
                filterPluginId = "bad id with spaces",
                filterPayload = emptyMap(),
                event = PluginCanonicalContract.PluginEvent("com.example.plugin"),
            ),
        )
    }

    @Test
    fun eventConstructorRejectsInvalidPluginIds() {
        try {
            PluginCanonicalContract.PluginEvent(pluginId = "")
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("plugin id"))
        }
    }

    // ------------------------------------------------------------------
    // Payload validation
    // ------------------------------------------------------------------

    @Test
    fun validPayloadIsAccepted() {
        assertEquals(
            PluginCanonicalContract.CheckResult.Accepted,
            PluginCanonicalContract.validatePayload(
                schema = schema,
                payload = mapOf("apiKey" to "k-123", "retries" to "3"),
            ),
        )
    }

    @Test
    fun unknownAndMissingSlotsAreTypedRefusals() {
        val result = PluginCanonicalContract.validatePayload(
            schema = schema,
            payload = mapOf("mystery" to "x"),
        ) as PluginCanonicalContract.CheckResult.Refused

        assertTrue(
            PluginCanonicalContract.RefusalReason.UNKNOWN_SLOT in result.reasons,
        )
        assertTrue(
            PluginCanonicalContract.RefusalReason.MISSING_REQUIRED_SLOT in result.reasons,
        )
    }

    @Test
    fun slotLengthOverflowIsATypedRefusal() {
        val result = PluginCanonicalContract.validatePayload(
            schema = schema,
            payload = mapOf("apiKey" to "x".repeat(600)),
        ) as PluginCanonicalContract.CheckResult.Refused

        assertTrue(
            PluginCanonicalContract.RefusalReason.SLOT_LENGTH_OVERFLOW in result.reasons,
        )
    }

    @Test
    fun oversizedPayloadEntryCountIsATypedRefusal() {
        val huge = (1..40).associate { "slot$it" to "x" }
        val result = PluginCanonicalContract.validatePayload(
            schema = PluginCanonicalContract.PayloadSchema(emptyList()),
            payload = huge,
        ) as PluginCanonicalContract.CheckResult.Refused

        assertTrue(
            PluginCanonicalContract.RefusalReason.PAYLOAD_TOO_LARGE in result.reasons,
        )
    }

    // ------------------------------------------------------------------
    // Invocation gate
    // ------------------------------------------------------------------

    @Test
    fun completeHealthyInvocationIsAccepted() {
        assertEquals(
            PluginCanonicalContract.CheckResult.Accepted,
            PluginCanonicalContract.checkInvocation(
                invocation = PluginCanonicalContract.PluginInvocation(
                    pluginId = "com.example.plugin",
                    payload = mapOf("apiKey" to "k", "retries" to "1"),
                ),
                schema = schema,
                host = host(),
            ),
        )
    }

    @Test
    fun inactiveLifecycleRefusesEvenWithValidPayload() {
        val result = PluginCanonicalContract.checkInvocation(
            invocation = PluginCanonicalContract.PluginInvocation("com.example.plugin", emptyMap()),
            schema = PluginCanonicalContract.PayloadSchema(emptyList()),
            host = host(lifecycleActive = false),
        ) as PluginCanonicalContract.CheckResult.Refused

        assertTrue(
            PluginCanonicalContract.RefusalReason.LIFECYCLE_NOT_ACTIVE in result.reasons,
        )
    }

    @Test
    fun missingTrustRefusesWhileApprovalIsRequired() {
        val result = PluginCanonicalContract.checkInvocation(
            invocation = PluginCanonicalContract.PluginInvocation("com.example.plugin"),
            schema = PluginCanonicalContract.PayloadSchema(emptyList()),
            host = host(trustGranted = false),
        ) as PluginCanonicalContract.CheckResult.Refused

        assertTrue(
            PluginCanonicalContract.RefusalReason.TRUST_NOT_GRANTED in result.reasons,
        )
    }

    @Test
    fun trustIsNotRequiredWhenThePolicyDisablesApproval() {
        assertEquals(
            PluginCanonicalContract.CheckResult.Accepted,
            PluginCanonicalContract.checkInvocation(
                invocation = PluginCanonicalContract.PluginInvocation("com.example.plugin"),
                schema = PluginCanonicalContract.PayloadSchema(emptyList()),
                host = host(trustGranted = false),
                policy = PluginInvocationPolicy(requireApproval = false),
            ),
        )
    }

    @Test
    fun invalidPluginIdIsATypedRefusalNotACrash() {
        val result = PluginCanonicalContract.checkInvocation(
            invocation = PluginCanonicalContract.PluginInvocation(
                pluginId = "a".repeat(200),
            ),
            schema = PluginCanonicalContract.PayloadSchema(emptyList()),
            host = host(),
        ) as PluginCanonicalContract.CheckResult.Refused

        assertTrue(
            PluginCanonicalContract.RefusalReason.INVALID_PLUGIN_ID in result.reasons,
        )
    }
}
