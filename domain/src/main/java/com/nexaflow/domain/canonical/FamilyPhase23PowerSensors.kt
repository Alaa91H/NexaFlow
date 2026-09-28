package com.nexaflow.domain.canonical

/**
 * T23 — Battery / Power / Sensors / Peripherals family (plan §T23).
 *
 * Upgrades 5 power actions and 8 battery/sensor/peripheral triggers over
 * the reviewed mappings. Threshold semantics are explicit (plan §14):
 *
 * - Threshold triggers (battery level, temperature) carry typed threshold
 *   values; combined battery filters (charging state, charger type) ride
 *   along as additional typed arguments.
 * - State triggers (charger connected, power saver) carry optional typed
 *   booleans; absent key = any-state match.
 * - State-or-event peripheral triggers (USB/HDMI/Wear) stay filterable.
 */
object FamilyPhase23PowerSensors {

    object Keys {
        const val ENABLED = "enabled"
        const val THRESHOLD = "threshold"
        const val VALUE = "value"
        const val CHARGING = "charging"
    }

    /** Boolean power actions. */
    private val POWER_BOOLEAN = setOf(
        "SYSTEM_ADAPTIVE_BATTERY",
        "SYSTEM_POWER_SAVER",
    )

    /** Scalar power actions. */
    private val POWER_VALUE = setOf(
        "SYSTEM_BATTERY_SAVER_THRESHOLD",
        "SYSTEM_CHARGING_LIMIT",
        "SYSTEM_CHARGING_FEEDBACK",
    )

    /** Threshold-based triggers. */
    private val THRESHOLD_TRIGGERS = setOf(
        "BATTERY",
        "BATTERY_TEMPERATURE",
    )

    /** Boolean state triggers. */
    private val STATE_TRIGGERS = setOf(
        "CHARGER",
        "POWER_SAVER",
    )

    /** State-or-event peripheral triggers. */
    private val PERIPHERAL_TRIGGERS = setOf(
        "USB_CONNECTED",
        "HDMI_CONNECTED",
        "WEAR_EVENT",
    )

    /** Reading-based sensor trigger. */
    private val READING_TRIGGERS = setOf("SENSOR")

    private val ALL_FAMILY_ACTIONS = POWER_BOOLEAN + POWER_VALUE
    private val ALL_FAMILY_TRIGGERS = THRESHOLD_TRIGGERS + STATE_TRIGGERS +
        PERIPHERAL_TRIGGERS + READING_TRIGGERS

    private class PowerBooleanRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.ENABLED)
        override val requiredKeys: Set<String> = setOf(Keys.ENABLED)

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            return SetStateNode(
                id = skeleton.id,
                target = skeleton.target,
                state = LegacyValueParsers.parseBoolean(input.entry(Keys.ENABLED)!!),
            )
        }
    }

    private class PowerValueRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.VALUE)
        override val requiredKeys: Set<String> = setOf(Keys.VALUE)

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            return SetValueNode(
                id = skeleton.id,
                target = skeleton.target,
                value = LegacyValueParsers.parseInteger(input.entry(Keys.VALUE)!!),
            )
        }
    }

    private class PowerTriggerRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.TRIGGER
        override val consumedKeys: Set<String> =
            setOf(Keys.ENABLED, Keys.THRESHOLD, Keys.VALUE, Keys.CHARGING)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as ObserveNode
            val arguments = mutableListOf<CanonicalArgument>()

            input.entry(Keys.THRESHOLD)?.let {
                arguments += CanonicalArgument(
                    CanonicalFieldId("threshold"),
                    LegacyValueParsers.parseInteger(it),
                )
            }
            input.entry(Keys.VALUE)?.let {
                arguments += CanonicalArgument(
                    CanonicalFieldId("value"),
                    LegacyValueParsers.parseInteger(it),
                )
            }
            input.entry(Keys.ENABLED)?.let {
                arguments += CanonicalArgument(
                    CanonicalFieldId("enabled"),
                    LegacyValueParsers.parseBoolean(it),
                )
            }
            input.entry(Keys.CHARGING)?.let {
                arguments += CanonicalArgument(
                    CanonicalFieldId("charging"),
                    LegacyValueParsers.parseBoolean(it),
                )
            }

            return ObserveNode(
                id = skeleton.id,
                target = skeleton.target,
                predicate = skeleton.predicate,
                arguments = CanonicalArguments(arguments),
            )
        }
    }

    /** Overrides for every family member present in the generated table. */
    fun ruleOverrides(table: List<LegacyMappingRule> = LegacyMappingTable.all()): List<LegacyMappingRule> {
        val generated = table.associateBy { it.kind to it.legacyType }
        val missing = (ALL_FAMILY_ACTIONS.map { LegacyNodeKind.ACTION to it } +
            ALL_FAMILY_TRIGGERS.map { LegacyNodeKind.TRIGGER to it })
            .filterNot { it in generated.keys }
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "T15 table drift: ${missing.size} power/sensor members missing",
            )
        }

        return ALL_FAMILY_ACTIONS.map { name ->
            val base = generated.getValue(LegacyNodeKind.ACTION to name)
            if (name in POWER_BOOLEAN) PowerBooleanRule(name, base)
            else PowerValueRule(name, base)
        } + ALL_FAMILY_TRIGGERS.map { name ->
            PowerTriggerRule(name, generated.getValue(LegacyNodeKind.TRIGGER to name))
        }
    }

    /** Adapter with power/sensor overrides merged over the full table. */
    fun adapterWithFamily(
        table: List<LegacyMappingRule> = LegacyMappingTable.all(),
    ): LegacyCanonicalAdapter {
        val overridden = ruleOverrides(table).associateBy { it.legacyType }
        return LegacyCanonicalAdapter(table.filter { it.legacyType !in overridden } + overridden.values)
    }

    /** Power writes are multi-capable ordered with continue-on-error. */
    val powerSemantics: NodeSelectionSemantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.MULTI,
        executionMode = ExecutionMode.ORDERED,
        failurePolicy = FailurePolicy.CONTINUE_ON_ERROR,
    )

    /** The battery-saver schema with a bounded threshold. */
    fun batterySaverThresholdSchema(): NodeSchema = NodeSchema(
        schemaId = "core.schema.power.saver_threshold.set_value",
        kind = NodeSchemaKind.ACTION,
        target = TargetId("core.power.saver_threshold"),
        operation = OperationId("core.operation.set_value"),
        title = "Battery saver threshold",
        summaryTemplate = "Saver below {threshold}%",
        securityClass = NodeSecurityClass.STANDARD,
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("threshold"),
                type = NodeFieldType.INTEGER,
                alwaysRequired = true,
                minimum = 0,
                maximum = 100,
            ),
        ),
        capabilities = listOf(
            NodeSchemaCapability("core.capability.system_setting_write"),
        ),
    )
}
