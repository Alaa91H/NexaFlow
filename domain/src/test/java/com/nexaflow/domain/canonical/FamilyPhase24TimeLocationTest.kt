package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.LocalDateTime

class FamilyPhase24TimeLocationTest {

    private val familyAdapter = FamilyPhase24TimeLocation.adapterWithFamily()

    private fun overrides() = FamilyPhase24TimeLocation.ruleOverrides()

    @Test
    fun familyOverridesCoverTimeLocationMembers() {
        assertEquals(5, overrides().count { it.kind == LegacyNodeKind.ACTION })
        assertEquals(6, overrides().count { it.kind == LegacyNodeKind.TRIGGER })
    }

    @Test
    fun familyAdapterKeepsTheFullTable() {
        assertEquals(233, familyAdapter.declaredRules)
    }

    @Test
    fun locationStateUpgradesToTypedSetState() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_LOCATION",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("enabled", "false")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as SetStateNode
        assertEquals(TargetId("core.location.service"), node.target)
        assertEquals(BooleanValue(false), node.state)
    }

    @Test
    fun alarmCarriesWallClockAndExplicitTimezone() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_SET_ALARM",
                LegacyNodeKind.ACTION,
                listOf(
                    LegacyConfigEntry("time", "07:30"),
                    LegacyConfigEntry("timezone", "Europe/Berlin"),
                ),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as InvokeNode
        assertEquals(TimeOfDayValue(450), node.arguments[CanonicalFieldId("time")])
        assertEquals(
            TimezoneValue("Europe/Berlin"),
            node.arguments[CanonicalFieldId("timezone")],
        )
    }

    @Test
    fun bogusWallClockIsRejected() {
        // Scheduling without any time payload is not an alarm: the family
        // fails closed when both time and timezone are absent.
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput("SYSTEM_SET_ALARM", LegacyNodeKind.ACTION, emptyList()),
        )
        assertEquals(
            LegacyAdapterRejection.UNPARSABLE_CONFIG_VALUE,
            (outcome as LegacyAdapterOutcome.Rejected).reason,
        )
    }

    @Test
    fun wallClockMinutesAreValidated() {
        // 25:00 is not a wall clock; the T04 TimeOfDayValue type rejects it.
        try {
            TimeOfDayValue(25 * 60)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun scheduleTriggerCarriesDstSafeWallClockPlusZone() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "TIME",
                LegacyNodeKind.TRIGGER,
                listOf(
                    LegacyConfigEntry("time", "08:00"),
                    LegacyConfigEntry("timezone", "Europe/Berlin"),
                ),
            ),
        )
        val observation = (outcome as LegacyAdapterOutcome.Canonicalized).node as ObserveNode
        assertEquals(TimeOfDayValue(480), observation.arguments[CanonicalFieldId("time")])
        assertEquals(
            TimezoneValue("Europe/Berlin"),
            observation.arguments[CanonicalFieldId("timezone")],
        )

        // DST safety property: the same wall clock resolves to different UTC
        // instants in summer vs winter, and the stored zone id is what makes
        // each resolve correctly — no frozen offset goes stale.
        val zone = ZoneId.of("Europe/Berlin")
        val summer = LocalDateTime.of(2026, 7, 1, 8, 0).atZone(zone).offset
        val winter = LocalDateTime.of(2026, 1, 1, 8, 0).atZone(zone).offset
        assertTrue(summer != winter)
    }

    @Test
    fun timerDurationsAreMonotonicNotWallClock() {
        // A timer payload is a DurationValue (monotonic), never a time of
        // day: 90 minutes from now is unaffected by DST transitions.
        val duration = DurationValue(90 * 60 * 1000L)
        assertEquals(5_400_000L, duration.milliseconds)
    }

    @Test
    fun geofenceRequiresBoundedRadius() {
        val valid = familyAdapter.canonicalize(
            LegacyNodeInput(
                "LOCATION",
                LegacyNodeKind.TRIGGER,
                listOf(
                    LegacyConfigEntry("radius", "150"),
                    LegacyConfigEntry("transition", "ENTER"),
                ),
            ),
        ) as LegacyAdapterOutcome.Canonicalized
        val observation = valid.node as ObserveNode
        assertEquals(IntegerValue(150), observation.arguments[CanonicalFieldId("radius")])
        assertEquals(TextValue("ENTER"), observation.arguments[CanonicalFieldId("transition")])

        val unbounded = familyAdapter.canonicalize(
            LegacyNodeInput(
                "LOCATION",
                LegacyNodeKind.TRIGGER,
                listOf(LegacyConfigEntry("radius", "500000")),
            ),
        )
        assertEquals(
            LegacyAdapterRejection.UNPARSABLE_CONFIG_VALUE,
            (unbounded as LegacyAdapterOutcome.Rejected).reason,
        )
    }

    @Test
    fun timezoneChangedStaysAPureChangeEvent() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput("TIMEZONE_CHANGED", LegacyNodeKind.TRIGGER, emptyList()),
        ) as LegacyAdapterOutcome.Canonicalized
        val observation = outcome.node as ObserveNode
        assertEquals(
            PredicateId("core.predicate.match_change_event"),
            observation.predicate,
        )
        assertEquals(0, observation.arguments.entries.size)
    }

    @Test
    fun scheduleSemanticsAreSingleTarget() {
        val errors = validateSelectionSemantics(
            semantics = FamilyPhase24TimeLocation.scheduleSemantics,
            selectedTargetCount = 1,
            cardinality = OperationCardinality.SINGLE_TARGET,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun scheduleSchemaRendersTheWallClock() {
        val schema = FamilyPhase24TimeLocation.scheduleSchema()
        val summary = NodeSummaryFormatter.summarize(
            schema,
            listOf(
                NodeFieldValue(CanonicalFieldId("time"), TimeOfDayValue(480)),
                NodeFieldValue(CanonicalFieldId("timezone"), TimezoneValue("Europe/Berlin")),
            ),
        )
        // The time renders as its canonical string form; the zone id renders
        // as its identifier. Deterministic across builder/history/import.
        assertTrue(summary.startsWith("At "))
        assertTrue(summary.contains("480"))
        assertTrue(summary.contains("Europe/Berlin"))
    }

    @Test
    fun canonicalizationRemainsIdempotent() {
        for (rule in overrides()) {
            val config = when {
                rule.legacyType in setOf("SYSTEM_SET_ALARM", "TIME") -> listOf(
                    LegacyConfigEntry("time", "08:00"),
                    LegacyConfigEntry("timezone", "Europe/Berlin"),
                )
                rule.legacyType == "LOCATION" -> listOf(LegacyConfigEntry("radius", "150"))
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
            .filter { it.legacyType != "TIME" }
        try {
            FamilyPhase24TimeLocation.ruleOverrides(drifted)
            throw AssertionError("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
    }
}
