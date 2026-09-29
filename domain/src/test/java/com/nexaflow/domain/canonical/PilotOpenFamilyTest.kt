package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PilotOpenFamilyTest {

    private val baseAdapter = LegacyCanonicalAdapter(LegacyMappingTable.all())
    private val pilotAdapter = PilotOpenFamily.adapterWithPilot()

    private fun settingsRules() = LegacyMappingTable.all()
        .filter {
            it.kind == LegacyNodeKind.ACTION &&
                it.legacyType in PilotOpenFamily.legacyPageMappings
        }

    @Test
    fun pilotOverridesCoverExactlyTheSettingsLaunchers() {
        val overrides = PilotOpenFamily.ruleOverrides()
        val expected = PilotOpenFamily.legacyPageMappings.keys.sorted()

        assertEquals(29, overrides.size)
        assertEquals(expected, overrides.map { it.legacyType }.sorted())
    }

    @Test
    fun pilotAdapterKeepsEveryOtherRuleIntact() {
        assertEquals(233, pilotAdapter.declaredRules)
        assertTrue(pilotAdapter.hasMapping(LegacyNodeKind.ACTION, "SYSTEM_OPEN_URL"))
        assertTrue(pilotAdapter.hasMapping(LegacyNodeKind.ACTION, "SYSTEM_OPEN_CAMERA"))
        assertTrue(pilotAdapter.hasMapping(LegacyNodeKind.TRIGGER, "WIFI_CONNECTED"))
    }

    @Test
    fun settingsLaunchersInferTheirCanonicalPageWithoutLegacyConfig() {
        for (rule in settingsRules()) {
            val outcome = pilotAdapter.canonicalize(
                LegacyNodeInput(rule.legacyType, LegacyNodeKind.ACTION, emptyList()),
            ) as LegacyAdapterOutcome.Canonicalized
            val node = outcome.node as OpenNode
            val expected = PilotOpenFamily.legacyPageMappings.getValue(rule.legacyType)
            assertEquals(
                EnumTokenValue("core.system.settings", expected),
                node.arguments[CanonicalFieldId("page")],
            )
        }
    }

    @Test
    fun genericOpenSettingsKeepsExplicitPageAndLegacyWifiDefault() {
        val explicit = pilotAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_OPEN_SETTINGS",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("page", "PRIVACY")),
            ),
        ) as LegacyAdapterOutcome.Canonicalized
        assertEquals(
            EnumTokenValue("core.system.settings", "PRIVACY"),
            (explicit.node as OpenNode).arguments[CanonicalFieldId("page")],
        )

        val defaulted = pilotAdapter.canonicalize(
            LegacyNodeInput("SYSTEM_OPEN_SETTINGS", LegacyNodeKind.ACTION, emptyList()),
        ) as LegacyAdapterOutcome.Canonicalized
        assertEquals(
            EnumTokenValue("core.system.settings", "WIFI"),
            (defaulted.node as OpenNode).arguments[CanonicalFieldId("page")],
        )
    }

    @Test
    fun settingsActionsPreserveReviewedTargetAndOperation() {
        for (rule in settingsRules()) {
            val skeleton = baseAdapter.canonicalize(
                LegacyNodeInput(rule.legacyType, LegacyNodeKind.ACTION, emptyList()),
            ) as LegacyAdapterOutcome.Canonicalized
            val pilot = pilotAdapter.canonicalize(
                LegacyNodeInput(rule.legacyType, LegacyNodeKind.ACTION, emptyList()),
            ) as LegacyAdapterOutcome.Canonicalized

            val skeletonNode = skeleton.node as InvokeNode
            val pilotNode = pilot.node as OpenNode
            assertEquals(skeletonNode.target, pilotNode.target)
            assertEquals(skeletonNode.operation, pilotNode.operation)
        }
    }

    @Test
    fun nonSettingsOpenActionsAreNotCapturedByThePilot() {
        for (legacyType in listOf(
            "SYSTEM_OPEN_URL",
            "SYSTEM_OPEN_APP",
            "SYSTEM_OPEN_CAMERA",
            "SYSTEM_OPEN_CONTACTS",
            "SYSTEM_OPEN_MAPS",
            "SYSTEM_OPEN_RECENTS",
            "SYSTEM_OPEN_QUICK_SETTINGS",
        )) {
            val config = when (legacyType) {
                "SYSTEM_OPEN_URL" -> listOf(LegacyConfigEntry("url", "https://example.com"))
                "SYSTEM_OPEN_APP" -> listOf(LegacyConfigEntry("package", "com.example.app"))
                else -> emptyList()
            }
            val outcome = pilotAdapter.canonicalize(
                LegacyNodeInput(legacyType, LegacyNodeKind.ACTION, config),
            ) as LegacyAdapterOutcome.Canonicalized
            assertTrue(outcome.node is InvokeNode)
        }
    }

    @Test
    fun unconsumedKeysStillRideAlong() {
        val outcome = pilotAdapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_OPEN_WIFI_SETTINGS",
                kind = LegacyNodeKind.ACTION,
                config = listOf(LegacyConfigEntry("futureFlag", "1")),
            ),
        )
        val canonicalized = outcome as LegacyAdapterOutcome.Canonicalized
        assertEquals(
            listOf(LegacyConfigEntry("futureFlag", "1")),
            canonicalized.preservedConfig,
        )
    }

    @Test
    fun schemaValidatesEveryDeclaredPageToken() {
        val schema = PilotOpenFamily.openSettingsSchema()
        for (token in PilotOpenFamily.legacyPageMappings.values.distinct()) {
            val issues = validateNodeValues(
                schema,
                listOf(
                    NodeFieldValue(
                        CanonicalFieldId("page"),
                        EnumTokenValue("core.system.settings", token),
                    ),
                ),
            )
            assertTrue("token $token must validate: $issues", issues.isEmpty())
        }

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
    fun driftedSettingsTableFailsClosed() {
        val driftedTable = LegacyMappingTable.all()
            .filter { it.legacyType != "SYSTEM_OPEN_WIFI_SETTINGS" }
        try {
            PilotOpenFamily.ruleOverrides(driftedTable)
            throw AssertionError("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
    }
}
