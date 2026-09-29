package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyPhase18MediaNavigationTest {

    private val baseAdapter = LegacyCanonicalAdapter(LegacyMappingTable.all())
    private val familyAdapter = FamilyPhase18MediaNavigation.adapterWithFamily()

    @Test
    fun familyOverridesCoverOnlyTableMembers() {
        val overrides = FamilyPhase18MediaNavigation.ruleOverrides()
        assertTrue("expected media+navigation overrides", overrides.size >= 10)

        val generatedNames = LegacyMappingTable.all().map { it.legacyType }.toSet()
        assertTrue(overrides.all { it.legacyType in generatedNames })
    }

    @Test
    fun familyAdapterKeepsTheFullTable() {
        assertEquals(233, familyAdapter.declaredRules)
    }

    @Test
    fun mediaTargetsPreserveParityWithTheSkeletonTable() {
        // PLAY_FROM_SEARCH needs its required query; parity for it is covered
        // by searchRequiresQueryButPackageStaysOptional.
        val requiredPayload = setOf("SYSTEM_MEDIA_PLAY_FROM_SEARCH")
        for (rule in FamilyPhase18MediaNavigation.ruleOverrides()) {
            val config = if (rule.legacyType in requiredPayload) {
                listOf(LegacyConfigEntry("query", "jazz"))
            } else {
                emptyList()
            }
            val input = LegacyNodeInput(rule.legacyType, LegacyNodeKind.ACTION, config)
            val skeleton = baseAdapter.canonicalize(input) as LegacyAdapterOutcome.Canonicalized
            val family = familyAdapter.canonicalize(input) as LegacyAdapterOutcome.Canonicalized

            val skeletonNode = skeleton.node as InvokeNode
            val familyNode = family.node as InvokeNode
            assertEquals(rule.legacyType, skeletonNode.target, familyNode.target)
            assertEquals(rule.legacyType, skeletonNode.operation, familyNode.operation)
        }
    }

    @Test
    fun mediaCommandIdentitySurvivesCanonicalization() {
        val expected = linkedMapOf(
            "SYSTEM_MEDIA_PLAY_PAUSE" to "PLAY_PAUSE",
            "SYSTEM_MEDIA_NEXT" to "NEXT",
            "SYSTEM_MEDIA_PREVIOUS" to "PREVIOUS",
            "SYSTEM_MEDIA_STOP" to "STOP",
            "SYSTEM_MEDIA_FAST_FORWARD" to "FAST_FORWARD",
            "SYSTEM_MEDIA_REWIND" to "REWIND",
            "SYSTEM_MEDIA_PLAY_FROM_SEARCH" to "PLAY_FROM_SEARCH",
        )
        for ((legacyType, token) in expected) {
            val config = if (legacyType == "SYSTEM_MEDIA_PLAY_FROM_SEARCH") {
                listOf(LegacyConfigEntry("query", "jazz"))
            } else {
                emptyList()
            }
            val outcome = familyAdapter.canonicalize(
                LegacyNodeInput(legacyType, LegacyNodeKind.ACTION, config),
            ) as LegacyAdapterOutcome.Canonicalized
            val node = outcome.node as InvokeNode
            assertEquals(
                EnumTokenValue("core.media.command", token),
                node.arguments[CanonicalFieldId("command")],
            )
        }
    }

    @Test
    fun navigationDestinationIdentitySurvivesCanonicalization() {
        val expected = linkedMapOf(
            "SYSTEM_GO_HOME" to "HOME",
            "SYSTEM_OPEN_RECENTS" to "RECENTS",
            "SYSTEM_OPEN_NOTIFICATIONS" to "NOTIFICATIONS",
            "SYSTEM_OPEN_QUICK_SETTINGS" to "QUICK_SETTINGS",
            "SYSTEM_OPEN_APP_DRAWER" to "APP_DRAWER",
            "SYSTEM_EXPAND_STATUS_BAR" to "EXPAND_STATUS_BAR",
            "SYSTEM_COLLAPSE_STATUS_BAR" to "COLLAPSE_STATUS_BAR",
            "SYSTEM_STATUS_BAR_TOGGLE" to "STATUS_BAR_TOGGLE",
        )
        for ((legacyType, token) in expected) {
            val outcome = familyAdapter.canonicalize(
                LegacyNodeInput(legacyType, LegacyNodeKind.ACTION, emptyList()),
            ) as LegacyAdapterOutcome.Canonicalized
            val node = outcome.node as InvokeNode
            assertEquals(
                EnumTokenValue("core.system.navigation.destination", token),
                node.arguments[CanonicalFieldId("destination")],
            )
        }
    }

    @Test
    fun mediaSessionFilterUpgradesToTypedPackage() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_MEDIA_NEXT",
                kind = LegacyNodeKind.ACTION,
                config = listOf(LegacyConfigEntry("package", "com.example.player")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as InvokeNode
        assertEquals(
            PackageIdValue("com.example.player"),
            node.arguments[CanonicalFieldId("sessionPackage")],
        )
    }

    @Test
    fun bogusSessionPackageIsRejectedNotCoerced() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_MEDIA_PLAY_FROM_SEARCH",
                kind = LegacyNodeKind.ACTION,
                config = listOf(
                    LegacyConfigEntry("query", "jazz playlist"),
                    LegacyConfigEntry("package", "player app"),
                ),
            ),
        )
        assertEquals(
            LegacyAdapterRejection.UNPARSABLE_CONFIG_VALUE,
            (outcome as LegacyAdapterOutcome.Rejected).reason,
        )
    }

    @Test
    fun searchRequiresQueryButPackageStaysOptional() {
        // PLAY_FROM_SEARCH declares query as required, package optional.
        val missing = familyAdapter.canonicalize(
            LegacyNodeInput("SYSTEM_MEDIA_PLAY_FROM_SEARCH", LegacyNodeKind.ACTION, emptyList()),
        )
        assertEquals(
            LegacyAdapterRejection.MISSING_REQUIRED_CONFIG,
            (missing as LegacyAdapterOutcome.Rejected).reason,
        )

        val ok = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_MEDIA_PLAY_FROM_SEARCH",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("query", "jazz")),
            ),
        )
        val node = (ok as LegacyAdapterOutcome.Canonicalized).node as InvokeNode
        assertEquals(TextValue("jazz"), node.arguments[CanonicalFieldId("query")])
    }

    @Test
    fun mediaMultiTargetSemanticsAreExecutable() {
        val errors = validateSelectionSemantics(
            semantics = FamilyPhase18MediaNavigation.mediaSemantics,
            selectedTargetCount = 3,
            cardinality = FamilyPhase18MediaNavigation.mediaCardinality,
            plannedWrites = emptyList(),
        )
        assertTrue("expected no violations, got $errors", errors.isEmpty())
    }

    @Test
    fun mediaCardinalityRejectsBeyondFourTargets() {
        val errors = validateSelectionSemantics(
            semantics = FamilyPhase18MediaNavigation.mediaSemantics,
            selectedTargetCount = 5,
            cardinality = FamilyPhase18MediaNavigation.mediaCardinality,
        )
        assertTrue(errors.any { it is CardinalityViolation })
    }

    @Test
    fun navigationSemanticsAreSingleTarget() {
        val errors = validateSelectionSemantics(
            semantics = FamilyPhase18MediaNavigation.navigationSemantics,
            selectedTargetCount = 1,
            cardinality = OperationCardinality.SINGLE_TARGET,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun mediaSchemaValidatesAndRejectsBogusSessionFilter() {
        val schema = FamilyPhase18MediaNavigation.mediaSchema()

        val valid = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(
                    CanonicalFieldId("command"),
                    EnumTokenValue("core.media.command", "NEXT"),
                ),
                NodeFieldValue(
                    CanonicalFieldId("sessionPackage"),
                    PackageIdValue("com.example.player"),
                ),
            ),
        )
        assertTrue(valid.isEmpty())

        val wrongKind = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(
                    CanonicalFieldId("command"),
                    EnumTokenValue("core.media.command", "NEXT"),
                ),
                NodeFieldValue(
                    CanonicalFieldId("sessionPackage"),
                    TextValue("com.example.player"),
                ),
            ),
        )
        assertTrue(wrongKind.any { it is FieldTypeMismatch })
    }

    @Test
    fun navigationSchemaRequiresTypedDestination() {
        val schema = FamilyPhase18MediaNavigation.navigationSchema()
        val missing = validateNodeValues(schema, emptyList())
        assertTrue(missing.any { it is MissingRequiredField })

        val valid = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(
                    CanonicalFieldId("destination"),
                    EnumTokenValue("core.system.navigation.destination", "HOME"),
                ),
            ),
        )
        assertTrue(valid.isEmpty())
    }

    @Test
    fun canonicalizationRemainsIdempotent() {
        for (rule in FamilyPhase18MediaNavigation.ruleOverrides()) {
            val input = LegacyNodeInput(
                rule.legacyType,
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("package", "com.example.player")),
            )
            assertEquals(
                familyAdapter.canonicalize(input),
                familyAdapter.canonicalize(input),
            )
        }
    }
}
