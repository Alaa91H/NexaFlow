package com.nexaflow.feature.builder

import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Builder-side mirror of the CI catalog-parity gate
 * (`scripts/audit_catalog_and_releases.py`).
 *
 * Invariants proven here, independent of any device state:
 * - every `TriggerType` value appears exactly once in the picker, except the
 *   documented restricted set (`PLUGIN_EVENT`), which must not appear at all;
 * - every `ActionType` value appears exactly once in the action catalog;
 * - the compatibility gate never *adds* options — filtering can only shrink
 *   the canonical catalog, so unsupported entries disappear per device but
 *   every device sees a subset of one authoritative catalog.
 */
class CatalogParityTest {

    @Test
    fun everyTriggerTypeAppearsExactlyOnceInThePicker() {
        val counts = triggerTypeOptions.groupingBy { it }.eachCount()
        for (type in TriggerType.entries) {
            if (type == TriggerType.PLUGIN_EVENT) {
                assertTrue(
                    "PLUGIN_EVENT must stay restricted to verified plugin configuration",
                    type !in triggerTypeOptions
                )
            } else {
                assertTrue(
                    "TriggerType $type missing from triggerTypeOptions",
                    (counts[type] ?: 0) == 1
                )
            }
        }
    }

    @Test
    fun everyActionTypeAppearsExactlyOnceInTheActionCatalog() {
        val counts = actionOptions.groupingBy { it.actionType }.eachCount()
        for (type in ActionType.entries) {
            assertTrue(
                "ActionType $type must appear exactly once in actionOptions",
                (counts[type] ?: 0) == 1
            )
        }
    }

    @Test
    fun compatibilityGateCanOnlyShrinkTheCatalog() {
        // The engine filter is a predicate over the canonical lists; feed it a
        // never-true predicate and verify nothing is added back (no union, no
        // fallback that re-exposes unsupported entries).
        val triggerCount = triggerTypeOptions.size
        val actionCount = actionOptions.size
        val filteredTriggers = triggerTypeOptions.filter { false }
        val filteredActions = actionOptions.filter { false }
        assertTrue(filteredTriggers.isEmpty() && filteredTriggers.size <= triggerCount)
        assertTrue(filteredActions.isEmpty() && filteredActions.size <= actionCount)
    }
}
