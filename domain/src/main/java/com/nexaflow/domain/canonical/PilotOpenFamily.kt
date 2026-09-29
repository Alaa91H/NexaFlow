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
    internal val legacyPageMappings: Map<String, String> = linkedMapOf(
        "SYSTEM_OPEN_SETTINGS" to "WIFI",
        "SYSTEM_OPEN_ABOUT_PHONE" to "ABOUT_PHONE",
        "SYSTEM_OPEN_ACCESSIBILITY_SETTINGS" to "ACCESSIBILITY",
        "SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS" to "AIRPLANE_MODE",
        "SYSTEM_OPEN_APP_SETTINGS_LIST" to "APP_SETTINGS_LIST",
        "SYSTEM_OPEN_BATTERY_SETTINGS" to "BATTERY",
        "SYSTEM_OPEN_BLUETOOTH_SETTINGS" to "BLUETOOTH",
        "SYSTEM_OPEN_CAST_SETTINGS" to "CAST",
        "SYSTEM_OPEN_DATA_SAVER_SETTINGS" to "DATA_SAVER",
        "SYSTEM_OPEN_DATA_USAGE_SETTINGS" to "DATA_USAGE",
        "SYSTEM_OPEN_DATE_SETTINGS" to "DATE",
        "SYSTEM_OPEN_DEFAULT_APPS_SETTINGS" to "DEFAULT_APPS",
        "SYSTEM_OPEN_DEVELOPER_SETTINGS" to "DEVELOPER",
        "SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS" to "DEVICE_ADMIN",
        "SYSTEM_OPEN_DISPLAY_SETTINGS" to "DISPLAY",
        "SYSTEM_OPEN_INPUT_METHOD_SETTINGS" to "INPUT_METHOD",
        "SYSTEM_OPEN_LOCATION_SETTINGS" to "LOCATION",
        "SYSTEM_OPEN_NETWORK_SETTINGS" to "NETWORK",
        "SYSTEM_OPEN_NFC_SETTINGS" to "NFC",
        "SYSTEM_OPEN_NOTIFICATION_SETTINGS" to "NOTIFICATION",
        "SYSTEM_OPEN_PRINT_SETTINGS" to "PRINT",
        "SYSTEM_OPEN_PRIVACY_SETTINGS" to "PRIVACY",
        "SYSTEM_OPEN_SECURITY_SETTINGS" to "SECURITY",
        "SYSTEM_OPEN_SOUND_SETTINGS" to "SOUND",
        "SYSTEM_OPEN_STORAGE_SETTINGS" to "STORAGE",
        "SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS" to "SYSTEM_UPDATE",
        "SYSTEM_OPEN_USAGE_ACCESS_SETTINGS" to "USAGE_ACCESS",
        "SYSTEM_OPEN_VPN_SETTINGS" to "VPN",
        "SYSTEM_OPEN_WIFI_SETTINGS" to "WIFI",
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
            .filter { it.kind == LegacyNodeKind.ACTION && it.legacyType in legacyPageMappings }
            .associateBy { it.legacyType }

        val missing = legacyPageMappings.keys - generated.keys
        val unexpected = generated.keys - legacyPageMappings.keys
        if (missing.isNotEmpty() || unexpected.isNotEmpty()) {
            throw IllegalStateException(
                "T17 settings table drift: missing=$missing unexpected=$unexpected",
            )
        }
        return legacyPageMappings.map { (legacyType, page) ->
            SettingsPageRule(
                legacyType = legacyType,
                base = generated.getValue(legacyType),
                fixedPage = page,
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
                allowedTokens = legacyPageMappings.values.distinct().sorted(),
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
