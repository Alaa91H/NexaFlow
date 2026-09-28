package com.nexaflow.domain.canonical

/**
 * T17 — Pilot family: Open Settings (plan §T17).
 *
 * The clearest duplication (SYSTEM_OPEN_*) becomes the first family with
 * typed config upgrades on top of the T15 skeleton table. Contract:
 *
 * - Rule overrides are keyed exactly like the generated table and must match
 *   an existing entry — the family can only refine a reviewed mapping, never
 *   invent a new one (fail closed on drift with T15).
 * - Consumed keys are strictly parsed into typed canonical values; anything
 *   the family does not consume still rides along losslessly (T14 contract).
 * - The pilot schema ([openSettingsSchema]) is the single source of truth for
 *   the family's fields, defaults, and summary — wired into the T08 engine.
 */
object PilotOpenFamily {

    private val SET_STATE_FREE_TARGET = TargetId("core.system.settings")

    /** Legacy config keys the pilot family upgrades into typed values. */
    object Keys {
        const val PAGE = "page"
        const val PACKAGE = "package"
        const val URL = "url"
    }

    /**
     * Value upgrade rule for one SYSTEM_OPEN_* mapping: consumes its legacy
     * config keys and produces a typed [OpenNode]. The produced node reuses
     * the reviewed target/operation from the generated table entry.
     */
    private class OpenFamilyRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> =
            if (legacyType == "SYSTEM_OPEN_APP") {
                setOf(Keys.PACKAGE)
            } else if (legacyType == "SYSTEM_OPEN_URL") {
                setOf(Keys.URL)
            } else {
                setOf(Keys.PAGE)
            }

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val arguments = mutableListOf<CanonicalArgument>()

            when (legacyType) {
                "SYSTEM_OPEN_APP" -> {
                    val pkg = input.entry(Keys.PACKAGE)
                    if (pkg != null) {
                        arguments += CanonicalArgument(
                            CanonicalFieldId("packageName"),
                            LegacyValueParsers.parsePackage(pkg),
                        )
                    }
                }
                "SYSTEM_OPEN_URL" -> {
                    val url = input.entry(Keys.URL)
                        ?: throw IllegalArgumentException("SYSTEM_OPEN_URL requires a url")
                    arguments += CanonicalArgument(
                        CanonicalFieldId("url"),
                        LegacyValueParsers.parseUri(url),
                    )
                }
                else -> {
                    // Page-opening actions carry a typed page token: the page
                    // identity IS the reviewed target mapping (parity with
                    // legacy), upgraded into the schema's enum value.
                    input.entry(Keys.PAGE)?.let { page ->
                        arguments += CanonicalArgument(
                            CanonicalFieldId("page"),
                            LegacyValueParsers.parseEnumToken(
                                page,
                                "core.system.settings",
                                openSettingsSchema()
                                    .field(CanonicalFieldId("page"))
                                    ?.allowedTokens
                                    ?: emptyList(),
                            ),
                        )
                    }
                }
            }

            return OpenNode(
                id = skeleton.id,
                target = skeleton.target,
                operation = skeleton.operation,
                arguments = CanonicalArguments(arguments),
            )
        }
    }

    /**
     * Builds the pilot rule overrides. Every override must match a generated
     * T15 entry (same kind+legacyType); drift fails closed at construction.
     */
    fun ruleOverrides(table: List<LegacyMappingRule> = LegacyMappingTable.all()): List<LegacyMappingRule> {
        val generated = table
            .filter { it.kind == LegacyNodeKind.ACTION && it.legacyType.startsWith("SYSTEM_OPEN_") }
            .associateBy { it.legacyType }

        val expected = generated.keys
        if (expected.size != 41) {
            throw IllegalStateException(
                "T15 table drift: expected 41 SYSTEM_OPEN_* entries, found ${expected.size}",
            )
        }

        return generated.map { (legacyType, base) -> OpenFamilyRule(legacyType, base) }
    }

    /**
     * An adapter whose rule table replaces the pilot entries with the typed
     * upgrades while keeping all other 192+ generated rules intact.
     */
    fun adapterWithPilot(
        table: List<LegacyMappingRule> = LegacyMappingTable.all(),
    ): LegacyCanonicalAdapter {
        val overridden = ruleOverrides(table).associateBy { it.legacyType }
        val merged = table.filter { it.legacyType !in overridden } + overridden.values
        return LegacyCanonicalAdapter(merged)
    }

    /** The family's schema: single-select settings page, typed URL field. */
    fun openSettingsSchema(): NodeSchema = NodeSchema(
        schemaId = "core.schema.system.settings.open",
        kind = NodeSchemaKind.ACTION,
        target = SET_STATE_FREE_TARGET,
        operation = OperationId("core.operation.open"),
        title = "Open settings",
        summaryTemplate = "Open {page}{{, package {packageName}}}{{, {url}}}",
        securityClass = NodeSecurityClass.STANDARD,
        selectionMode = TargetSelectionMode.SINGLE,
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("page"),
                type = NodeFieldType.ENUM_TOKEN,
                alwaysRequired = true,
                enumType = "core.system.settings",
                allowedTokens = listOf(
                    "SETTINGS",
                    "ABOUT_PHONE",
                    "ACCESSIBILITY",
                    "AIRPLANE_MODE",
                    "APP_SETTINGS_LIST",
                    "BATTERY",
                    "BLUETOOTH",
                    "CAST",
                    "DATA_SAVER",
                    "DATA_USAGE",
                    "DATE",
                    "DEFAULT_APPS",
                    "DEVELOPER",
                    "DEVICE_ADMIN",
                    "DISPLAY",
                    "INPUT_METHOD",
                    "LOCATION",
                    "NETWORK",
                    "NFC",
                    "NOTIFICATION",
                    "PRINT",
                    "PRIVACY",
                    "SECURITY",
                    "SOUND",
                    "STORAGE",
                    "SYSTEM_UPDATE",
                    "USAGE_ACCESS",
                    "VPN",
                    "WIFI",
                ),
            ),
            NodeSchemaField(
                id = CanonicalFieldId("packageName"),
                type = NodeFieldType.PACKAGE_ID,
                level = NodeSchemaLevel.ADVANCED,
                visibleWhen = listOf(
                    NodeFieldCondition.Equals(CanonicalFieldId("page"), false),
                ),
            ),
        ),
        capabilities = listOf(
            NodeSchemaCapability("core.capability.settings_launch"),
        ),
    )

    /** Cardinality per the plan §9.3 table: open-settings is single-target. */
    val cardinality: OperationCardinality = OperationCardinality.SINGLE_TARGET

    /** Declared selection semantics for the pilot family. */
    val semantics: NodeSelectionSemantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.SINGLE,
        executionMode = ExecutionMode.SINGLE,
    )
}
