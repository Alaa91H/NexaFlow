package com.nexaflow.domain.canonical

/**
 * T18 — Media & Navigation family (plan §T18).
 *
 * Same pattern as the T17 pilot: refine reviewed T15 mappings with typed
 * config upgrades — never re-map them. Media actions compose the transport
 * command from the reviewed targets; navigation actions carry no payload
 * beyond their reviewed identity. Android media-session limitations are
 * represented explicitly: an optional media-session package filter is a
 * typed PACKAGE_ID, and absent session targets surface PENDING semantics at
 * runtime (the family never fabricates a fallback session).
 */
object FamilyPhase18MediaNavigation {

    object Keys {
        const val PACKAGE = "package"
        const val QUERY = "query"
    }

    private val MEDIA_SESSION_TARGET = TargetId("core.media.active_session")
    private val NAVIGATION_TARGET = TargetId("core.system.navigation")

    /** Legacy SYSTEM_* media transport actions from the reviewed inventory. */
    private val MEDIA_ACTIONS = setOf(
        "SYSTEM_MEDIA_PLAY_PAUSE",
        "SYSTEM_MEDIA_NEXT",
        "SYSTEM_MEDIA_PREVIOUS",
        "SYSTEM_MEDIA_STOP",
        "SYSTEM_MEDIA_FAST_FORWARD",
        "SYSTEM_MEDIA_REWIND",
        "SYSTEM_MEDIA_PLAY_FROM_SEARCH",
    )

    /** Legacy navigation actions mapped to core.system.navigation/INVOKE. */
    private val NAVIGATION_ACTIONS = setOf(
        "SYSTEM_GO_HOME",
        "SYSTEM_OPEN_RECENTS",
        "SYSTEM_OPEN_NOTIFICATIONS",
        "SYSTEM_OPEN_QUICK_SETTINGS",
        "SYSTEM_OPEN_APP_DRAWER",
        "SYSTEM_EXPAND_STATUS_BAR",
        "SYSTEM_COLLAPSE_STATUS_BAR",
        "SYSTEM_STATUS_BAR_TOGGLE",
    )

    private class MediaRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.PACKAGE, Keys.QUERY)

        // Session package and search query are optional filters.
        override val requiredKeys: Set<String> =
            if (legacyType == "SYSTEM_MEDIA_PLAY_FROM_SEARCH") {
                setOf(Keys.QUERY)
            } else {
                emptySet()
            }

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val arguments = mutableListOf<CanonicalArgument>()

            input.entry(Keys.PACKAGE)?.let {
                arguments += CanonicalArgument(
                    CanonicalFieldId("sessionPackage"),
                    LegacyValueParsers.parsePackage(it),
                )
            }
            input.entry(Keys.QUERY)?.let {
                arguments += CanonicalArgument(
                    CanonicalFieldId("query"),
                    LegacyValueParsers.parseText(it),
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

    private class NavigationRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.PACKAGE)

        // The app filter is optional; identity carries the navigation intent.
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val arguments = mutableListOf<CanonicalArgument>()
            input.entry(Keys.PACKAGE)?.let {
                // Home/recents app filters are strictly typed package ids.
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

    /** Verified membership against the generated table (fail closed on drift). */
    private fun membershipCheck(table: List<LegacyMappingRule>) {
        val actionNames = table
            .filter { it.kind == LegacyNodeKind.ACTION }
            .map { it.legacyType }
            .toSet()
        val media = MEDIA_ACTIONS.filter { it in actionNames }.toSet()
        val navigation = NAVIGATION_ACTIONS.filter { it in actionNames }.toSet()
        if (media.isEmpty() || navigation.isEmpty()) {
            throw IllegalStateException(
                "T15 table drift: media=${media.size} navigation=${navigation.size}",
            )
        }
    }

    /** Overrides for the family members present in the generated table. */
    fun ruleOverrides(table: List<LegacyMappingRule> = LegacyMappingTable.all()): List<LegacyMappingRule> {
        membershipCheck(table)
        val generated = table
            .filter { it.kind == LegacyNodeKind.ACTION }
            .filter { it.legacyType in MEDIA_ACTIONS || it.legacyType in NAVIGATION_ACTIONS }
            .associateBy { it.legacyType }

        return generated.map { (legacyType, base) ->
            if (legacyType in MEDIA_ACTIONS) MediaRule(legacyType, base)
            else NavigationRule(legacyType, base)
        }
    }

    /** Adapter with the family overrides merged over the full table. */
    fun adapterWithFamily(
        table: List<LegacyMappingRule> = LegacyMappingTable.all(),
    ): LegacyCanonicalAdapter {
        val overridden = ruleOverrides(table).associateBy { it.legacyType }
        return LegacyCanonicalAdapter(table.filter { it.legacyType !in overridden } + overridden.values)
    }

    /**
     * Multi-target semantics for media control: several session packages can
     * be addressed in one ordered execution (later writes win), but the
     * transport command itself is never a contradictory batch (T05/T06).
     */
    val mediaCardinality: OperationCardinality = OperationCardinality(
        minTargets = 1,
        maxTargets = 4,
    )

    val mediaSemantics: NodeSelectionSemantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.MULTI,
        executionMode = ExecutionMode.ORDERED,
        failurePolicy = FailurePolicy.CONTINUE_ON_ERROR,
    )

    val navigationSemantics: NodeSelectionSemantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.SINGLE,
        executionMode = ExecutionMode.SINGLE,
    )

    /** The media schema: transport command + typed session filter. */
    fun mediaSchema(): NodeSchema = NodeSchema(
        schemaId = "core.schema.media.active_session.invoke",
        kind = NodeSchemaKind.ACTION,
        target = MEDIA_SESSION_TARGET,
        operation = OperationId("core.operation.invoke"),
        title = "Media control",
        summaryTemplate = "Media {{session {sessionPackage}}}{{: {query}}}",
        securityClass = NodeSecurityClass.STANDARD,
        selectionMode = TargetSelectionMode.MULTI,
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("sessionPackage"),
                type = NodeFieldType.PACKAGE_ID,
                level = NodeSchemaLevel.ADVANCED,
                helpText = "Restrict the transport command to one media session",
            ),
            NodeSchemaField(
                id = CanonicalFieldId("query"),
                type = NodeFieldType.TEXT,
                level = NodeSchemaLevel.ADVANCED,
                maximum = 256,
            ),
        ),
        capabilities = listOf(
            NodeSchemaCapability("core.capability.intent_launch"),
        ),
    )

    /** The navigation schema: identity-only, no payload fields. */
    fun navigationSchema(): NodeSchema = NodeSchema(
        schemaId = "core.schema.system.navigation.invoke",
        kind = NodeSchemaKind.ACTION,
        target = NAVIGATION_TARGET,
        operation = OperationId("core.operation.invoke"),
        title = "System navigation",
        summaryTemplate = "Navigate",
        securityClass = NodeSecurityClass.STANDARD,
        selectionMode = TargetSelectionMode.SINGLE,
        fields = emptyList(),
        capabilities = listOf(
            NodeSchemaCapability("core.capability.intent_launch"),
        ),
    )
}
