package com.nexaflow.core.pluginsdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T41 — Canonical plugin condition contract tests: the query gate fails
 * closed, the Locale result codes map onto the typed five-state result, and
 * `Unknown`/`Unavailable`/`Error` never collapse into a boolean.
 */
class PluginConditionContractTest {

    private fun host(
        instanceApproved: Boolean = true,
        senderVerified: Boolean = true,
        lifecycleActive: Boolean = true,
    ) = PluginConditionContract.QueryHostState(
        instanceApproved = instanceApproved,
        senderVerified = senderVerified,
        lifecycleActive = lifecycleActive,
    )

    private fun query(instance: String = "instance-abc123") =
        PluginConditionContract.ConditionQuery(pluginInstance = instance)

    // ------------------------------------------------------------------
    // Canonical identities
    // ------------------------------------------------------------------

    @Test
    fun canonicalIdentitiesMatchTheEcosystemContract() {
        assertEquals(
            "core.capability.plugin_condition_read",
            PluginConditionContract.CAPABILITY,
        )
        assertEquals(
            "core.operation.get_state",
            PluginConditionContract.OPERATION_GET_STATE,
        )
        assertEquals(
            "pluginInstance",
            PluginConditionContract.ARG_PLUGIN_INSTANCE,
        )
    }

    // ------------------------------------------------------------------
    // Fail-closed query gate
    // ------------------------------------------------------------------

    @Test
    fun healthyQueryIsAccepted() {
        val check = PluginConditionContract.checkQuery(query(), host())
        assertTrue(check is PluginConditionContract.QueryCheck.Accepted)
    }

    @Test
    fun unapprovedInstanceRefusesEvenWithEverythingElseFine() {
        val check = PluginConditionContract.checkQuery(
            query(),
            host(instanceApproved = false),
        )
        val refused = check as PluginConditionContract.QueryCheck.Refused
        assertTrue(
            PluginConditionContract.QueryRefusal.INSTANCE_NOT_APPROVED in refused.reasons,
        )
    }

    @Test
    fun unverifiedSenderRefusesWhileVerificationIsRequired() {
        val check = PluginConditionContract.checkQuery(
            query(),
            host(senderVerified = false),
        )
        val refused = check as PluginConditionContract.QueryCheck.Refused
        assertTrue(
            PluginConditionContract.QueryRefusal.SENDER_NOT_VERIFIED in refused.reasons,
        )
    }

    @Test
    fun senderIsAllowedWhenThePolicyDisablesVerification() {
        val check = PluginConditionContract.checkQuery(
            query(),
            host(senderVerified = false),
            PluginConditionContract.QueryPolicy(requireSenderVerification = false),
        )
        assertTrue(check is PluginConditionContract.QueryCheck.Accepted)
    }

    @Test
    fun inactiveLifecycleRefuses() {
        val check = PluginConditionContract.checkQuery(
            query(),
            host(lifecycleActive = false),
        )
        val refused = check as PluginConditionContract.QueryCheck.Refused
        assertTrue(
            PluginConditionContract.QueryRefusal.LIFECYCLE_NOT_ACTIVE in refused.reasons,
        )
    }

    @Test
    fun invalidPluginInstanceIsAConstructorFailureNotADefault() {
        try {
            PluginConditionContract.ConditionQuery(pluginInstance = "bad instance!")
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Invalid plugin instance"))
        }
    }

    // ------------------------------------------------------------------
    // Locale result-code mapping
    // ------------------------------------------------------------------

    @Test
    fun localeResultCodesMapOntoTypedStates() {
        assertEquals(
            PluginConditionContract.ConditionState.Satisfied,
            PluginConditionContract.fromLocaleResultCode(16),
        )
        assertEquals(
            PluginConditionContract.ConditionState.Unsatisfied,
            PluginConditionContract.fromLocaleResultCode(17),
        )
        assertEquals(
            PluginConditionContract.ConditionState.Unknown,
            PluginConditionContract.fromLocaleResultCode(18),
        )
    }

    @Test
    fun unmappedResultCodesAreTypedErrorsNeverBooleans() {
        val state = PluginConditionContract.fromLocaleResultCode(999)
        assertTrue(state is PluginConditionContract.ConditionState.Error)
        assertNull(PluginConditionContract.booleanVerdict(state))
    }

    @Test
    fun timedOutQueriesAreUnavailableNotFalse() {
        val state = PluginConditionContract.timedOut()
        assertEquals(
            PluginConditionContract.ConditionState.Unavailable,
            state,
        )
        assertNull(PluginConditionContract.booleanVerdict(state))
    }

    // ------------------------------------------------------------------
    // The tri-state rule
    // ------------------------------------------------------------------

    @Test
    fun onlyRealVerdictsProduceABoolean() {
        assertEquals(
            true,
            PluginConditionContract.booleanVerdict(PluginConditionContract.ConditionState.Satisfied),
        )
        assertEquals(
            false,
            PluginConditionContract.booleanVerdict(PluginConditionContract.ConditionState.Unsatisfied),
        )
        assertNull(
            PluginConditionContract.booleanVerdict(PluginConditionContract.ConditionState.Unknown),
        )
        assertNull(
            PluginConditionContract.booleanVerdict(PluginConditionContract.ConditionState.Unavailable),
        )
        assertNull(
            PluginConditionContract.booleanVerdict(
                PluginConditionContract.ConditionState.Error("receiver crashed"),
            ),
        )
    }

    @Test
    fun errorStatesRejectBlankAndOversizedReasons() {
        try {
            PluginConditionContract.ConditionState.Error(" ")
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("must not be blank"))
        }
        try {
            PluginConditionContract.ConditionState.Error("x".repeat(1_025))
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("too long"))
        }
    }

    @Test
    fun conditionStateIsDistinctFromPluginEventTypes() {
        // The condition contract shares the SDK module but never reuses the
        // event/invocation surface: conditions are read-side only.
        assertFalse(
            PluginConditionContract.ConditionState.Satisfied ==
                PluginCanonicalContract.CheckResult.Accepted,
        )
    }
}
