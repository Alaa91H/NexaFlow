package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCanonicalAdapterTest {

    private val wifi = TargetId("core.connectivity.wifi")

    /** Rule mirroring the reviewed semantic mapping: SYSTEM_WIFI → SetState. */
    private val wifiRule = object : LegacyMappingRule {
        override val legacyType = "SYSTEM_WIFI"
        override val kind = LegacyNodeKind.ACTION
        override val consumedKeys = setOf("enabled")

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val enabled = input.entry("enabled")!!
            return SetStateNode(
                id = CanonicalNodeId("legacy.${input.legacyType.lowercase()}"),
                target = wifi,
                state = LegacyValueParsers.parseBoolean(enabled),
            )
        }
    }

    /** Rule that consumes nothing: everything is preserved. */
    private val observationRule = object : LegacyMappingRule {
        override val legacyType = "WIFI_CONNECTED"
        override val kind = LegacyNodeKind.TRIGGER
        override val consumedKeys = emptySet<String>()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode =
            ObserveNode(
                id = CanonicalNodeId("legacy.${input.legacyType.lowercase()}"),
                target = TargetId("core.connectivity.wifi_network"),
                predicate = PredicateId("core.predicate.match_state"),
            )
    }

    private val adapter = LegacyCanonicalAdapter(listOf(wifiRule, observationRule))

    @Test
    fun legacyWifiMapsToTypedSetState() {
        val outcome = adapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_WIFI",
                kind = LegacyNodeKind.ACTION,
                config = listOf(LegacyConfigEntry("enabled", "true")),
            ),
        )

        val canonicalized = outcome as LegacyAdapterOutcome.Canonicalized
        val node = canonicalized.node as SetStateNode
        assertEquals(wifi, node.target)
        assertEquals(BooleanValue(true), node.state)
        assertTrue(canonicalized.preservedConfig.isEmpty())
    }

    @Test
    fun canonicalizationIsIdempotent() {
        val input = LegacyNodeInput(
            legacyType = "SYSTEM_WIFI",
            kind = LegacyNodeKind.ACTION,
            config = listOf(LegacyConfigEntry("enabled", "false")),
        )

        val first = adapter.canonicalize(input)
        val second = adapter.canonicalize(input)

        assertEquals(first, second)
    }

    @Test
    fun unconsumedConfigIsPreservedLosslessly() {
        val outcome = adapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_WIFI",
                kind = LegacyNodeKind.ACTION,
                config = listOf(
                    LegacyConfigEntry("enabled", "true"),
                    LegacyConfigEntry("unknown_future_key", "keep-me"),
                ),
            ),
        )

        val canonicalized = outcome as LegacyAdapterOutcome.Canonicalized
        assertEquals(
            listOf(LegacyConfigEntry("unknown_future_key", "keep-me")),
            canonicalized.preservedConfig,
        )
    }

    @Test
    fun unknownLegacyTypeIsRejectedNotGuessed() {
        val outcome = adapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SOMETHING_NEW",
                kind = LegacyNodeKind.ACTION,
                config = emptyList(),
            ),
        )

        val rejected = outcome as LegacyAdapterOutcome.Rejected
        assertEquals(LegacyAdapterRejection.UNKNOWN_LEGACY_TYPE, rejected.reason)
    }

    @Test
    fun missingRequiredConfigIsRejected() {
        val outcome = adapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_WIFI",
                kind = LegacyNodeKind.ACTION,
                config = emptyList(),
            ),
        )

        val rejected = outcome as LegacyAdapterOutcome.Rejected
        assertEquals(LegacyAdapterRejection.MISSING_REQUIRED_CONFIG, rejected.reason)
        assertTrue(rejected.message.contains("enabled"))
    }

    @Test
    fun unparsableBooleanIsRejectedNotCoerced() {
        val outcome = adapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_WIFI",
                kind = LegacyNodeKind.ACTION,
                config = listOf(LegacyConfigEntry("enabled", "yes-please")),
            ),
        )

        val rejected = outcome as LegacyAdapterOutcome.Rejected
        assertEquals(LegacyAdapterRejection.UNPARSABLE_CONFIG_VALUE, rejected.reason)
    }

    @Test
    fun kindMismatchIsRejected() {
        // SYSTEM_WIFI exists as an ACTION rule; asking for a TRIGGER mapping
        // must fail closed rather than matching across kinds.
        val outcome = adapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_WIFI",
                kind = LegacyNodeKind.TRIGGER,
                config = emptyList(),
            ),
        )

        val rejected = outcome as LegacyAdapterOutcome.Rejected
        assertEquals(LegacyAdapterRejection.UNKNOWN_LEGACY_TYPE, rejected.reason)
    }

    @Test
    fun triggerMappingPreservesEverything() {
        val outcome = adapter.canonicalize(
            LegacyNodeInput(
                legacyType = "WIFI_CONNECTED",
                kind = LegacyNodeKind.TRIGGER,
                config = listOf(LegacyConfigEntry("legacyExtra", "42")),
            ),
        )

        val canonicalized = outcome as LegacyAdapterOutcome.Canonicalized
        val node = canonicalized.node as ObserveNode
        assertEquals(PredicateId("core.predicate.match_state"), node.predicate)
        assertEquals(1, canonicalized.preservedConfig.size)
    }

    @Test
    fun ruleTableRejectsDuplicates() {
        try {
            LegacyCanonicalAdapter(listOf(wifiRule, wifiRule))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun declaredRulesAreCounted() {
        assertEquals(2, adapter.declaredRules)
        assertTrue(adapter.hasMapping(LegacyNodeKind.ACTION, "SYSTEM_WIFI"))
        assertTrue(!adapter.hasMapping(LegacyNodeKind.ACTION, "SYSTEM_BRIGHTNESS"))
    }

    @Test
    fun strictParsersRejectBogusValues() {
        listOf(
            { LegacyValueParsers.parseInteger(LegacyConfigEntry("k", "abc")) },
            { LegacyValueParsers.parseDuration(LegacyConfigEntry("k", "soon")) },
            { LegacyValueParsers.parseBoolean(LegacyConfigEntry("k", "TRUE")) },
        ).forEach { parser ->
            try {
                parser()
                throw AssertionError("Expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }
}
