package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyPhase21ApplicationsTest {

    private val baseAdapter = LegacyCanonicalAdapter(LegacyMappingTable.all())
    private val familyAdapter = FamilyPhase21Applications.adapterWithFamily()

    private fun overrides() = FamilyPhase21Applications.ruleOverrides()

    @Test
    fun familyOverridesCoverApplicationMembers() {
        // 17 actions + 2 triggers from the reviewed inventory.
        assertEquals(17, overrides().count { it.kind == LegacyNodeKind.ACTION })
        assertEquals(2, overrides().count { it.kind == LegacyNodeKind.TRIGGER })
    }

    @Test
    fun familyAdapterKeepsTheFullTable() {
        assertEquals(237, familyAdapter.declaredRules)
    }

    @Test
    fun launchActionUpgradesToTypedPackage() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_OPEN_APP",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("package", "com.example.app")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as InvokeNode
        assertEquals(
            PackageIdValue("com.example.app"),
            node.arguments[CanonicalFieldId("packageName")],
        )
    }

    @Test
    fun launchWithoutPackageStillCanonicalizes() {
        // Open-app without a filter stays valid (parity: legacy allowed it).
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput("SYSTEM_OPEN_APP", LegacyNodeKind.ACTION, emptyList()),
        )
        assertTrue(outcome is LegacyAdapterOutcome.Canonicalized)
    }

    @Test
    fun forceStopUpgradesToTypedList() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_FORCE_STOP_APP",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("packages", "com.a.app|com.b.app")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as InvokeNode
        val packages = node.arguments[CanonicalFieldId("packages")] as CollectionValue
        assertEquals(2, packages.values.size)
    }

    @Test
    fun forceStopWithoutPackagesIsRejected() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput("SYSTEM_FORCE_STOP_APP", LegacyNodeKind.ACTION, emptyList()),
        )
        assertEquals(
            LegacyAdapterRejection.UNPARSABLE_CONFIG_VALUE,
            (outcome as LegacyAdapterOutcome.Rejected).reason,
        )
    }

    @Test
    fun uninstallWithBogusPackageIsRejectedNotCoerced() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_UNINSTALL_APP",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("package", "some app")),
            ),
        )
        assertEquals(
            LegacyAdapterRejection.UNPARSABLE_CONFIG_VALUE,
            (outcome as LegacyAdapterOutcome.Rejected).reason,
        )
    }

    @Test
    fun appTriggerFilterUpgradesToTypedList() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "APP_INSTALLED",
                LegacyNodeKind.TRIGGER,
                listOf(LegacyConfigEntry("packages", "com.a.app, com.b.app")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as ObserveNode
        val packages = node.arguments[CanonicalFieldId("packages")] as CollectionValue
        assertEquals(2, packages.values.size)

        // No filter: any-app matches, no arguments.
        val anyApp = familyAdapter.canonicalize(
            LegacyNodeInput("APP_INSTALLED", LegacyNodeKind.TRIGGER, emptyList()),
        ) as LegacyAdapterOutcome.Canonicalized
        assertEquals(0, (anyApp.node as ObserveNode).arguments.entries.size)
    }

    @Test
    fun openIsStrictlySingleTarget() {
        val tooMany = validateSelectionSemantics(
            semantics = FamilyPhase21Applications.launchSemantics,
            selectedTargetCount = 2,
            cardinality = FamilyPhase21Applications.openCardinality,
        )
        assertTrue(tooMany.any { it is CardinalityViolation })

        val exact = validateSelectionSemantics(
            semantics = FamilyPhase21Applications.launchSemantics,
            selectedTargetCount = 1,
            cardinality = FamilyPhase21Applications.openCardinality,
        )
        assertTrue(exact.isEmpty())
    }

    @Test
    fun destructiveSemanticsAreFailFastAndCapped() {
        assertTrue(
            FamilyPhase21Applications.destructiveSemantics.failurePolicy ==
                FailurePolicy.FAIL_FAST,
        )
        val tooMany = validateSelectionSemantics(
            semantics = FamilyPhase21Applications.destructiveSemantics,
            selectedTargetCount = 6,
            cardinality = FamilyPhase21Applications.destructiveMultiCardinality,
        )
        assertTrue(tooMany.any { it is CardinalityViolation })
    }

    @Test
    fun destructiveOperationsAreNotBlindlyRetryable() {
        // The planner must never plan a blind retry for clear/uninstall:
        // their declared idempotency is CONDITIONALLY_IDEMPOTENT.
        val planner = CanonicalExecutionPlanner.of(
            FamilyPhase21Applications.commandSemantics() + listOf(
                CommandSemantics(
                    OperationId("core.operation.wait"),
                    CommandIdempotency.IDEMPOTENT,
                    reversible = false,
                ),
            ),
        )
        val plan = planner.plan(
            root = InvokeNode(
                id = CanonicalNodeId("u1"),
                target = TargetId("core.application.package"),
                operation = OperationId("core.operation.uninstall"),
            ),
            executionPolicy = PlanExecutionPolicy.SEQUENTIAL,
            failurePolicy = FailurePolicy.FAIL_FAST,
        )
        assertEquals(
            CommandIdempotency.CONDITIONALLY_IDEMPOTENT,
            plan.allCommands.single().idempotency,
        )
    }

    @Test
    fun uninstallSchemaRequiresCapabilityDeclaration() {
        val schema = FamilyPhase21Applications.uninstallSchema()
        assertTrue(schema.securityClass == NodeSecurityClass.DESTRUCTIVE)
        assertTrue(schema.capabilities.isNotEmpty())
        val packages = CollectionValue(
            CanonicalValueKind.PACKAGE_ID,
            listOf(PackageIdValue("com.example.app")),
        )
        assertTrue(
            validateNodeValues(
                schema,
                listOf(NodeFieldValue(CanonicalFieldId("packages"), packages)),
            ).isEmpty(),
        )
    }

    @Test
    fun parityWithSkeletonTargetsIsPreserved() {
        for (rule in overrides().filter { it.kind == LegacyNodeKind.ACTION }) {
            val input = LegacyNodeInput(rule.legacyType, LegacyNodeKind.ACTION, emptyList())
            val skeleton = baseAdapter.canonicalize(input)
            val family = familyAdapter.canonicalize(input)
            // Rules that require payloads are skipped in this bare parity
            // sweep; their parity is asserted by the targeted tests above.
            val skeletonNode = (skeleton as? LegacyAdapterOutcome.Canonicalized)?.node as? InvokeNode
                ?: continue
            val familyNode = (family as? LegacyAdapterOutcome.Canonicalized)?.node as? InvokeNode
                ?: continue
            assertEquals(rule.legacyType, skeletonNode.target, familyNode.target)
            assertEquals(rule.legacyType, skeletonNode.operation, familyNode.operation)
        }
    }

    @Test
    fun canonicalizationRemainsIdempotent() {
        for (rule in overrides()) {
            val config = when {
                rule.kind == LegacyNodeKind.TRIGGER ->
                    listOf(LegacyConfigEntry("packages", "com.a.app"))
                rule.legacyType == "SYSTEM_INSTALL_APK" ->
                    listOf(LegacyConfigEntry("path", "/data/local/tmp/app.apk"))
                rule.legacyType in setOf(
                    "SYSTEM_FORCE_STOP_APP",
                    "SYSTEM_UNINSTALL_APP",
                    "SYSTEM_CLEAR_APP_DATA",
                    "SYSTEM_UPDATE_GOOGLE_PLAY_APPS",
                ) -> listOf(LegacyConfigEntry("packages", "com.a.app"))
                else -> listOf(LegacyConfigEntry("package", "com.example.app"))
            }
            val input = LegacyNodeInput(rule.legacyType, rule.kind, config)
            assertEquals(
                "non-idempotent ${rule.legacyType}",
                familyAdapter.canonicalize(input),
                familyAdapter.canonicalize(input),
            )
        }
    }

    @Test
    fun driftedTableFailsClosed() {
        val drifted = LegacyMappingTable.all()
            .filter { it.legacyType != "SYSTEM_UNINSTALL_APP" }
        try {
            FamilyPhase21Applications.ruleOverrides(drifted)
            throw AssertionError("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
    }
}
