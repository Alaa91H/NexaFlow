package com.nexaflow.core.pluginsdk

/**
 * T31 — Canonical plugin SDK surface (plan §T31).
 *
 * The typed contract between the canonical platform and external plugins.
 * It mirrors the canonical AST identities pinned by T15/T25:
 *
 *  - the PLUGIN_EVENT trigger observes [TARGET_EVENT] with
 *    `core.predicate.match_event_filter` and carries exactly one typed
 *    `pluginId` argument;
 *  - the PLUGIN_FIRE action invokes [TARGET_ACTION] with `core.operation.invoke`
 *    and carries `pluginId` plus an optional typed payload.
 *
 * Everything here is host-side and pure: no Android framework types, no I/O.
 * The host adapters (Locale/Tasker bridges) translate this contract into
 * intents and ordered broadcasts at the boundary.
 */
object PluginCanonicalContract {

    /** Canonical target of the PLUGIN_EVENT trigger (T15 table). */
    const val TARGET_EVENT: String = "plugin.event"

    /** Canonical target of the PLUGIN_FIRE action (T15 table). */
    const val TARGET_ACTION: String = "plugin.action"

    /** Shared predicate for plugin event filtering (T15 table). */
    const val PREDICATE_MATCH_EVENT_FILTER: String = "core.predicate.match_event_filter"

    /** Operation of the PLUGIN_FIRE action (T15 table). */
    const val OPERATION_INVOKE: String = "core.operation.invoke"

    /** The single typed argument identifying the plugin instance. */
    const val ARG_PLUGIN_ID: String = "pluginId"

    /** Optional typed payload argument of PLUGIN_FIRE. */
    const val ARG_PAYLOAD: String = "payload"

    /** Optional event payload argument surfaced to trigger conditions. */
    const val ARG_EVENT_PAYLOAD: String = "eventPayload"

    /** Strict plugin id: opaque token, 1..96 chars, no whitespace. */
    val PLUGIN_ID_PATTERN: Regex = Regex("[A-Za-z0-9_][A-Za-z0-9_.-]{0,95}")

    /** Canonical value types a payload slot may carry. */
    enum class PayloadKind { STRING, BOOLEAN, INTEGER, DOUBLE, STRING_LIST }

    /** One typed payload slot. */
    data class PayloadSlot(
        val name: String,
        val kind: PayloadKind,
        val required: Boolean = false,
        /** Bounded strings: length cap fails closed on overflow. */
        val maximumLength: Int = 512,
    ) {
        init {
            require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}"))) {
                "payload slot name must match [A-Za-z][A-Za-z0-9_]{0,63}"
            }
            require(maximumLength in 1..4_096) { "maximumLength must be in 1..4096" }
        }
    }

    /** The declared payload shape of one plugin capability. */
    data class PayloadSchema(val slots: List<PayloadSlot> = emptyList()) {
        init {
            require(slots.map { it.name }.distinct().size == slots.size) {
                "payload slot names must be unique"
            }
        }

        fun slot(name: String): PayloadSlot? = slots.firstOrNull { it.name == name }
    }

    /** A plugin event emitted by an external plugin or the host bridge. */
    data class PluginEvent(
        val pluginId: String,
        /** Optional typed event payload (e.g. detected beacons, values). */
        val eventPayload: Map<String, String> = emptyMap(),
    ) {
        init {
            require(pluginId.matches(PLUGIN_ID_PATTERN)) { "Invalid plugin id" }
            require(eventPayload.size <= 32) { "event payload exceeds 32 entries" }
        }
    }

    /** A plugin invocation requested by a workflow action. */
    data class PluginInvocation(
        val pluginId: String,
        /** Payload values keyed by slot name; values are typed by the schema. */
        val payload: Map<String, String> = emptyMap(),
    )

    /** Why a request was refused; every reason is a stable diagnostics code. */
    enum class RefusalReason {
        INVALID_PLUGIN_ID,
        UNKNOWN_SLOT,
        MISSING_REQUIRED_SLOT,
        SLOT_TYPE_MISMATCH,
        SLOT_LENGTH_OVERFLOW,
        PAYLOAD_TOO_LARGE,
        LIFECYCLE_NOT_ACTIVE,
        TRUST_NOT_GRANTED,
    }

    /** The outcome of validating one event/invocation against a contract. */
    sealed class CheckResult {
        data object Accepted : CheckResult()
        data class Refused(val reasons: List<RefusalReason>) : CheckResult()
    }

    // ------------------------------------------------------------------
    // Event filter matching (host-side evaluation of the T15 predicate)
    // ------------------------------------------------------------------

    /**
     * Deterministic host-side matcher for `core.predicate.match_event_filter`:
     * an observation carries the same typed `pluginId` argument (and
     * optionally exact-match event payload entries); a plugin event matches
     * when the plugin id equals and every declared filter entry is present
     * and equal. Missing filter entries are wildcards.
     */
    fun eventMatches(
        filterPluginId: String,
        filterPayload: Map<String, String>,
        event: PluginEvent,
    ): Boolean {
        if (!filterPluginId.matches(PLUGIN_ID_PATTERN)) return false
        if (filterPluginId != event.pluginId) return false
        return filterPayload.all { (key, value) -> event.eventPayload[key] == value }
    }

    // ------------------------------------------------------------------
    // Payload validation (fail closed, bounded, deterministic)
    // ------------------------------------------------------------------

    private const val MAX_PAYLOAD_ENTRIES: Int = 32

    fun validatePayload(
        schema: PayloadSchema,
        payload: Map<String, String>,
    ): CheckResult {
        val reasons = mutableListOf<RefusalReason>()
        if (payload.size > MAX_PAYLOAD_ENTRIES) reasons += RefusalReason.PAYLOAD_TOO_LARGE

        for ((name, value) in payload) {
            val slot = schema.slot(name)
            when {
                slot == null -> reasons += RefusalReason.UNKNOWN_SLOT
                value.length > slot.maximumLength -> reasons += RefusalReason.SLOT_LENGTH_OVERFLOW
                !valueMatches(slot.kind, value) -> reasons += RefusalReason.SLOT_TYPE_MISMATCH
            }
        }
        for (slot in schema.slots) {
            if (slot.required && payload[slot.name] == null) {
                reasons += RefusalReason.MISSING_REQUIRED_SLOT
            }
        }
        return if (reasons.isEmpty()) CheckResult.Accepted else CheckResult.Refused(reasons.distinct())
    }

    private fun valueMatches(kind: PayloadKind, value: String): Boolean = when (kind) {
        PayloadKind.STRING -> true
        PayloadKind.BOOLEAN -> value == "true" || value == "false"
        PayloadKind.INTEGER -> value.toLongOrNull() != null
        PayloadKind.DOUBLE -> value.toDoubleOrNull()?.isFinite() == true
        PayloadKind.STRING_LIST -> {
            // Canonical list payloads use JSON-array syntax at this pure SDK
            // boundary. The Android adapter may encode/decode Bundles outside
            // this contract, but arbitrary comma splitting is not accepted.
            val trimmed = value.trim()
            trimmed.startsWith("[") && trimmed.endsWith("]") &&
                runCatching {
                    val body = trimmed.removePrefix("[").removeSuffix("]").trim()
                    body.isEmpty() || body.split(",").all { element ->
                        val item = element.trim()
                        item.length >= 2 && item.startsWith("\"") && item.endsWith("\"")
                    }
                }.getOrDefault(false)
        }
    }

    // ------------------------------------------------------------------
    // Full invocation check (schema + lifecycle + trust)
    // ------------------------------------------------------------------

    /** The host state a policy decision needs (pure mirror of T31 checks). */
    data class HostState(
        val lifecycleActive: Boolean,
        val trustGranted: Boolean,
    )

    /**
     * The single fail-closed gate before any adapter invocation: schema
     * validation, active lifecycle and granted trust must ALL hold. The
     * deprecated USER_APPROVED manifest value never satisfies [trustGranted] —
     * the host's user-policy layer decides that explicitly.
     */
    fun checkInvocation(
        invocation: PluginInvocation,
        schema: PayloadSchema,
        host: HostState,
        policy: PluginInvocationPolicy = PluginInvocationPolicy(),
    ): CheckResult {
        val reasons = mutableListOf<RefusalReason>()
        if (!invocation.pluginId.matches(PLUGIN_ID_PATTERN)) {
            reasons += RefusalReason.INVALID_PLUGIN_ID
        }
        val payloadBytes = invocation.payload.entries.sumOf {
            it.key.length + it.value.length
        }
        if (payloadBytes > policy.maximumPayloadBytes) {
            reasons += RefusalReason.PAYLOAD_TOO_LARGE
        }
        if (!host.lifecycleActive) reasons += RefusalReason.LIFECYCLE_NOT_ACTIVE
        if (policy.requireApproval && !host.trustGranted) {
            reasons += RefusalReason.TRUST_NOT_GRANTED
        }
        when (val payloadCheck = validatePayload(schema, invocation.payload)) {
            is CheckResult.Refused -> reasons += payloadCheck.reasons
            CheckResult.Accepted -> Unit
        }
        return if (reasons.isEmpty()) CheckResult.Accepted
        else CheckResult.Refused(reasons.distinct())
    }
}
