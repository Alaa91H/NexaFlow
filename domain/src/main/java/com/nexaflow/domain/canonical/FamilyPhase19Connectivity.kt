package com.nexaflow.domain.canonical

/**
 * T19 — Connectivity family (plan §T19).
 *
 * The largest family: 16 connectivity triggers and 24 connectivity actions
 * (excluding the seven SYSTEM_OPEN_*_SETTINGS entries already owned by the
 * T17 pilot). Pattern identical to T17/T18: refine reviewed mappings with
 * typed config upgrades, never re-map them.
 *
 * Semantics highlights (plan §T19 closure):
 * - Enable/disable actions are desired-state writes (idempotent, reversible)
 *   and support MULTI/ORDERED selection; contradictory desired states for
 *   the same target inside one batch are rejected by the T06 rules.
 * - Provider fallback (public API → privileged) is declared through the T07
 *   resolver: the canonical intent never names a backend.
 * - Wi-Fi connect/forget carry optional typed SSID (TEXT) and password as a
 *   SECRET_REFERENCE — secrets never ride in plain arguments.
 */
object FamilyPhase19Connectivity {

    object Keys {
        const val ENABLED = "enabled"
        const val SSID = "ssid"
        const val PASSWORD = "password"
    }

    /** State-setting actions keyed by their desired-state legacy name. */
    private val STATE_ACTIONS = setOf(
        "SYSTEM_WIFI",
        "SYSTEM_BLUETOOTH",
        "SYSTEM_MOBILE_DATA",
        "SYSTEM_HOTSPOT",
        "SYSTEM_NFC",
        "SYSTEM_AIRPLANE_MODE",
        "SYSTEM_DATA_SAVER",
        "SYSTEM_DATA_ROAMING",
        "SYSTEM_WIFI_SCANNING",
    )

    /** Value-setting actions (scalar payloads, not boolean). */
    private val VALUE_ACTIONS = setOf(
        "SYSTEM_NETWORK_MODE",
        "SYSTEM_PRIVATE_DNS",
        "SYSTEM_BLUETOOTH_DISCOVERABILITY",
        "SYSTEM_WIFI_SLEEP_POLICY",
    )

    /** Scan/connect/forget actions with optional typed filters. */
    private val WIFI_SESSION_ACTIONS = setOf(
        "SYSTEM_WIFI_CONNECT",
        "SYSTEM_WIFI_FORGET",
        "SYSTEM_WIFI_SCAN_NOW",
        "SYSTEM_BLUETOOTH_SCAN",
    )

    private val ALL_FAMILY_ACTIONS =
        STATE_ACTIONS + VALUE_ACTIONS + WIFI_SESSION_ACTIONS

    /** Connectivity triggers (observation nodes) from the reviewed inventory. */
    private val TRIGGER_NAMES = setOf(
        "WIFI_STATE", "WIFI_CONNECTED", "WIFI_SIGNAL_STRENGTH", "MOBILE_DATA_CONNECTED",
        "HOTSPOT", "BLUETOOTH_STATE", "BLUETOOTH_DEVICE", "NFC_STATE", "NFC_TAG_SCANNED",
        "AIRPLANE_MODE", "ETHERNET_CONNECTED", "VPN_CONNECTED", "DATA_ROAMING_STATE",
        "DATA_SAVER_STATE", "NETWORK_MODE", "CELL_SIGNAL_STRENGTH",
    )

    private class StateActionRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.ENABLED)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val entry = input.entry(Keys.ENABLED) ?: return skeleton
            val enabled = LegacyValueParsers.parseBoolean(entry)
            return SetStateNode(
                id = skeleton.id,
                target = skeleton.target,
                state = enabled,
            )
        }
    }

    private class ValueActionRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION

        private val primaryKey: String = when (legacyType) {
            "SYSTEM_NETWORK_MODE", "SYSTEM_PRIVATE_DNS" -> "mode"
            "SYSTEM_BLUETOOTH_DISCOVERABILITY" -> "timeoutSeconds"
            "SYSTEM_WIFI_SLEEP_POLICY" -> "policy"
            else -> error("Unknown connectivity value action: $legacyType")
        }

        override val consumedKeys: Set<String> = setOf(primaryKey)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val entry = input.entry(primaryKey) ?: return skeleton
            return SetValueNode(
                id = skeleton.id,
                target = skeleton.target,
                value = LegacyValueParsers.parseText(entry),
            )
        }
    }

    private class WifiSessionRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.SSID, Keys.PASSWORD)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val arguments = mutableListOf<CanonicalArgument>()
            input.entry(Keys.SSID)?.let {
                arguments += CanonicalArgument(CanonicalFieldId("ssid"), LegacyValueParsers.parseText(it))
            }
            input.entry(Keys.PASSWORD)?.let {
                // Passwords are referenced, never carried in plain form.
                arguments += CanonicalArgument(
                    CanonicalFieldId("password"),
                    SecretReferenceValue("legacy.wifi_password"),
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

    private class ConnectivityTriggerRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.TRIGGER
        override val consumedKeys: Set<String> = setOf(Keys.ENABLED)

        // A missing enabled key means an any-state match, not an error.
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as ObserveNode
            val entry = input.entry(Keys.ENABLED) ?: return skeleton
            return ObserveNode(
                id = skeleton.id,
                target = skeleton.target,
                predicate = skeleton.predicate,
                arguments = CanonicalArguments(
                    listOf(
                        CanonicalArgument(
                            CanonicalFieldId("connected"),
                            LegacyValueParsers.parseBoolean(entry),
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
            TRIGGER_NAMES.map { LegacyNodeKind.TRIGGER to it })
            .filterNot { it in generated.keys }
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "T15 table drift: ${missing.size} connectivity members missing",
            )
        }

        return ALL_FAMILY_ACTIONS.map { name ->
            val base = generated.getValue(LegacyNodeKind.ACTION to name)
            when {
                name in STATE_ACTIONS -> StateActionRule(name, base)
                name in VALUE_ACTIONS -> ValueActionRule(name, base)
                else -> WifiSessionRule(name, base)
            }
        } + TRIGGER_NAMES.map { name ->
            ConnectivityTriggerRule(name, generated.getValue(LegacyNodeKind.TRIGGER to name))
        }
    }

    /** Adapter with connectivity overrides merged over the full table. */
    fun adapterWithFamily(
        table: List<LegacyMappingRule> = LegacyMappingTable.all(),
    ): LegacyCanonicalAdapter {
        val overridden = ruleOverrides(table).associateBy { it.legacyType }
        return LegacyCanonicalAdapter(table.filter { it.legacyType !in overridden } + overridden.values)
    }

    /**
     * Connectivity enable selection: multi-target ordered with
     * CONTINUE_ON_ERROR (an unavailable radio must not block the others).
     */
    val enableCardinality: OperationCardinality = OperationCardinality()

    val enableSemantics: NodeSelectionSemantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.MULTI,
        executionMode = ExecutionMode.ORDERED,
        failurePolicy = FailurePolicy.CONTINUE_ON_ERROR,
    )

    /** Provider candidates for desired-state writes (plan §7.1 example). */
    fun stateWriteProviders(): OperationCapabilityRequirements = OperationCapabilityRequirements(
        operation = OperationId("core.operation.set_state"),
        providers = listOf(
            ProviderDescriptor(
                providerId = "core.provider.connectivity.public_api",
                strategy = com.nexaflow.domain.capability.operation.StrategyId.ANDROID_PUBLIC_API,
                capabilities = setOf(
                    ProviderCapability(com.nexaflow.domain.capability.CapabilityBackendId.ANDROID_API),
                ),
            ),
            ProviderDescriptor(
                providerId = "core.provider.connectivity.shizuku",
                strategy = com.nexaflow.domain.capability.operation.StrategyId.SHIZUKU_USER_SERVICE,
                capabilities = setOf(
                    ProviderCapability(
                        com.nexaflow.domain.capability.CapabilityBackendId.SHIZUKU,
                        com.nexaflow.domain.capability.PrivilegeLevel.SHIZUKU,
                    ),
                ),
            ),
            ProviderDescriptor(
                providerId = "core.provider.connectivity.root",
                strategy = com.nexaflow.domain.capability.operation.StrategyId.ROOT_SHELL,
                capabilities = setOf(
                    ProviderCapability(
                        com.nexaflow.domain.capability.CapabilityBackendId.ROOT,
                        com.nexaflow.domain.capability.PrivilegeLevel.ROOT,
                    ),
                ),
            ),
        ),
    )

    /** The connectivity enable schema (typed boolean desired state). */
    fun enableSchema(): NodeSchema = NodeSchema(
        schemaId = "core.schema.connectivity.set_state",
        kind = NodeSchemaKind.ACTION,
        target = TargetId("core.connectivity.wifi"),
        operation = OperationId("core.operation.set_state"),
        title = "Connectivity state",
        summaryTemplate = "Connectivity {enabled}",
        securityClass = NodeSecurityClass.SENSITIVE,
        selectionMode = TargetSelectionMode.MULTI,
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("enabled"),
                type = NodeFieldType.BOOLEAN,
                alwaysRequired = true,
            ),
        ),
        capabilities = listOf(
            NodeSchemaCapability("core.capability.system_setting_write"),
        ),
    )
}
