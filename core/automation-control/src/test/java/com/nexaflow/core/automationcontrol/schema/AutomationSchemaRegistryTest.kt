package com.nexaflow.core.automationcontrol.schema

import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSchemaRegistryTest {

    @Test
    fun catalogCoversEveryPersistedNodeTypeExactlyOnce() {
        val snapshot = AutomationSchemaRegistry().snapshot()

        assertEquals(
            TriggerType.entries.map { it.name }.toSet(),
            snapshot.triggers.map { it.type }.toSet()
        )
        assertEquals(
            ActionType.entries.map { it.name }.toSet(),
            snapshot.actions.map { it.type }.toSet()
        )
        assertEquals(
            ConstraintType.entries.map { it.name }.toSet(),
            snapshot.constraints.map { it.type }.toSet()
        )
        assertEquals(snapshot.triggers.size, snapshot.triggers.map { it.id }.distinct().size)
        assertEquals(snapshot.actions.size, snapshot.actions.map { it.id }.distinct().size)
        assertTrue(snapshot.triggers.none { it.id.isBlank() })
        assertTrue(snapshot.actions.none { it.id.isBlank() })
    }
}
