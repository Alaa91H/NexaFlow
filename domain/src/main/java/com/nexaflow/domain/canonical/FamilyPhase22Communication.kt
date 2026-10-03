package com.nexaflow.domain.canonical

/**
 * T22 — Notifications / Calls / Communication family (plan §T22).
 *
 * Upgrades 14 communication actions and 4 communication triggers over the
 * reviewed mappings. Sensitive-data rules are structural:
 *
 * - Outbound message bodies (SMS/email/notification text) are typed TEXT in
 *   the AST but flagged via the SENSITIVE security class on their schemas —
 *   the journal's secret sweep (T11) refuses to record them.
 * - SEND operations declare NON_IDEMPOTENT command semantics: a transport
 *   failure may never trigger a blind re-send (plan rule 46.14); recovery
 *   reconciles through reads or requires explicit user intent.
 * - Event filters (incoming-call/sms/notification) are optional typed
 *   package lists — absent filter means "any sender" (ANY_OF semantics).
 */
object FamilyPhase22Communication {

    object Keys {
        const val PACKAGE = "package"
        const val PACKAGES = "packages"
        const val TEXT = "text"
        const val NUMBER = "number"
        const val TITLE = "title"
    }

    /** Outbound send actions: NON_IDEMPOTENT, sensitive payloads. */
    private val SEND_ACTIONS = setOf(
        "SYSTEM_SEND_SMS",
        "SYSTEM_SEND_EMAIL",
        "SYSTEM_SEND_NOTIFICATION",
        "SYSTEM_SEND_REMINDER",
        "SMS_REPLY",
        "CALL_REPLY_WITH_SMS",
        "BATTERY_ALERTS",
        "BATTERY_CHARGING_NOTIFICATIONS",
    )

    /** Show/toast actions: interactive, no blind retry either. */
    private val SHOW_ACTIONS = setOf(
        "SYSTEM_ALERT",
        "SYSTEM_TOAST",
    )

    /** Notification-management actions (clear/block policies). */
    private val POLICY_ACTIONS = setOf(
        "SYSTEM_CLEAR_NOTIFICATIONS",
        "SYSTEM_CLEAR_APP_NOTIFICATIONS",
        "SYSTEM_BLOCK_NOTIFICATION",
        "SMS_BLOCK_INCOMING",
    )

    /** Call actions (reject/silence — reversible call-scoped states). */
    private val CALL_ACTIONS = setOf(
        "CALL_BLOCK",
        "CALL_BLOCK_SILENT",
        "CALL_REPLY_WITH_SMS",
        "CALL_SILENCE",
    )

    /** Dial is an external, user-visible side effect. */
    private val DIAL_ACTIONS = setOf("SYSTEM_DIAL_NUMBER")

    private val COMM_TRIGGERS = setOf(
        "CALL_STATE", "INCOMING_CALL", "NOTIFICATION", "SMS",
    )

    private val ALL_FAMILY_ACTIONS = SEND_ACTIONS + SHOW_ACTIONS +
        POLICY_ACTIONS + CALL_ACTIONS + DIAL_ACTIONS

    private fun isMessageAction(legacyType: String): Boolean =
        legacyType in setOf("SYSTEM_SEND_SMS", "SYSTEM_SEND_EMAIL", "SYSTEM_DIAL_NUMBER")

    /** Generic typed upgrade: consumes all message-ish keys, all optional. */
    private class CommunicationRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> =
            setOf(Keys.PACKAGE, Keys.PACKAGES, Keys.TEXT, Keys.NUMBER, Keys.TITLE)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val arguments = mutableListOf<CanonicalArgument>()

            input.entry(Keys.TEXT)?.let {
                arguments += CanonicalArgument(CanonicalFieldId("text"), LegacyValueParsers.parseText(it))
            }
            input.entry(Keys.TITLE)?.let {
                arguments += CanonicalArgument(CanonicalFieldId("title"), LegacyValueParsers.parseText(it))
            }
            input.entry(Keys.NUMBER)?.let {
                arguments += CanonicalArgument(CanonicalFieldId("number"), LegacyValueParsers.parseText(it))
            }
            input.entry(Keys.PACKAGES)?.let {
                val packages = it.rawValue.split('|', ';', ',')
                    .map { token -> token.trim() }
                    .filter { token -> token.isNotEmpty() }
                    .map { token -> PackageIdValue(token) }
                if (packages.isNotEmpty()) {
                    arguments += CanonicalArgument(
                        CanonicalFieldId("packages"),
                        CollectionValue(CanonicalValueKind.PACKAGE_ID, packages),
                    )
                }
            }
            input.entry(Keys.PACKAGE)?.let {
                arguments += CanonicalArgument(
                    CanonicalFieldId("appPackage"),
                    LegacyValueParsers.parsePackage(it),
                )
            }

            return InvokeNode(
                id = skeleton.id,
                target = skeleton.target,
                operation = skeleton.operation,
                arguments = CanonicalArguments(arguments),
            )
        }
    }

    private class CommunicationTriggerRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.TRIGGER
        override val consumedKeys: Set<String> = setOf(Keys.PACKAGE, Keys.PACKAGES)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as ObserveNode
            val entry = input.entry(Keys.PACKAGES) ?: input.entry(Keys.PACKAGE)
                ?: return skeleton // no filter: any sender matches
            val packages = entry.rawValue.split('|', ';', ',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { PackageIdValue(it) }
            if (packages.isEmpty()) return skeleton
            return ObserveNode(
                id = skeleton.id,
                target = skeleton.target,
                predicate = skeleton.predicate,
                arguments = CanonicalArguments(
                    listOf(
                        CanonicalArgument(
                            CanonicalFieldId("packages"),
                            CollectionValue(CanonicalValueKind.PACKAGE_ID, packages),
                        ),
                    ),
                ),
            )
        }
    }

    /** Overrides for every family member present in the generated table. */
    fun ruleOverrides(table: List<LegacyMappingRule> = LegacyMappingTable.all()): List<LegacyMappingRule> {
        val generated = table.associateBy { it.kind to it.legacyType }
        val missing = (ALL_FAMILY_ACTIONS.map { LegacyNodeKind.ACTION to it } +
            COMM_TRIGGERS.map { LegacyNodeKind.TRIGGER to it })
            .filterNot { it in generated.keys }
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "T15 table drift: ${missing.size} communication members missing",
            )
        }

        return ALL_FAMILY_ACTIONS.map { name ->
            CommunicationRule(name, generated.getValue(LegacyNodeKind.ACTION to name))
        } + COMM_TRIGGERS.map { name ->
            CommunicationTriggerRule(name, generated.getValue(LegacyNodeKind.TRIGGER to name))
        }
    }

    /** Adapter with communication overrides merged over the full table. */
    fun adapterWithFamily(
        table: List<LegacyMappingRule> = LegacyMappingTable.all(),
    ): LegacyCanonicalAdapter {
        val overridden = ruleOverrides(table).associateBy { it.legacyType }
        return LegacyCanonicalAdapter(table.filter { it.legacyType !in overridden } + overridden.values)
    }

    /** Outbound sends are strictly single-target (one recipient channel). */
    val sendSemantics: NodeSelectionSemantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.SINGLE,
        executionMode = ExecutionMode.SINGLE,
    )

    /** Notification management supports bounded multi-app batches. */
    val policySemantics: NodeSelectionSemantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.MULTI,
        executionMode = ExecutionMode.ORDERED,
        failurePolicy = FailurePolicy.CONTINUE_ON_ERROR,
    )

    /**
     * Command semantics for the family: sends are NON_IDEMPOTENT (the strict
     * vocabulary is richer than the plan's SEND example — an SMS re-send is
     * a duplicate message, never a safe retry), policy clears are
     * IDEMPOTENT, rejects/silence are IDEMPOTENT reversible call states.
     */
    fun commandSemantics(): List<CommandSemantics> = listOf(
        CommandSemantics(
            OperationId("core.operation.send"),
            CommandIdempotency.NON_IDEMPOTENT,
            reversible = false,
        ),
        CommandSemantics(
            OperationId("core.operation.clear"),
            CommandIdempotency.IDEMPOTENT,
            reversible = false,
        ),
        CommandSemantics(
            OperationId("core.operation.set_blocked"),
            CommandIdempotency.IDEMPOTENT,
            reversible = true,
        ),
        CommandSemantics(
            OperationId("core.operation.reject"),
            CommandIdempotency.IDEMPOTENT,
            reversible = false,
        ),
        CommandSemantics(
            OperationId("core.operation.silence"),
            CommandIdempotency.IDEMPOTENT,
            reversible = true,
        ),
        CommandSemantics(
            OperationId("core.operation.dial"),
            CommandIdempotency.NON_IDEMPOTENT,
            reversible = false,
        ),
        CommandSemantics(
            OperationId("core.operation.show"),
            CommandIdempotency.NON_IDEMPOTENT,
            reversible = false,
        ),
        CommandSemantics(
            OperationId("core.operation.schedule"),
            CommandIdempotency.NON_IDEMPOTENT,
            reversible = false,
        ),
    )

    /** The SMS schema (SENSITIVE: message bodies are sensitive data). */
    fun smsSchema(): NodeSchema = NodeSchema(
        schemaId = "core.schema.communication.sms.send",
        kind = NodeSchemaKind.ACTION,
        target = TargetId("core.communication.sms"),
        operation = OperationId("core.operation.send"),
        title = "Send SMS",
        summaryTemplate = "SMS to {number}",
        securityClass = NodeSecurityClass.SENSITIVE,
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("number"),
                type = NodeFieldType.TEXT,
                alwaysRequired = true,
                maximum = 32,
            ),
            NodeSchemaField(
                id = CanonicalFieldId("text"),
                type = NodeFieldType.TEXT,
                alwaysRequired = true,
                maximum = 1600,
            ),
        ),
        capabilities = listOf(
            NodeSchemaCapability("core.capability.intent_launch"),
        ),
    )
}
