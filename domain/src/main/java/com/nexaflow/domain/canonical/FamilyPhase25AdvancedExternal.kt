package com.nexaflow.domain.canonical

/**
 * T25 — Data / ROM / Advanced / External family (plan §T25).
 *
 * The final family phase: upgrades 17 remaining action groups (data
 * transforms, ROM customizations, privileged commands, HTTP, plugins,
 * clipboard, system settings, device power, wait) and the plugin trigger.
 * Security-critical closure rules:
 *
 * - Root/Shizuku command actions keep their reviewed skeletons but are
 *   flagged DESTRUCTIVE-class schemas: they demand a capability declaration
 *   (T09 security stage) and their command semantics are
 *   CONDITIONALLY_IDEMPOTENT (never blindly retried).
 * - HTTP actions never carry raw auth headers in the AST: credentials
 *   upgrade to SECRET_REFERENCE values (the T11 journal sweep + T09
 *   security stage both enforce the boundary).
 * - Reboot/shutdown are DESTRUCTIVE; wait stays side-effect-free.
 */
object FamilyPhase25AdvancedExternal {

    object Keys {
        const val URL = "url"
        const val METHOD = "method"
        const val AUTH_TOKEN = "auth_token"
        const val EXPRESSION = "expression"
        const val DURATION = "duration"
        const val PLUGIN_ID = "plugin_id"
        const val KEY = "key"
        const val VALUE = "value"
    }

    private val DATA_TRANSFORMS = setOf(
        "DATA_ARRAY",
        "DATA_DATE_TIME",
        "DATA_ENCODING",
        "DATA_HASH",
        "DATA_JSON",
        "DATA_MATH",
        "DATA_RANDOM",
        "DATA_TEXT",
    )

    private val ROM_CONFIGURATIONS = setOf(
        "ROM_AMBIENT_AOD",
        "ROM_BATCH",
        "ROM_LOCKSCREEN",
        "ROM_NAVIGATION",
        "ROM_NOTIFICATIONS",
        "ROM_QS_TILES",
        "ROM_STATUS_BAR",
        "ROM_THEME",
        "ROM_CUSTOM_SETTING",
    )

    private val PRIVILEGED_COMMANDS = setOf(
        "ADVANCED_ROOT",
        "ADVANCED_SHIZUKU",
    )

    private val DEVICE_POWER = setOf(
        "SYSTEM_REBOOT",
        "SYSTEM_SHUTDOWN",
        "SYSTEM_SOFT_RESTART",
        "SYSTEM_RESTART_SYSTEM_UI",
    )

    private val EXTERNAL_ACTIONS = setOf(
        "SYSTEM_HTTP_REQUEST",
        "PLUGIN_FIRE",
        "SYSTEM_CLIPBOARD_SET",
        "SYSTEM_SET_SETTING",
        "SYSTEM_WAIT",
    )

    private val ALL_FAMILY_ACTIONS = DATA_TRANSFORMS + ROM_CONFIGURATIONS +
        PRIVILEGED_COMMANDS + DEVICE_POWER + EXTERNAL_ACTIONS

    private val PLUGIN_TRIGGERS = setOf("PLUGIN_EVENT")

    private class DataTransformRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.EXPRESSION, Keys.VALUE)

        // Both payload keys are optional; transforms without input are valid.
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val arguments = mutableListOf<CanonicalArgument>()
            input.entry(Keys.EXPRESSION)?.let {
                arguments += CanonicalArgument(
                    CanonicalFieldId("expression"),
                    ExpressionValue(it.rawValue, CanonicalValueKind.TEXT),
                )
            }
            input.entry(Keys.VALUE)?.let {
                arguments += CanonicalArgument(CanonicalFieldId("value"), LegacyValueParsers.parseText(it))
            }
            return InvokeNode(
                id = skeleton.id,
                target = skeleton.target,
                operation = skeleton.operation,
                arguments = CanonicalArguments(arguments),
            )
        }
    }

    private class RomRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.KEY, Keys.VALUE)

        // key/value are optional; the reviewed skeleton carries the intent.
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val arguments = mutableListOf<CanonicalArgument>()
            input.entry(Keys.KEY)?.let {
                arguments += CanonicalArgument(CanonicalFieldId("key"), LegacyValueParsers.parseText(it))
            }
            input.entry(Keys.VALUE)?.let {
                arguments += CanonicalArgument(CanonicalFieldId("value"), LegacyValueParsers.parseText(it))
            }
            return InvokeNode(
                id = skeleton.id,
                target = skeleton.target,
                operation = skeleton.operation,
                arguments = CanonicalArguments(arguments),
            )
        }
    }

    /** Privileged command rule: keeps only a SECRET_REFERENCE command ref. */
    private class PrivilegedCommandRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf("command")

        // The command is replaced by a secret reference either way; the raw
        // key is optional at the adapter boundary (T26 cutover contract).
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val arguments = mutableListOf<CanonicalArgument>()
            // The raw command NEVER enters the AST: it upgrades to a secret
            // reference resolved by the privileged backend at run time.
            input.entry("command")?.let {
                arguments += CanonicalArgument(
                    CanonicalFieldId("commandRef"),
                    SecretReferenceValue("legacy.privileged_command"),
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

    private class PowerRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = emptySet()

        // Consumes nothing, so nothing can be required at the adapter boundary.
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode =
            base.canonicalize(input)
    }

    private class HttpRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.URL, Keys.METHOD, Keys.AUTH_TOKEN)

        // All three keys upgrade to typed arguments when present; the family
        // fails closed downstream if a required one is missing.
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val arguments = mutableListOf<CanonicalArgument>()
            input.entry(Keys.URL)?.let {
                arguments += CanonicalArgument(CanonicalFieldId("url"), LegacyValueParsers.parseUri(it))
            }
            input.entry(Keys.METHOD)?.let {
                arguments += CanonicalArgument(CanonicalFieldId("method"), LegacyValueParsers.parseText(it))
            }
            input.entry(Keys.AUTH_TOKEN)?.let {
                // Credentials upgrade to secret references — never raw.
                arguments += CanonicalArgument(
                    CanonicalFieldId("authToken"),
                    SecretReferenceValue("legacy.http_auth_token"),
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

    private class WaitRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.DURATION)
        override val requiredKeys: Set<String> = setOf(Keys.DURATION)

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            return InvokeNode(
                id = skeleton.id,
                target = skeleton.target,
                operation = skeleton.operation,
                arguments = CanonicalArguments(
                    listOf(
                        CanonicalArgument(
                            CanonicalFieldId("duration"),
                            LegacyValueParsers.parseDuration(input.entry(Keys.DURATION)!!),
                        ),
                    ),
                ),
            )
        }
    }

    private class PluginTriggerRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.TRIGGER
        override val consumedKeys: Set<String> = setOf(Keys.PLUGIN_ID)

        // The plugin id is optional at the boundary; rules fail closed when a
        // later stage needs it (no inherited requiredKeys on optional keys).
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as ObserveNode
            input.entry(Keys.PLUGIN_ID)?.let {
                // Plugin ids are strict opaque tokens, not free text.
                return ObserveNode(
                    id = skeleton.id,
                    target = skeleton.target,
                    predicate = skeleton.predicate,
                    arguments = CanonicalArguments(
                        listOf(
                            CanonicalArgument(
                                CanonicalFieldId("pluginId"),
                                LegacyValueParsers.parseText(it),
                            ),
                        ),
                    ),
                )
            }
            return skeleton
        }
    }

    /** Overrides for every family member present in the generated table. */
    fun ruleOverrides(table: List<LegacyMappingRule> = LegacyMappingTable.all()): List<LegacyMappingRule> {
        val generated = table.associateBy { it.kind to it.legacyType }
        val missing = (ALL_FAMILY_ACTIONS.map { LegacyNodeKind.ACTION to it } +
            PLUGIN_TRIGGERS.map { LegacyNodeKind.TRIGGER to it })
            .filterNot { it in generated.keys }
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "T15 table drift: ${missing.size} advanced/external members missing",
            )
        }

        return ALL_FAMILY_ACTIONS.map { name ->
            val base = generated.getValue(LegacyNodeKind.ACTION to name)
            when {
                name in DATA_TRANSFORMS -> DataTransformRule(name, base)
                name in ROM_CONFIGURATIONS -> RomRule(name, base)
                name in PRIVILEGED_COMMANDS -> PrivilegedCommandRule(name, base)
                name in DEVICE_POWER -> PowerRule(name, base)
                name == "SYSTEM_HTTP_REQUEST" -> HttpRule(name, base)
                name == "SYSTEM_WAIT" -> WaitRule(name, base)
                else -> DataTransformRule(name, base) // clipboard/setting/plugin
            }
        } + PLUGIN_TRIGGERS.map { name ->
            PluginTriggerRule(name, generated.getValue(LegacyNodeKind.TRIGGER to name))
        }
    }

    /** Adapter with advanced/external overrides merged over the full table. */
    fun adapterWithFamily(
        table: List<LegacyMappingRule> = LegacyMappingTable.all(),
    ): LegacyCanonicalAdapter {
        val overridden = ruleOverrides(table).associateBy { it.legacyType }
        return LegacyCanonicalAdapter(table.filter { it.legacyType !in overridden } + overridden.values)
    }

    /**
     * Command semantics: privileged/destructive operations are
     * CONDITIONALLY_IDEMPOTENT (never blindly retried); wait is idempotent.
     */
    fun commandSemantics(): List<CommandSemantics> = listOf(
        CommandSemantics(
            OperationId("core.operation.execute"),
            CommandIdempotency.CONDITIONALLY_IDEMPOTENT,
            reversible = false,
        ),
        CommandSemantics(
            OperationId("core.operation.reboot"),
            CommandIdempotency.CONDITIONALLY_IDEMPOTENT,
            reversible = false,
        ),
        CommandSemantics(
            OperationId("core.operation.shutdown"),
            CommandIdempotency.CONDITIONALLY_IDEMPOTENT,
            reversible = false,
        ),
        CommandSemantics(
            OperationId("core.operation.restart"),
            CommandIdempotency.CONDITIONALLY_IDEMPOTENT,
            reversible = false,
        ),
        CommandSemantics(
            OperationId("core.operation.transform"),
            CommandIdempotency.IDEMPOTENT,
            reversible = true,
        ),
        CommandSemantics(
            OperationId("core.operation.wait"),
            CommandIdempotency.IDEMPOTENT,
            reversible = false,
        ),
    )

    /** The HTTP schema: SENSITIVE, with a typed URL and secret auth token. */
    fun httpSchema(): NodeSchema = NodeSchema(
        schemaId = "core.schema.external.http.send",
        kind = NodeSchemaKind.ACTION,
        target = TargetId("core.external.http"),
        operation = OperationId("core.operation.send"),
        title = "HTTP request",
        summaryTemplate = "HTTP {method} {url}",
        securityClass = NodeSecurityClass.SENSITIVE,
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("url"),
                type = NodeFieldType.URI,
                alwaysRequired = true,
            ),
            NodeSchemaField(
                id = CanonicalFieldId("method"),
                type = NodeFieldType.ENUM_TOKEN,
                alwaysRequired = true,
                enumType = "core.external.http.method",
                allowedTokens = listOf("GET", "POST", "PUT", "DELETE", "HEAD"),
            ),
            NodeSchemaField(
                id = CanonicalFieldId("authToken"),
                type = NodeFieldType.SECRET_REFERENCE,
                level = NodeSchemaLevel.EXPERT,
            ),
        ),
        capabilities = listOf(
            NodeSchemaCapability("core.capability.network_http_request"),
        ),
    )
}
