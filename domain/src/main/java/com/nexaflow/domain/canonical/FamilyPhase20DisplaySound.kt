package com.nexaflow.domain.canonical

/**
 * T20 — Display / Screen / Sound / Haptics family (plan §T20).
 *
 * Upgrades 19 display actions + 14 sound/haptics actions and 11 triggers
 * with typed config, over the reviewed T15 mappings. Highlights:
 *
 * - Boolean desired states (dark mode, auto brightness, DND, …) become
 *   typed SetState with a required boolean; scalar settings (brightness,
 *   volume, font scale, timeouts) become typed SetValue with strict numeric
 *   parsing where the legacy payload is numeric.
 * - Batch profiles (e.g. a night profile: dark mode on + extra dim on +
 *   brightness down) are plain adjacent typed writes in one atomic scope;
 *   the T06 rules reject contradictory profiles and the T10 planner proves
 *   parallel safety per scope.
 * - Ringer mode is an enum-token allowlist (NORMAL/SILENT/VIBRATE), so an
 *   unknown mode fails schema validation instead of coercing.
 */
object FamilyPhase20DisplaySound {

    object Keys {
        const val ENABLED = "enabled"
        const val VALUE = "value"
    }

    /** Display actions whose desired state is boolean. */
    private val DISPLAY_BOOLEAN = setOf(
        "SYSTEM_ALWAYS_ON_DISPLAY",
        "SYSTEM_ANIMATIONS",
        "SYSTEM_AUTO_BRIGHTNESS",
        "SYSTEM_COLOR_INVERSION",
        "SYSTEM_DARK_MODE",
        "SYSTEM_EXTRA_DIM",
        "SYSTEM_GRAYSCALE",
        "SYSTEM_NIGHT_LIGHT",
        "SYSTEM_POINTER_LOCATION",
        "SYSTEM_SCREENSAVER",
        "SYSTEM_SCREEN_ROTATION",
        "SYSTEM_SHOW_TAPS",
        "SYSTEM_STAY_AWAKE",
    )

    /** Display actions with scalar payloads. */
    private val DISPLAY_VALUE = setOf(
        "SYSTEM_BRIGHTNESS",
        "SYSTEM_DISPLAY_DENSITY",
        "SYSTEM_FONT_SCALE",
        "SYSTEM_SCREENSAVER_TIMEOUT",
        "SYSTEM_SCREEN_TIMEOUT",
    )

    /** Sound/haptics boolean actions. */
    private val SOUND_BOOLEAN = setOf(
        "SYSTEM_CAMERA_SHUTTER_SOUND",
        "SYSTEM_DND",
        "SYSTEM_HAPTIC_FEEDBACK",
        "SYSTEM_SOUND_EFFECTS",
        "SYSTEM_VIBRATE",
    )

    /** Sound/haptics scalar or enum actions. */
    private val SOUND_VALUE = setOf(
        "SYSTEM_HAPTIC_INTENSITY",
        "SYSTEM_RING_VOLUME",
        "SYSTEM_STREAM_VOLUME",
        "SYSTEM_VOLUME",
        "SYSTEM_RINGER_MODE",
        "SYSTEM_SET_RINGTONE",
        "SYSTEM_SET_NOTIFICATION_TONE",
        "SYSTEM_CALL_VIBRATION",
        "SYSTEM_VIBRATE_PATTERN",
    )

    /** Wake/other one-shot display actions kept as invoke skeletons. */
    private val OTHER_ACTIONS = setOf("SYSTEM_WAKE_SCREEN")

    private val DISPLAY_TRIGGERS = setOf(
        "AUTO_BRIGHTNESS_STATE", "AUTO_ROTATE", "BRIGHTNESS_LEVEL", "DARK_MODE",
        "DND_STATE", "HEADPHONE", "RINGER_MODE", "SCREEN_ROTATION_STATE",
        "SCREEN_TIMEOUT_CHANGED", "STAY_AWAKE_STATE", "VOLUME_CHANGED",
    )

    private val ALL_FAMILY_ACTIONS =
        DISPLAY_BOOLEAN + DISPLAY_VALUE + SOUND_BOOLEAN + SOUND_VALUE + OTHER_ACTIONS

    private class BooleanActionRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.ENABLED)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val entry = input.entry(Keys.ENABLED) ?: return skeleton
            return SetStateNode(
                id = skeleton.id,
                target = skeleton.target,
                state = LegacyValueParsers.parseBoolean(entry),
            )
        }
    }

    private class ValueActionRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.VALUE)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val entry = input.entry(Keys.VALUE) ?: return skeleton
            // Numeric settings upgrade to typed integers; enum-ish payloads
            // (ringer mode) stay as strict text tokens.
            val value = entry.rawValue.toLongOrNull()?.let { IntegerValue(it) }
                ?: LegacyValueParsers.parseText(entry)
            return SetValueNode(
                id = skeleton.id,
                target = skeleton.target,
                value = value,
            )
        }
    }

    private class TriggerRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.TRIGGER
        override val consumedKeys: Set<String> = setOf(Keys.ENABLED, Keys.VALUE)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as ObserveNode
            val arguments = mutableListOf<CanonicalArgument>()
            input.entry(Keys.ENABLED)?.let {
                arguments += CanonicalArgument(
                    CanonicalFieldId("enabled"),
                    LegacyValueParsers.parseBoolean(it),
                )
            }
            input.entry(Keys.VALUE)?.let {
                val numeric = it.rawValue.toLongOrNull()?.let { n -> IntegerValue(n) }
                    ?: LegacyValueParsers.parseText(it)
                arguments += CanonicalArgument(CanonicalFieldId("value"), numeric)
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
            DISPLAY_TRIGGERS.map { LegacyNodeKind.TRIGGER to it })
            .filterNot { it in generated.keys }
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "T15 table drift: ${missing.size} display/sound members missing",
            )
        }

        return ALL_FAMILY_ACTIONS.map { name ->
            val base = generated.getValue(LegacyNodeKind.ACTION to name)
            when {
                name in DISPLAY_BOOLEAN || name in SOUND_BOOLEAN -> BooleanActionRule(name, base)
                name in DISPLAY_VALUE || name in SOUND_VALUE -> ValueActionRule(name, base)
                else -> base // WAKE_SCREEN keeps its reviewed skeleton
            }
        } + DISPLAY_TRIGGERS.map { name ->
            TriggerRule(name, generated.getValue(LegacyNodeKind.TRIGGER to name))
        }
    }

    /** Adapter with display/sound overrides merged over the full table. */
    fun adapterWithFamily(
        table: List<LegacyMappingRule> = LegacyMappingTable.all(),
    ): LegacyCanonicalAdapter {
        val overridden = ruleOverrides(table).associateBy { it.legacyType }
        return LegacyCanonicalAdapter(table.filter { it.legacyType !in overridden } + overridden.values)
    }

    /**
     * Display batch profile semantics: several display writes in one ordered
     * execution with CONTINUE_ON_ERROR (a blocked setting must not block the
     * rest of the profile). Contradictions are T06's job.
     */
    val profileSemantics: NodeSelectionSemantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.MULTI,
        executionMode = ExecutionMode.ORDERED,
        failurePolicy = FailurePolicy.CONTINUE_ON_ERROR,
    )

    /** The ringer-mode schema with the enum-token allowlist. */
    fun ringerModeSchema(): NodeSchema = NodeSchema(
        schemaId = "core.schema.audio.ringer_mode.set_value",
        kind = NodeSchemaKind.ACTION,
        target = TargetId("core.audio.ringer_mode"),
        operation = OperationId("core.operation.set_value"),
        title = "Ringer mode",
        summaryTemplate = "Ringer {mode}",
        securityClass = NodeSecurityClass.STANDARD,
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("mode"),
                type = NodeFieldType.ENUM_TOKEN,
                alwaysRequired = true,
                enumType = "core.audio.ringer_mode",
                allowedTokens = listOf("NORMAL", "SILENT", "VIBRATE"),
            ),
        ),
        capabilities = listOf(
            NodeSchemaCapability("core.capability.system_setting_write"),
        ),
    )

    /** The brightness schema uses the persisted/runtime 0..255 integer scale. */
    fun brightnessSchema(): NodeSchema = NodeSchema(
        schemaId = "core.schema.display.brightness.set_value",
        kind = NodeSchemaKind.ACTION,
        target = TargetId("core.display.brightness"),
        operation = OperationId("core.operation.set_value"),
        title = "Brightness",
        summaryTemplate = "Brightness {level}",
        securityClass = NodeSecurityClass.STANDARD,
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("level"),
                type = NodeFieldType.INTEGER,
                alwaysRequired = true,
                minimum = 0,
                maximum = 255,
            ),
        ),
        capabilities = listOf(
            NodeSchemaCapability("core.capability.system_setting_write"),
        ),
    )
}
