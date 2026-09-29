package com.nexaflow.core.pluginsdk

/**
 * T41 — Canonical plugin condition contract (ecosystem contract design).
 *
 * The typed, host-side contract for reading one configured external plugin
 * condition (Locale-compatible / Tasker-extended). It pins the decisions the
 * ecosystem contract design approved:
 *
 *  - the read is the `core.capability.plugin_condition_read` capability with
 *    the `core.operation.get_state` operation and a single persisted,
 *    user-approved `pluginInstance` argument — never a generic component
 *    invocation;
 *  - the ordered-broadcast result code maps onto the typed five-state
 *    condition result; `Unknown`, `Unavailable` and `Error` are NEVER
 *    coerced into `Unsatisfied` — the boolean gate only sees a verdict when
 *    the plugin actually produced one;
 *  - a query may run only when the instance is persisted and approved, the
 *    sender identity is verifiable (API 34+ sender APIs or a signature
 *    contract) and the host lifecycle is active — fail closed otherwise.
 *
 * Everything here is pure: no Android framework types, no I/O, no clocks.
 * The host adapter translates this contract into an ordered broadcast at
 * the boundary and feeds the result back through [fromLocaleResultCode].
 */
object PluginConditionContract {

    /** Canonical capability identity of the plugin condition read (T-mapping). */
    const val CAPABILITY: String = "core.capability.plugin_condition_read"

    /** Canonical operation of the read: a state query, nothing more. */
    const val OPERATION_GET_STATE: String = "core.operation.get_state"

    /** The single typed argument: the persisted, opaque instance reference. */
    const val ARG_PLUGIN_INSTANCE: String = "pluginInstance"

    /** Strict persisted instance reference: opaque token, 1..192 chars. */
    val PLUGIN_INSTANCE_PATTERN: Regex = Regex("[A-Za-z0-9_][A-Za-z0-9_.-]{0,191}")

    /** Why a condition query was refused; every reason is a stable diagnostics code. */
    enum class QueryRefusal {
        INVALID_PLUGIN_INSTANCE,
        INSTANCE_NOT_APPROVED,
        SENDER_NOT_VERIFIED,
        LIFECYCLE_NOT_ACTIVE,
    }

    /**
     * The typed condition state an external plugin condition resolves to.
     * Mirrors the domain's five-state condition result without depending on
     * any host module: `Unknown` and `Unavailable` are distinct, actionable
     * states and are never booleans.
     */
    sealed class ConditionState {
        /** The plugin evaluated the configured state as true. */
        data object Satisfied : ConditionState()

        /** The plugin evaluated the configured state as false. */
        data object Unsatisfied : ConditionState()

        /** The plugin cannot currently determine the state; this is not false. */
        data object Unknown : ConditionState()

        /** The plugin/component was unavailable before any result existed. */
        data object Unavailable : ConditionState()

        /** The query was answered with an invalid or actionable error. */
        data class Error(val reason: String) : ConditionState() {
            init {
                require(reason.isNotBlank()) { "Condition error reason must not be blank" }
                require(reason.length <= MAX_REASON_LENGTH) { "Condition error reason is too long" }
            }
        }
    }

    /** The condition query a workflow asks the host to run. */
    data class ConditionQuery(
        val pluginInstance: String,
    ) {
        init {
            require(pluginInstance.matches(PLUGIN_INSTANCE_PATTERN)) {
                "Invalid plugin instance reference"
            }
        }
    }

    /** Host state the query gate needs (pure mirror of the adapter checks). */
    data class QueryHostState(
        /** The instance exists in the persisted, user-approved store. */
        val instanceApproved: Boolean,
        /** Sender identity verified (API 34+ sender APIs or signature contract). */
        val senderVerified: Boolean,
        val lifecycleActive: Boolean,
    )

    /** Deterministic policy knobs; the defaults fail closed. */
    data class QueryPolicy(
        val requireInstanceApproval: Boolean = true,
        val requireSenderVerification: Boolean = true,
    )

    /** The outcome of gating one condition query. */
    sealed class QueryCheck {
        data object Accepted : QueryCheck()
        data class Refused(val reasons: List<QueryRefusal>) : QueryCheck()
    }

    /**
     * The single fail-closed gate before any condition broadcast is sent:
     * persisted+approved instance, verified sender identity and an active
     * host lifecycle must ALL hold when the corresponding policy knob is on.
     */
    fun checkQuery(
        @Suppress("UnusedParameter") query: ConditionQuery,
        host: QueryHostState,
        policy: QueryPolicy = QueryPolicy(),
    ): QueryCheck {
        // `query` stays in the signature so the gate contract carries the
        // typed instance argument end to end; its validity is enforced by
        // the ConditionQuery constructor and the host's approved-instance
        // lookup, so the pure policy check itself needs nothing from it.
        val reasons = mutableListOf<QueryRefusal>()
        if (policy.requireInstanceApproval && !host.instanceApproved) {
            reasons += QueryRefusal.INSTANCE_NOT_APPROVED
        }
        if (policy.requireSenderVerification && !host.senderVerified) {
            reasons += QueryRefusal.SENDER_NOT_VERIFIED
        }
        if (!host.lifecycleActive) {
            reasons += QueryRefusal.LIFECYCLE_NOT_ACTIVE
        }
        return if (reasons.isEmpty()) QueryCheck.Accepted
        else QueryCheck.Refused(reasons.distinct())
    }

    /**
     * Maps the Locale ordered-broadcast condition result code onto the typed
     * state. `16/17/18` are the Locale `RESULT_CONDITION_*` codes; every
     * other code is a typed [ConditionState.Error], never a boolean.
     */
    fun fromLocaleResultCode(code: Int): ConditionState = when (code) {
        LocaleContract.RESULT_CONDITION_SATISFIED -> ConditionState.Satisfied
        LocaleContract.RESULT_CONDITION_UNSATISFIED -> ConditionState.Unsatisfied
        LocaleContract.RESULT_CONDITION_UNKNOWN -> ConditionState.Unknown
        else -> ConditionState.Error("unmapped_locale_result_code_$code")
    }

    /** A timed-out query was never answered: unavailable, not false. */
    fun timedOut(): ConditionState = ConditionState.Unavailable

    /**
     * The tri-state gate rule: only a real plugin verdict produces a boolean;
     * every other state yields `null` and the caller's policy decides. This
     * is the function that makes "UNKNOWN must not become FALSE" structural.
     */
    fun booleanVerdict(state: ConditionState): Boolean? = when (state) {
        ConditionState.Satisfied -> true
        ConditionState.Unsatisfied -> false
        ConditionState.Unknown,
        ConditionState.Unavailable,
        is ConditionState.Error,
        -> null
    }

    private const val MAX_REASON_LENGTH = 1_024
}
