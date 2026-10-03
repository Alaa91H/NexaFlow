package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyPhase23PowerSensorsTest {

    private val familyAdapter = FamilyPhase23PowerSensors.adapterWithFamily()

    private fun overrides() = FamilyPhase23PowerSensors.ruleOverrides()

    @Test
    fun familyOverridesCoverPowerSensorMembers() {
        assertEquals(5, overrides().count { it.kind == LegacyNodeKind.ACTION })
        assertEquals(8, overrides().count { it.kind == LegacyNodeKind.TRIGGER })
    }

    @Test
    fun familyAdapterKeepsTheFullTable() {
        assertEquals(237, familyAdapter.declaredRules)
    }

    @Test
    fun powerSaverUpgradesToTypedSetState() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_POWER_SAVER",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("enabled", "true")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as SetStateNode
        assertEquals(TargetId("core.power.saver"), node.target)
        assertEquals(BooleanValue(true), node.state)
    }

    @Test
    fun chargingLimitUpgradesToTypedInteger() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_CHARGING_LIMIT",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("value", "80")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as SetValueNode
        assertEquals(IntegerValue(80), node.value)
    }

    @Test
    fun bogusThresholdIsRejectedNotCoerced() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_BATTERY_SAVER_THRESHOLD",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("value", "low")),
            ),
        )
        assertEquals(
            LegacyAdapterRejection.UNPARSABLE_CONFIG_VALUE,
            (outcome as LegacyAdapterOutcome.Rejected).reason,
        )
    }

    @Test
    fun batteryTriggerCarriesTypedThresholdAndChargingFilter() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "BATTERY",
                LegacyNodeKind.TRIGGER,
                listOf(
                    LegacyConfigEntry("threshold", "20"),
                    LegacyConfigEntry("charging", "false"),
                ),
            ),
        )
        val observation = (outcome as LegacyAdapterOutcome.Canonicalized).node as ObserveNode
        assertEquals(
            IntegerValue(20),
            observation.arguments[CanonicalFieldId("threshold")],
        )
        assertEquals(
            BooleanValue(false),
            observation.arguments[CanonicalFieldId("charging")],
        )
    }

    @Test
    fun chargerTriggerCarriesOptionalTypedState() {
        val withState = familyAdapter.canonicalize(
            LegacyNodeInput(
                "CHARGER",
                LegacyNodeKind.TRIGGER,
                listOf(LegacyConfigEntry("enabled", "true")),
            ),
        ) as LegacyAdapterOutcome.Canonicalized
        assertEquals(
            BooleanValue(true),
            (withState.node as ObserveNode).arguments[CanonicalFieldId("enabled")],
        )

        val anyState = familyAdapter.canonicalize(
            LegacyNodeInput("CHARGER", LegacyNodeKind.TRIGGER, emptyList()),
        ) as LegacyAdapterOutcome.Canonicalized
        assertEquals(0, (anyState.node as ObserveNode).arguments.entries.size)
    }

    @Test
    fun multiSensorObservationsComposeThroughTheStateRules() {
        // Two sensor observations on different targets never contradict.
        val observations = listOf(
            ObserveNode(
                id = CanonicalNodeId("t1"),
                target = TargetId("core.power.battery"),
                predicate = PredicateId("core.predicate.match_threshold"),
                arguments = CanonicalArguments(
                    listOf(CanonicalArgument(CanonicalFieldId("threshold"), IntegerValue(20))),
                ),
            ),
            ObserveNode(
                id = CanonicalNodeId("t2"),
                target = TargetId("core.sensor.reading"),
                predicate = PredicateId("core.predicate.match_reading"),
            ),
        )
        assertTrue(evaluateStateConditions(observations).isEmpty())

        // The same boolean field on one target with opposite values does.
        val contradictory = listOf(
            ObserveNode(
                id = CanonicalNodeId("c1"),
                target = TargetId("core.power.charging"),
                predicate = PredicateId("core.predicate.match_state"),
                arguments = CanonicalArguments(
                    listOf(CanonicalArgument(CanonicalFieldId("enabled"), BooleanValue(true))),
                ),
            ),
            ObserveNode(
                id = CanonicalNodeId("c2"),
                target = TargetId("core.power.charging"),
                predicate = PredicateId("core.predicate.match_state"),
                arguments = CanonicalArguments(
                    listOf(CanonicalArgument(CanonicalFieldId("enabled"), BooleanValue(false))),
                ),
            ),
        )
        assertTrue(evaluateStateConditions(contradictory).any { it is ContradictoryStateConditions })
    }

    @Test
    fun thresholdSchemaEnforcesBoundedValues() {
        val schema = FamilyPhase23PowerSensors.batterySaverThresholdSchema()

        assertTrue(
            validateNodeValues(
                schema,
                listOf(NodeFieldValue(CanonicalFieldId("threshold"), IntegerValue(50))),
            ).isEmpty(),
        )
        assertTrue(
            validateNodeValues(
                schema,
                listOf(NodeFieldValue(CanonicalFieldId("threshold"), IntegerValue(150))),
            ).any { it is FieldValueOutOfBounds },
        )
    }

    @Test
    fun powerSemanticsAreExecutable() {
        val errors = validateSelectionSemantics(
            semantics = FamilyPhase23PowerSensors.powerSemantics,
            selectedTargetCount = 2,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun canonicalizationRemainsIdempotent() {
        for (rule in overrides()) {
            val config = when {
                rule.legacyType in setOf(
                    "SYSTEM_BATTERY_SAVER_THRESHOLD",
                    "SYSTEM_CHARGING_LIMIT",
                    "SYSTEM_CHARGING_FEEDBACK",
                ) -> listOf(LegacyConfigEntry("value", "50"))
                rule.kind == LegacyNodeKind.ACTION -> listOf(LegacyConfigEntry("enabled", "true"))
                else -> emptyList()
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
            .filter { it.legacyType != "SYSTEM_POWER_SAVER" }
        try {
            FamilyPhase23PowerSensors.ruleOverrides(drifted)
            throw AssertionError("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
    }
}
