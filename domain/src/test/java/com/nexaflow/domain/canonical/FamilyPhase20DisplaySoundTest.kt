package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyPhase20DisplaySoundTest {

    private val baseAdapter = LegacyCanonicalAdapter(LegacyMappingTable.all())
    private val familyAdapter = FamilyPhase20DisplaySound.adapterWithFamily()

    private fun overrides() = FamilyPhase20DisplaySound.ruleOverrides()

    @Test
    fun familyOverridesCoverDisplaySoundMembers() {
        // 19 display + 14 sound actions + 11 triggers; WAKE_SCREEN keeps its
        // reviewed skeleton (included in the 33 action members).
        assertEquals(33, overrides().count { it.kind == LegacyNodeKind.ACTION })
        assertEquals(11, overrides().count { it.kind == LegacyNodeKind.TRIGGER })
    }

    @Test
    fun familyAdapterKeepsTheFullTable() {
        assertEquals(233, familyAdapter.declaredRules)
    }

    @Test
    fun booleanActionsUpgradeToTypedSetState() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_DARK_MODE",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("enabled", "true")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as SetStateNode
        assertEquals(TargetId("core.display.dark_mode"), node.target)
        assertEquals(BooleanValue(true), node.state)

        // Parity with the skeleton mapping.
        val skeleton = baseAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_DARK_MODE",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("enabled", "true")),
            ),
        ) as LegacyAdapterOutcome.Canonicalized
        assertEquals((skeleton.node as InvokeNode).target, node.target)
    }

    @Test
    fun numericValueActionsUpgradeToTypedIntegers() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_BRIGHTNESS",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("value", "35")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as SetValueNode
        assertEquals(IntegerValue(35), node.value)
    }

    @Test
    fun legacyRingerValueAliasUpgradesToTypedModeToken() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_RINGER_MODE",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("value", "VIBRATE")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as InvokeNode
        assertEquals(
            EnumTokenValue("compat.system_ringer_mode.mode", "VIBRATE"),
            node.arguments[CanonicalFieldId("mode")],
        )
    }

    @Test
    fun missingValueDefersToCatalogContract() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput("SYSTEM_BRIGHTNESS", LegacyNodeKind.ACTION, emptyList()),
        )
        val canonicalized = outcome as LegacyAdapterOutcome.Canonicalized
        assertTrue(canonicalized.node is InvokeNode)
    }

    @Test
    fun realRingerModeKeyIsConsumedIntoTypedCanonicalArgument() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_RINGER_MODE",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("mode", "VIBRATE")),
            ),
        ) as LegacyAdapterOutcome.Canonicalized
        val node = outcome.node as InvokeNode
        assertEquals(
            EnumTokenValue("compat.system_ringer_mode.mode", "VIBRATE"),
            node.arguments[CanonicalFieldId("mode")],
        )
        assertTrue(outcome.preservedConfig.isEmpty())
    }

    @Test
    fun triggersUpgradeStateConditionally() {
        val withState = familyAdapter.canonicalize(
            LegacyNodeInput(
                "DARK_MODE",
                LegacyNodeKind.TRIGGER,
                listOf(LegacyConfigEntry("enabled", "false")),
            ),
        ) as LegacyAdapterOutcome.Canonicalized
        val observation = withState.node as ObserveNode
        assertEquals(
            BooleanValue(false),
            observation.arguments[CanonicalFieldId("enabled")],
        )

        val withValue = familyAdapter.canonicalize(
            LegacyNodeInput(
                "BRIGHTNESS_LEVEL",
                LegacyNodeKind.TRIGGER,
                listOf(LegacyConfigEntry("value", "30")),
            ),
        ) as LegacyAdapterOutcome.Canonicalized
        assertEquals(
            IntegerValue(30),
            (withValue.node as ObserveNode).arguments[CanonicalFieldId("value")],
        )
    }

    @Test
    fun nightProfileBatchIsProvenParallelSafe() {
        // A night profile: three distinct targets, one atomic scope.
        val profile = listOf<CanonicalActionNode>(
            SetStateNode(CanonicalNodeId("p1"), TargetId("core.display.dark_mode"), BooleanValue(true)),
            SetStateNode(CanonicalNodeId("p2"), TargetId("core.display.extra_dim"), BooleanValue(true)),
            SetValueNode(CanonicalNodeId("p3"), TargetId("core.display.brightness"), IntegerValue(20)),
        )

        // No contradiction in the profile.
        assertTrue(evaluateWriteConflicts(profile).isEmpty())
    }

    @Test
    fun contradictoryProfileIsRejected() {
        val contradictory = listOf<CanonicalActionNode>(
            SetStateNode(CanonicalNodeId("p1"), TargetId("core.display.dark_mode"), BooleanValue(true)),
            SetStateNode(CanonicalNodeId("p2"), TargetId("core.display.dark_mode"), BooleanValue(false)),
        )
        assertTrue(evaluateWriteConflicts(contradictory).any { it is DuplicateConflictingWrites })
    }

    @Test
    fun profileSemanticsAreExecutable() {
        val errors = validateSelectionSemantics(
            semantics = FamilyPhase20DisplaySound.profileSemantics,
            selectedTargetCount = 3,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun ringerModeSchemaEnforcesTheTokenAllowlist() {
        val schema = FamilyPhase20DisplaySound.ringerModeSchema()

        val valid = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(
                    CanonicalFieldId("mode"),
                    EnumTokenValue("core.audio.ringer_mode", "SILENT"),
                ),
            ),
        )
        assertTrue(valid.isEmpty())

        val invalid = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(
                    CanonicalFieldId("mode"),
                    EnumTokenValue("core.audio.ringer_mode", "LOUD"),
                ),
            ),
        )
        assertTrue(invalid.any { it is EnumTokenNotAllowed })
    }

    @Test
    fun brightnessSchemaEnforcesRuntimeIntegerBounds() {
        val schema = FamilyPhase20DisplaySound.brightnessSchema()

        assertTrue(
            validateNodeValues(
                schema,
                listOf(NodeFieldValue(CanonicalFieldId("level"), IntegerValue(128))),
            ).isEmpty(),
        )
        assertTrue(
            validateNodeValues(
                schema,
                listOf(NodeFieldValue(CanonicalFieldId("level"), IntegerValue(256))),
            ).any { it is FieldValueOutOfBounds },
        )
    }

    @Test
    fun canonicalizationRemainsIdempotent() {
        for (rule in overrides()) {
            val config = when (rule.kind) {
                LegacyNodeKind.TRIGGER -> emptyList()
                else -> when (rule.legacyType) {
                    "SYSTEM_BRIGHTNESS" -> listOf(LegacyConfigEntry("value", "35"))
                    "SYSTEM_RINGER_MODE" -> listOf(LegacyConfigEntry("mode", "VIBRATE"))
                    else -> listOf(LegacyConfigEntry("enabled", "true"))
                }
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
            .filter { it.legacyType != "SYSTEM_DARK_MODE" }
        try {
            FamilyPhase20DisplaySound.ruleOverrides(drifted)
            throw AssertionError("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
    }
}
