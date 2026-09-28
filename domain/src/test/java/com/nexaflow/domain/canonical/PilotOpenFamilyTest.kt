package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PilotOpenFamilyTest {

    private val baseAdapter = LegacyCanonicalAdapter(LegacyMappingTable.all())
    private val pilotAdapter = PilotOpenFamily.adapterWithPilot()

    private fun openRules() = LegacyMappingTable.all()
        .filter { it.kind == LegacyNodeKind.ACTION && it.legacyType.startsWith("SYSTEM_OPEN_") }

    @Test
    fun pilotOverridesCoverExactlyTheGeneratedOpenFamily() {
        val overrides = PilotOpenFamily.ruleOverrides()
        val generated = openRules().map { it.legacyType }.sorted()

        assertEquals(41, overrides.size)
        assertEquals(generated, overrides.map { it.legacyType }.sorted())
    }

    @Test
    fun pilotAdapterKeepsEveryOtherRuleIntact() {
        assertEquals(233, pilotAdapter.declaredRules)
        assertTrue(pilotAdapter.hasMapping(LegacyNodeKind.ACTION, "SYSTEM_WIFI"))
        assertTrue(pilotAdapter.hasMapping(LegacyNodeKind.TRIGGER, "WIFI_CONNECTED"))
    }

    @Test
    fun pageOpenActionsPreserveParityWithGeneratedTable() {
        // For every page-opening action, the pilot and the skeleton must
        // resolve the same (target, operation): parity with the legacy path.
        for (rule in openRules()) {
            if (rule.legacyType in setOf("SYSTEM_OPEN_APP", "SYSTEM_OPEN_URL")) continue

            val skeleton = baseAdapter.canonicalize(
                LegacyNodeInput(
                    rule.legacyType,
                    LegacyNodeKind.ACTION,
                    listOf(LegacyConfigEntry("page", "SETTINGS")),
                ),
            ) as LegacyAdapterOutcome.Canonicalized
            val pilot = pilotAdapter.canonicalize(
                LegacyNodeInput(
                    rule.legacyType,
                    LegacyNodeKind.ACTION,
                    listOf(LegacyConfigEntry("page", "SETTINGS")),
                ),
            ) as LegacyAdapterOutcome.Canonicalized

            val skeletonNode = skeleton.node as InvokeNode
            val pilotNode = pilot.node as OpenNode
            assertEquals(skeletonNode.target, pilotNode.target)
            assertEquals(skeletonNode.operation, pilotNode.operation)
        }
    }

    @Test
    fun urlActionUpgradesToTypedUri() {
        val outcome = pilotAdapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_OPEN_URL",
                kind = LegacyNodeKind.ACTION,
                config = listOf(LegacyConfigEntry("url", "https://example.com/help")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as OpenNode
        assertEquals(
            UriValue("https://example.com/help"),
            node.arguments[CanonicalFieldId("url")],
        )
    }

    @Test
    fun urlActionWithoutUrlIsRejected() {
        val outcome = pilotAdapter.canonicalize(
            LegacyNodeInput("SYSTEM_OPEN_URL", LegacyNodeKind.ACTION, emptyList()),
        )
        // The consumed key is declared, so its absence is a missing-config
        // rejection — caught by the framework before any parsing happens.
        assertEquals(
            LegacyAdapterRejection.MISSING_REQUIRED_CONFIG,
            (outcome as LegacyAdapterOutcome.Rejected).reason,
        )
    }

    @Test
    fun appActionUpgradesToTypedPackage() {
        val outcome = pilotAdapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_OPEN_APP",
                kind = LegacyNodeKind.ACTION,
                config = listOf(LegacyConfigEntry("package", "com.example.app")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as OpenNode
        assertEquals(
            PackageIdValue("com.example.app"),
            node.arguments[CanonicalFieldId("packageName")],
        )
    }

    @Test
    fun appActionWithBogusPackageIsRejectedNotCoerced() {
        val outcome = pilotAdapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_OPEN_APP",
                kind = LegacyNodeKind.ACTION,
                config = listOf(LegacyConfigEntry("package", "not a package")),
            ),
        )
        assertEquals(
            LegacyAdapterRejection.UNPARSABLE_CONFIG_VALUE,
            (outcome as LegacyAdapterOutcome.Rejected).reason,
        )
    }

    @Test
    fun unconsumedKeysStillRideAlong() {
        val outcome = pilotAdapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_OPEN_WIFI_SETTINGS",
                kind = LegacyNodeKind.ACTION,
                config = listOf(
                    LegacyConfigEntry("page", "WIFI"),
                    LegacyConfigEntry("futureFlag", "1"),
                ),
            ),
        )
        val canonicalized = outcome as LegacyAdapterOutcome.Canonicalized
        assertEquals(
            listOf(LegacyConfigEntry("futureFlag", "1")),
            canonicalized.preservedConfig,
        )
    }

    @Test
    fun schemaValidatesTypedPageTokens() {
        val schema = PilotOpenFamily.openSettingsSchema()

        val valid = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(
                    CanonicalFieldId("page"),
                    EnumTokenValue("core.system.settings", "WIFI"),
                ),
            ),
        )
        assertTrue(valid.isEmpty())

        val invalid = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(
                    CanonicalFieldId("page"),
                    EnumTokenValue("core.system.settings", "MARS"),
                ),
            ),
        )
        assertTrue(invalid.any { it is EnumTokenNotAllowed })
    }

    @Test
    fun familySemanticsAreSingleTarget() {
        val errors = validateSelectionSemantics(
            semantics = PilotOpenFamily.semantics,
            selectedTargetCount = 1,
            cardinality = PilotOpenFamily.cardinality,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun driftedTableFailsClosed() {
        // A table without the generated SYSTEM_OPEN_* entries must abort
        // override construction instead of silently producing a subset.
        val driftedTable = LegacyMappingTable.all()
            .filter { !it.legacyType.startsWith("SYSTEM_OPEN_") }
        try {
            PilotOpenFamily.ruleOverrides(driftedTable)
            throw AssertionError("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
    }
}
