package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyMappingTableTest {

    private val registry = CanonicalIdentityRegistry.default()
    private val adapter = LegacyCanonicalAdapter(LegacyMappingTable.all())

    @Test
    fun tableCoversAll237AutomationTypes() {
        assertEquals(237, LegacyMappingTable.ruleCount())
        assertEquals(57, LegacyMappingTable.triggerCount())
        assertEquals(180, LegacyMappingTable.actionCount())
        assertEquals(237, adapter.declaredRules)
    }

    @Test
    fun everyMappingReferencesRegisteredIdentities() {
        for (rule in LegacyMappingTable.all()) {
            val node = adapter.canonicalize(
                LegacyNodeInput(rule.legacyType, rule.kind, emptyList()),
            )
            val canonicalized = node as LegacyAdapterOutcome.Canonicalized
            when (val produced = canonicalized.node) {
                is ObserveNode -> {
                    assertTrue(
                        "unregistered target ${produced.target} in ${rule.legacyType}",
                        registry.target(produced.target) != null,
                    )
                    assertTrue(
                        "unregistered predicate ${produced.predicate} in ${rule.legacyType}",
                        registry.predicate(produced.predicate) != null,
                    )
                }
                is InvokeNode -> {
                    assertTrue(
                        "unregistered target ${produced.target} in ${rule.legacyType}",
                        registry.target(produced.target) != null,
                    )
                    assertTrue(
                        "unregistered operation ${produced.operation} in ${rule.legacyType}",
                        registry.operation(produced.operation) != null,
                    )
                }
                else -> throw AssertionError(
                    "mapping ${rule.legacyType} produced an unexpected node kind",
                )
            }
        }
    }

    @Test
    fun canonicalizationIsDeterministicAndIdempotent() {
        for (rule in LegacyMappingTable.all()) {
            val input = LegacyNodeInput(rule.legacyType, rule.kind, emptyList())
            val first = adapter.canonicalize(input)
            val second = adapter.canonicalize(input)
            assertEquals("non-idempotent mapping ${rule.legacyType}", first, second)
        }
    }

    @Test
    fun unknownInputStaysRejected() {
        val outcome = adapter.canonicalize(
            LegacyNodeInput("NOT_A_REAL_TYPE", LegacyNodeKind.ACTION, emptyList()),
        )
        assertTrue(outcome is LegacyAdapterOutcome.Rejected)
    }

    @Test
    fun legacyConfigRidesAlongUnconsumed() {
        val outcome = adapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_WIFI",
                kind = LegacyNodeKind.ACTION,
                config = listOf(LegacyConfigEntry("enabled", "true")),
            ),
        )
        val canonicalized = outcome as LegacyAdapterOutcome.Canonicalized
        assertEquals(
            listOf(LegacyConfigEntry("enabled", "true")),
            canonicalized.preservedConfig,
        )
    }

    @Test
    fun pilotMappingsArePinned() {
        // T17 pilot family (plan §T17): SYSTEM_OPEN_* actions map to the open
        // family. The reviewed distribution is 36 OPEN, 4 INVOKE and 1
        // OPEN_APP_PAGE — pinned here so a silent re-mapping fails loudly.
        val openRules = LegacyMappingTable.all().filter {
            it.kind == LegacyNodeKind.ACTION && it.legacyType.startsWith("SYSTEM_OPEN_")
        }
        assertTrue("expected SYSTEM_OPEN_* rules", openRules.isNotEmpty())

        val byOperation = openRules.groupBy { rule ->
            val outcome = adapter.canonicalize(
                LegacyNodeInput(rule.legacyType, rule.kind, emptyList()),
            )
            (outcome as LegacyAdapterOutcome.Canonicalized).node.let { it as InvokeNode }.operation
        }
        assertEquals(36, byOperation[OperationId("core.operation.open")]?.size)
        assertEquals(4, byOperation[OperationId("core.operation.invoke")]?.size)
        assertEquals(1, byOperation[OperationId("core.operation.open_app_page")]?.size)
        assertEquals(41, openRules.size)
    }

    @Test
    fun allMappingsAreFailClosedConsistent() {
        // Every entry must produce a valid outcome with zero config, since the
        // generated rules consume no keys. A rejection here would mean the
        // generated table drifted from the reviewed inventory.
        for (rule in LegacyMappingTable.all()) {
            val outcome = adapter.canonicalize(
                LegacyNodeInput(rule.legacyType, rule.kind, emptyList()),
            )
            assertTrue(
                "mapping ${rule.legacyType} rejected: $outcome",
                outcome is LegacyAdapterOutcome.Canonicalized,
            )
        }
    }
}
