package com.nexaflow.domain.canonical

/**
 * T21 — Applications family (plan §T21).
 *
 * Upgrades 17 app actions and 2 app triggers over the reviewed mappings.
 * The plan §9.3 cardinality table is now executable per operation:
 *
 * | Operation   | Target mode | Safety                                     |
 * |-------------|-------------|--------------------------------------------|
 * | open/launch | SINGLE      | —                                          |
 * | force stop  | MULTI       | idempotent, reversible                     |
 * | enable      | MULTI       | idempotent, reversible                     |
 * | clear data  | MULTI       | IRREVERSIBLE → destructive semantics       |
 * | uninstall   | MULTI       | IRREVERSIBLE → destructive semantics       |
 * | install     | SINGLE      | IRREVERSIBLE → destructive semantics       |
 *
 * Destructive operations (clear-data, uninstall, install) carry
 * [CommandIdempotency.CONDITIONALLY_IDEMPOTENT] command semantics and are
 * hard-wired to the DESTRUCTIVE security class: their schema refuses drafts
 * without a capability declaration (T09 security stage), and their planner
 * commands never inherit blind retry (plan rule 46.14).
 */
object FamilyPhase21Applications {

    object Keys {
        const val PACKAGE = "package"
        const val PACKAGES = "packages"
    }

    /** Open-family actions: strictly single-target. */
    private val OPEN_ACTIONS = setOf(
        "APPLICATION_LAUNCH_APP",
        "APPLICATION_OPEN_APP_SETTINGS",
        "SYSTEM_OPEN_APP",
        "SYSTEM_OPEN_CAMERA",
        "SYSTEM_OPEN_CONTACTS",
        "SYSTEM_OPEN_DEVICE_STORE",
        "SYSTEM_OPEN_PLAY_STORE_APP",
        "SYSTEM_OPEN_PLAY_UPDATES",
        "SYSTEM_TOGGLE_PIP",
    )

    /** Multi-target package actions with reversible side effects. */
    private val PACKAGE_MULTI_ACTIONS = setOf(
        "APPLICATION_CLOSE_APP",
        "SYSTEM_FORCE_STOP_APP",
        "SYSTEM_ENABLE_APP",
        "SYSTEM_DISABLE_APP",
    )

    /** Multi-target destructive actions. */
    private val DESTRUCTIVE_MULTI_ACTIONS = setOf(
        "SYSTEM_CLEAR_APP_DATA",
        "SYSTEM_UNINSTALL_APP",
    )

    /** Single-target destructive installs. */
    private val DESTRUCTIVE_SINGLE_ACTIONS = setOf(
        "SYSTEM_INSTALL_APK",
    )

    /** Store/update actions with optional package filters. */
    private val STORE_ACTIONS = setOf(
        "SYSTEM_UPDATE_GOOGLE_PLAY_APPS",
    )

    private val APP_TRIGGERS = setOf("APPLICATION", "APP_INSTALLED")

    private val ALL_FAMILY_ACTIONS = OPEN_ACTIONS + PACKAGE_MULTI_ACTIONS +
        DESTRUCTIVE_MULTI_ACTIONS + DESTRUCTIVE_SINGLE_ACTIONS + STORE_ACTIONS

    private class OpenAppRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.PACKAGE)

        // Launching without a package filter is parity-preserving legacy
        // behavior; the package stays optional.
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val arguments = mutableListOf<CanonicalArgument>()
            input.entry(Keys.PACKAGE)?.let {
                arguments += CanonicalArgument(
                    CanonicalFieldId("packageName"),
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

    /** Package-list rule: consumes a typed package list (multi-target). */
    private class PackageListRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
        private val destructive: Boolean,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.PACKAGE, Keys.PACKAGES)

        // Either key satisfies the requirement; the canonicalize body fails
        // closed when both are absent.
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val entry = input.entry(Keys.PACKAGES) ?: input.entry(Keys.PACKAGE)
                ?: throw IllegalArgumentException("requires a package selection")
            val packages = entry.rawValue.split('|', ';', ',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { PackageIdValue(it) }
            if (packages.isEmpty()) {
                throw IllegalArgumentException("empty package selection")
            }
            return InvokeNode(
                id = skeleton.id,
                target = skeleton.target,
                operation = skeleton.operation,
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

    private class InstallApkRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf("path")
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val path = input.entry("path") ?: return skeleton
            return InvokeNode(
                id = skeleton.id,
                target = skeleton.target,
                operation = skeleton.operation,
                arguments = CanonicalArguments(
                    listOf(
                        CanonicalArgument(
                            CanonicalFieldId("path"),
                            LegacyValueParsers.parseText(path),
                        ),
                    ),
                ),
            )
        }
    }

    private class AppTriggerRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.TRIGGER
        override val consumedKeys: Set<String> = setOf(Keys.PACKAGE, Keys.PACKAGES)

        // Package filters are optional for triggers: no filter = any app.
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as ObserveNode
            val entry = input.entry(Keys.PACKAGES) ?: input.entry(Keys.PACKAGE)
                ?: return skeleton // no filter: any app matches (ANY_OF)
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
            APP_TRIGGERS.map { LegacyNodeKind.TRIGGER to it })
            .filterNot { it in generated.keys }
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "T15 table drift: ${missing.size} applications members missing",
            )
        }

        return ALL_FAMILY_ACTIONS.map { name ->
            val base = generated.getValue(LegacyNodeKind.ACTION to name)
            when {
                name in OPEN_ACTIONS -> OpenAppRule(name, base)
                name in PACKAGE_MULTI_ACTIONS ->
                    PackageListRule(name, base, destructive = false)
                name in DESTRUCTIVE_MULTI_ACTIONS ->
                    PackageListRule(name, base, destructive = true)
                name in DESTRUCTIVE_SINGLE_ACTIONS ->
                    InstallApkRule(name, base)
                else -> OpenAppRule(name, base) // store updates: optional filter
            }
        } + APP_TRIGGERS.map { name ->
            AppTriggerRule(name, generated.getValue(LegacyNodeKind.TRIGGER to name))
        }
    }

    /** Adapter with applications overrides merged over the full table. */
    fun adapterWithFamily(
        table: List<LegacyMappingRule> = LegacyMappingTable.all(),
    ): LegacyCanonicalAdapter {
        val overridden = ruleOverrides(table).associateBy { it.legacyType }
        return LegacyCanonicalAdapter(table.filter { it.legacyType !in overridden } + overridden.values)
    }

    /** §9.3 table, executable: open/launch/settings are strictly single. */
    val openCardinality: OperationCardinality = OperationCardinality.SINGLE_TARGET

    /** Force-stop / enable / disable accept bounded multi-target. */
    val packageMultiCardinality: OperationCardinality = OperationCardinality(
        minTargets = 1,
        maxTargets = 20,
    )

    /** Destructive multi-target actions are capped lower (safety). */
    val destructiveMultiCardinality: OperationCardinality = OperationCardinality(
        minTargets = 1,
        maxTargets = 5,
    )

    /** Launch semantics: single target, ordered, fail fast. */
    val launchSemantics: NodeSelectionSemantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.SINGLE,
        executionMode = ExecutionMode.SINGLE,
    )

    /** Package batch semantics: multi, ordered, continue on error. */
    val packageBatchSemantics: NodeSelectionSemantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.MULTI,
        executionMode = ExecutionMode.ORDERED,
        failurePolicy = FailurePolicy.CONTINUE_ON_ERROR,
    )

    /** Destructive semantics: multi, ordered, fail fast (safety first). */
    val destructiveSemantics: NodeSelectionSemantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.MULTI,
        executionMode = ExecutionMode.ORDERED,
        failurePolicy = FailurePolicy.FAIL_FAST,
    )

    /**
     * Command semantics the planner must use for package operations:
     * destructive ones are CONDITIONALLY_IDEMPOTENT (plan §18) so blind
     * retries can never be planned.
     */
    fun commandSemantics(): List<CommandSemantics> = listOf(
        CommandSemantics(
            OperationId("core.operation.force_stop"),
            CommandIdempotency.IDEMPOTENT,
            reversible = true,
        ),
        CommandSemantics(
            OperationId("core.operation.set_enabled"),
            CommandIdempotency.IDEMPOTENT,
            reversible = true,
        ),
        CommandSemantics(
            OperationId("core.operation.open"),
            CommandIdempotency.CONDITIONALLY_IDEMPOTENT,
            reversible = false,
        ),
        CommandSemantics(
            OperationId("core.operation.clear_data"),
            CommandIdempotency.CONDITIONALLY_IDEMPOTENT,
            reversible = false,
        ),
        CommandSemantics(
            OperationId("core.operation.uninstall"),
            CommandIdempotency.CONDITIONALLY_IDEMPOTENT,
            reversible = false,
        ),
        CommandSemantics(
            OperationId("core.operation.install"),
            CommandIdempotency.CONDITIONALLY_IDEMPOTENT,
            reversible = false,
        ),
    )

    /** The destructive uninstall schema (DESTRUCTIVE security class). */
    fun uninstallSchema(): NodeSchema = NodeSchema(
        schemaId = "core.schema.application.package.uninstall",
        kind = NodeSchemaKind.ACTION,
        target = TargetId("core.application.package"),
        operation = OperationId("core.operation.uninstall"),
        title = "Uninstall app",
        summaryTemplate = "Uninstall {packages}",
        securityClass = NodeSecurityClass.DESTRUCTIVE,
        selectionMode = TargetSelectionMode.MULTI,
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("packages"),
                type = NodeFieldType.COLLECTION,
                collectionElementKind = CanonicalValueKind.PACKAGE_ID,
                alwaysRequired = true,
            ),
        ),
        capabilities = listOf(
            NodeSchemaCapability("core.capability.package_uninstall"),
        ),
    )
}
