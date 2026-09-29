package com.nexaflow.domain.canonical

/**
 * T17 — Pilot family: Open Settings.
 *
 * Only real Android settings launchers belong to this family. Other
 * SYSTEM_OPEN_* actions (apps, URL, camera, contacts, maps, recents, stores,
 * notification shade, quick settings...) keep their own reviewed families.
 *
 * Dedicated legacy launchers are normalized to one canonical
 * Open(core.system.settings) node with an explicit typed page token. The
 * historical SYSTEM_OPEN_SETTINGS action remains compatible with its optional
 * page config and its legacy WIFI default.
 */
object PilotOpenFamily {

    private val SETTINGS_TARGET = TargetId("core.system.settings")

    object Keys {
        const val PAGE = "page"
    }

    /**
     * Exhaustive T17 compatibility ledger. Keys are persisted legacy names;
     * values are the stable page tokens consumed by the canonical schema.
     */
    internal data class LegacySettingsPageMapping(
        val legacyType: String,
        val pageToken: String,
    )

    internal val legacyPageMappings: List<LegacySettingsPageMapping> = listOf(
        LegacySettingsPageMapping("SYSTEM_OPEN_SETTINGS", "WIFI"),
        LegacySettingsPageMapping("SYSTEM_OPEN_ABOUT_PHONE", "ABOUT_PHONE"),
        LegacySettingsPageMapping("SYSTEM_OPEN_ACCESSIBILITY_SETTINGS", "ACCESSIBILITY"),
        LegacySettingsPageMapping("SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS", "AIRPLANE_MODE"),
        LegacySettingsPageMapping("SYSTEM_OPEN_APP_SETTINGS_LIST", "APP_SETTINGS_LIST"),
        LegacySettingsPageMapping("SYSTEM_OPEN_BATTERY_SETTINGS", "BATTERY"),
        LegacySettingsPageMapping("SYSTEM_OPEN_BLUETOOTH_SETTINGS", "BLUETOOTH"),
        LegacySettingsPageMapping("SYSTEM_OPEN_CAST_SETTINGS", "CAST"),
        LegacySettingsPageMapping("SYSTEM_OPEN_DATA_SAVER_SETTINGS", "DATA_SAVER"),
        LegacySettingsPageMapping("SYSTEM_OPEN_DATA_USAGE_SETTINGS", "DATA_USAGE"),
        LegacySettingsPageMapping("SYSTEM_OPEN_DATE_SETTINGS", "DATE"),
        LegacySettingsPageMapping("SYSTEM_OPEN_DEFAULT_APPS_SETTINGS", "DEFAULT_APPS"),
        LegacySettingsPageMapping("SYSTEM_OPEN_DEVELOPER_SETTINGS", "DEVELOPER"),
        LegacySettingsPageMapping("SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS", "DEVICE_ADMIN"),
        LegacySettingsPageMapping("SYSTEM_OPEN_DISPLAY_SETTINGS", "DISPLAY"),
        LegacySettingsPageMapping("SYSTEM_OPEN_INPUT_METHOD_SETTINGS", "INPUT_METHOD"),
        LegacySettingsPageMapping("SYSTEM_OPEN_LOCATION_SETTINGS", "LOCATION"),
        LegacySettingsPageMapping("SYSTEM_OPEN_NETWORK_SETTINGS", "NETWORK"),
        LegacySettingsPageMapping("SYSTEM_OPEN_NFC_SETTINGS", "NFC"),
        LegacySettingsPageMapping("SYSTEM_OPEN_NOTIFICATION_SETTINGS", "NOTIFICATION"),
        LegacySettingsPageMapping("SYSTEM_OPEN_PRINT_SETTINGS", "PRINT"),
        LegacySettingsPageMapping("SYSTEM_OPEN_PRIVACY_SETTINGS", "PRIVACY"),
        LegacySettingsPageMapping("SYSTEM_OPEN_SECURITY_SETTINGS", "SECURITY"),
        LegacySettingsPageMapping("SYSTEM_OPEN_SOUND_SETTINGS", "SOUND"),
        LegacySettingsPageMapping("SYSTEM_OPEN_STORAGE_SETTINGS", "STORAGE"),
        LegacySettingsPageMapping("SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS", "SYSTEM_UPDATE"),
        LegacySettingsPageMapping("SYSTEM_OPEN_USAGE_ACCESS_SETTINGS", "USAGE_ACCESS"),
        LegacySettingsPageMapping("SYSTEM_OPEN_VPN_SETTINGS", "VPN"),
        LegacySettingsPageMapping("SYSTEM_OPEN_WIFI_SETTINGS", "WIFI"),
    )

    private class SettingsPageRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
        private val fixedPage: String,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> =
            if (legacyType == "SYSTEM_OPEN_SETTINGS") setOf(Keys.PAGE) else emptySet()
        // SYSTEM_OPEN_SETTINGS historically defaulted to WIFI when page was
        // absent, so page is deliberately optional at the adapter boundary.
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val page = if (legacyType == "SYSTEM_OPEN_SETTINGS") {
                input.entry(Keys.PAGE)?.rawValue ?: fixedPage
            } else {
                fixedPage
            }
            val allowed = openSettingsSchema()
                .field(CanonicalFieldId(Keys.PAGE))
                ?.allowedTokens
                .orEmpty()
            val typedPage = LegacyValueParsers.parseEnumToken(
                LegacyConfigEntry(Keys.PAGE, page),
                "core.system.settings",
                allowed,
            )
            return OpenNode(
                id = skeleton.id,
                target = skeleton.target,
                operation = skeleton.operation,
                arguments = CanonicalArguments(
                    listOf(CanonicalArgument(CanonicalFieldId(Keys.PAGE), typedPage)),
                ),
            )
        }
    }

    fun ruleOverrides(
        table: List<LegacyMappingRule> = LegacyMappingTable.all(),
    ): List<LegacyMappingRule> {
        val generated = table
            .filter {
                it.kind == LegacyNodeKind.ACTION &&
                    legacyPageMappings.any { mapping -> mapping.legacyType == it.legacyType }
            }
            .associateBy { it.legacyType }

        val expectedTypes = legacyPageMappings.mapTo(linkedSetOf()) { it.legacyType }
        val missing = expectedTypes - generated.keys
        val unexpected = generated.keys - expectedTypes
        if (missing.isNotEmpty() || unexpected.isNotEmpty()) {
            throw IllegalStateException(
                "T17 settings table drift: missing=$missing unexpected=$unexpected",
            )
        }
        return legacyPageMappings.map { mapping ->
            SettingsPageRule(
                legacyType = mapping.legacyType,
                base = generated.getValue(mapping.legacyType),
                fixedPage = mapping.pageToken,
            )
        }
    }

    fun adapterWithPilot(
        table: List<LegacyMappingRule> = LegacyMappingTable.all(),
    ): LegacyCanonicalAdapter {
        val overridden = ruleOverrides(table).associateBy { it.legacyType }
        return LegacyCanonicalAdapter(
            table.filter { it.legacyType !in overridden } + overridden.values,
        )
    }

    fun openSettingsSchema(): NodeSchema = NodeSchema(
        schemaId = "core.schema.system.settings.open",
        kind = NodeSchemaKind.ACTION,
        target = SETTINGS_TARGET,
        operation = OperationId("core.operation.open"),
        title = "Open settings",
        summaryTemplate = "Open {page}",
        securityClass = NodeSecurityClass.STANDARD,
        selectionMode = TargetSelectionMode.SINGLE,
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId(Keys.PAGE),
                type = NodeFieldType.ENUM_TOKEN,
                alwaysRequired = true,
                default = NodeFieldDefault.ofEnumToken("core.system.settings", "WIFI"),
                enumType = "core.system.settings",
                allowedTokens = (
                    legacyPageMappings.map { it.pageToken } + "SETTINGS"
                ).distinct().sorted(),
            ),
        ),
        capabilities = listOf(
            NodeSchemaCapability("core.capability.settings_launch"),
        ),
    )

    val cardinality: OperationCardinality = OperationCardinality.SINGLE_TARGET

    val semantics: NodeSelectionSemantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.SINGLE,
        executionMode = ExecutionMode.SINGLE,
    )
}
