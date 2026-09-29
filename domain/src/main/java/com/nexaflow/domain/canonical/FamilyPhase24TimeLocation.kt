package com.nexaflow.domain.canonical

/**
 * T24 — Time / Calendar / Location family (plan §T24).
 *
 * Upgrades 5 actions and 6 triggers over the reviewed mappings, with the
 * closure rule's temporal semantics:
 *
 * - Monotonic vs wall-clock separation: durations ([DurationValue]) are
 *   monotonic timers; schedules carry explicit [TimeOfDayValue] wall-clock
 *   times plus a [TimezoneValue] — DST-safe by construction, because the
 *   zone id resolves the wall time at evaluation time instead of freezing a
 *   UTC offset that goes stale across DST transitions.
 * - Geofence transitions carry a typed radius in meters (bounded) and
 *   optional typed enter/exit tokens.
 * - Timezone-changed and alarm-set triggers are pure change events; they
 *   carry no payload.
 */
object FamilyPhase24TimeLocation {

    object Keys {
        const val ENABLED = "enabled"
        const val TIME = "time"
        const val TIMEZONE = "timezone"
        const val HOUR = "hour"
        const val MINUTE = "minute"
        const val SECONDS = "seconds"
        const val MESSAGE = "message"
        const val SKIP_UI = "skipUi"
        const val RADIUS = "radius"
        const val TRANSITION = "transition"
        const val VALUE = "value"
    }

    /** Location service is a boolean state action. */
    private val LOCATION_STATE_ACTIONS = setOf("SYSTEM_LOCATION")

    /** Location mode / alarm / timer are value-ish CREATE/SET actions. */
    private val LOCATION_VALUE_ACTIONS = setOf("SYSTEM_LOCATION_MODE")

    /** Alarm/timer keep the persisted/runtime contracts exactly. */
    private val ALARM_ACTIONS = setOf("SYSTEM_SET_ALARM")
    private val TIMER_ACTIONS = setOf("SYSTEM_SET_TIMER")

    /** Maps stays an open skeleton with an optional typed query. */
    private val OTHER_ACTIONS = setOf("SYSTEM_OPEN_MAPS")

    private val SCHEDULE_TRIGGERS = setOf("TIME")
    private val EVENT_TRIGGERS = setOf("CALENDAR", "ALARM_SET_CHANGED", "TIMEZONE_CHANGED")
    private val GEOFENCE_TRIGGERS = setOf("LOCATION")
    private val STATE_TRIGGERS = setOf("LOCATION_STATE")

    private val ALL_FAMILY_ACTIONS = LOCATION_STATE_ACTIONS + LOCATION_VALUE_ACTIONS +
        ALARM_ACTIONS + TIMER_ACTIONS + OTHER_ACTIONS
    private val ALL_FAMILY_TRIGGERS = SCHEDULE_TRIGGERS + EVENT_TRIGGERS +
        GEOFENCE_TRIGGERS + STATE_TRIGGERS

    /** Strict wall-clock parser: HH:mm or HH:mm:ss, validated via T04 type. */
    private fun parseWallClock(entry: LegacyConfigEntry): TimeOfDayValue {
        val match = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?").matchEntire(entry.rawValue)
            ?: throw IllegalArgumentException("legacy key ${entry.key} is not a wall-clock time")
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        return TimeOfDayValue(hour * 60 + minute)
    }

    private class LocationStateRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.ENABLED)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val entry = input.entry(Keys.ENABLED) ?: return skeleton
            return SetStateNode(
                id = skeleton.id,
                target = skeleton.target,
                state = LegacyValueParsers.parseBoolean(entry),
            )
        }
    }

    private class TimeValueRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.VALUE)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val entry = input.entry(Keys.VALUE) ?: return skeleton
            return SetValueNode(
                id = skeleton.id,
                target = skeleton.target,
                value = LegacyValueParsers.parseText(entry),
            )
        }
    }

    private class AlarmRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf(Keys.HOUR, Keys.MINUTE)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val arguments = mutableListOf<CanonicalArgument>()
            input.entry(Keys.HOUR)?.let { entry ->
                val hour = LegacyValueParsers.parseInteger(entry)
                require(hour.value in 0L..23L) { "alarm hour must be in 0..23" }
                arguments += CanonicalArgument(CanonicalFieldId(Keys.HOUR), hour)
            }
            input.entry(Keys.MINUTE)?.let { entry ->
                val minute = LegacyValueParsers.parseInteger(entry)
                require(minute.value in 0L..59L) { "alarm minute must be in 0..59" }
                arguments += CanonicalArgument(CanonicalFieldId(Keys.MINUTE), minute)
            }
            return InvokeNode(
                id = skeleton.id,
                target = skeleton.target,
                operation = skeleton.operation,
                arguments = CanonicalArguments(arguments),
            )
        }
    }

    private class TimerRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> =
            setOf(Keys.SECONDS, Keys.MESSAGE, Keys.SKIP_UI)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val arguments = mutableListOf<CanonicalArgument>()
            input.entry(Keys.SECONDS)?.let { entry ->
                val seconds = entry.rawValue.toLongOrNull()
                    ?: throw IllegalArgumentException("timer seconds must be an integer")
                require(seconds in 1L..86_400L) {
                    "timer seconds must be in 1..86400"
                }
                arguments += CanonicalArgument(
                    CanonicalFieldId(Keys.SECONDS),
                    DurationValue(Math.multiplyExact(seconds, 1000L)),
                )
            }
            input.entry(Keys.MESSAGE)?.let { entry ->
                arguments += CanonicalArgument(
                    CanonicalFieldId(Keys.MESSAGE),
                    LegacyValueParsers.parseText(entry),
                )
            }
            input.entry(Keys.SKIP_UI)?.let { entry ->
                arguments += CanonicalArgument(
                    CanonicalFieldId(Keys.SKIP_UI),
                    LegacyValueParsers.parseBoolean(entry),
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

    private class MapsRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.ACTION
        override val consumedKeys: Set<String> = setOf("query")

        // The destination query is optional: opening Maps without one is
        // parity-preserving legacy behavior (fail closed handled downstream).
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as InvokeNode
            val arguments = mutableListOf<CanonicalArgument>()
            input.entry("query")?.let {
                arguments += CanonicalArgument(CanonicalFieldId("query"), LegacyValueParsers.parseText(it))
            }
            return InvokeNode(
                id = skeleton.id,
                target = skeleton.target,
                operation = skeleton.operation,
                arguments = CanonicalArguments(arguments),
            )
        }
    }

    private class TimeTriggerRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.TRIGGER
        override val consumedKeys: Set<String> = setOf(Keys.TIME, Keys.TIMEZONE)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as ObserveNode
            val arguments = mutableListOf<CanonicalArgument>()
            input.entry(Keys.TIME)?.let {
                arguments += CanonicalArgument(CanonicalFieldId("time"), parseWallClock(it))
            }
            input.entry(Keys.TIMEZONE)?.let {
                arguments += CanonicalArgument(
                    CanonicalFieldId("timezone"),
                    TimezoneValue(it.rawValue),
                )
            }
            return ObserveNode(
                id = skeleton.id,
                target = skeleton.target,
                predicate = skeleton.predicate,
                arguments = CanonicalArguments(arguments),
            )
        }
    }

    private class GeofenceRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.TRIGGER
        override val consumedKeys: Set<String> = setOf(Keys.RADIUS, Keys.TRANSITION)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as ObserveNode
            val arguments = mutableListOf<CanonicalArgument>()
            input.entry(Keys.RADIUS)?.let { entry ->
                val radius = entry.rawValue.toLongOrNull()
                    ?: throw IllegalArgumentException("radius must be an integer meter value")
                require(radius in 1..100_000) {
                    "geofence radius must be in 1..100000 meters"
                }
                arguments += CanonicalArgument(CanonicalFieldId("radius"), IntegerValue(radius))
            }
            input.entry(Keys.TRANSITION)?.let {
                arguments += CanonicalArgument(
                    CanonicalFieldId("transition"),
                    LegacyValueParsers.parseText(it),
                )
            }
            return ObserveNode(
                id = skeleton.id,
                target = skeleton.target,
                predicate = skeleton.predicate,
                arguments = CanonicalArguments(arguments),
            )
        }
    }

    private class PlainTriggerRule(
        override val legacyType: String,
        private val base: LegacyMappingRule,
    ) : LegacyMappingRule {
        override val kind: LegacyNodeKind = LegacyNodeKind.TRIGGER
        override val consumedKeys: Set<String> = setOf(Keys.ENABLED)
        override val requiredKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val skeleton = base.canonicalize(input) as ObserveNode
            val entry = input.entry(Keys.ENABLED) ?: return skeleton
            return ObserveNode(
                id = skeleton.id,
                target = skeleton.target,
                predicate = skeleton.predicate,
                arguments = CanonicalArguments(
                    listOf(
                        CanonicalArgument(
                            CanonicalFieldId("enabled"),
                            LegacyValueParsers.parseBoolean(entry),
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
            ALL_FAMILY_TRIGGERS.map { LegacyNodeKind.TRIGGER to it })
            .filterNot { it in generated.keys }
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "T15 table drift: ${missing.size} time/location members missing",
            )
        }

        return ALL_FAMILY_ACTIONS.map { name ->
            val base = generated.getValue(LegacyNodeKind.ACTION to name)
            when {
                name in LOCATION_STATE_ACTIONS -> LocationStateRule(name, base)
                name in LOCATION_VALUE_ACTIONS -> TimeValueRule(name, base)
                name in ALARM_ACTIONS -> AlarmRule(name, base)
                name in TIMER_ACTIONS -> TimerRule(name, base)
                else -> MapsRule(name, base)
            }
        } + ALL_FAMILY_TRIGGERS.map { name ->
            val base = generated.getValue(LegacyNodeKind.TRIGGER to name)
            when {
                name in SCHEDULE_TRIGGERS -> TimeTriggerRule(name, base)
                name in GEOFENCE_TRIGGERS -> GeofenceRule(name, base)
                else -> PlainTriggerRule(name, base)
            }
        }
    }

    /** Adapter with time/location overrides merged over the full table. */
    fun adapterWithFamily(
        table: List<LegacyMappingRule> = LegacyMappingTable.all(),
    ): LegacyCanonicalAdapter {
        val overridden = ruleOverrides(table).associateBy { it.legacyType }
        return LegacyCanonicalAdapter(table.filter { it.legacyType !in overridden } + overridden.values)
    }

    /** Scheduling semantics: single target, ordered, fail fast. */
    val scheduleSemantics: NodeSelectionSemantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.SINGLE,
        executionMode = ExecutionMode.SINGLE,
    )

    /**
     * The schedule schema: wall-clock time + explicit timezone. Carrying the
     * zone id is what makes DST explicit: a 08:00 Europe/Berlin schedule
     * stays 08:00 local across DST transitions instead of silently shifting
     * an hour (plan §T24 closure rule).
     */
    fun scheduleSchema(): NodeSchema = NodeSchema(
        schemaId = "core.schema.schedule.clock.match_schedule",
        kind = NodeSchemaKind.TRIGGER,
        target = TargetId("core.schedule.clock"),
        predicate = PredicateId("core.predicate.match_schedule"),
        title = "Schedule",
        summaryTemplate = "At {time}{{ {timezone}}}",
        securityClass = NodeSecurityClass.STANDARD,
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("time"),
                type = NodeFieldType.TIME_OF_DAY,
                alwaysRequired = true,
            ),
            NodeSchemaField(
                id = CanonicalFieldId("timezone"),
                type = NodeFieldType.TIMEZONE_ID,
                level = NodeSchemaLevel.ADVANCED,
                helpText = "Explicit zone keeps wall-clock times DST-safe",
            ),
        ),
    )
}
