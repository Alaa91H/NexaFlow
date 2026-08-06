package com.nexaflow.feature.widgets

import com.nexaflow.domain.models.Automation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileTargetResolverTest {

    private fun task(id: String, enabled: Boolean) = Automation(
        id = id,
        name = id,
        description = "",
        icon = "",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "",
        priority = 0,
        enabled = enabled,
        triggers = emptyList(),
        actions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `explicit binding wins over automatic resolution`() {
        val automations = listOf(task("a", true), task("b", false))
        val result = TileTargetResolver.resolveTarget(automations, slot = 1, boundId = "b")
        assertEquals("b", result?.id)
    }

    @Test
    fun `stale binding falls back to enabled task`() {
        val automations = listOf(task("a", true), task("b", false))
        val result = TileTargetResolver.resolveTarget(automations, slot = 1, boundId = "ghost")
        assertEquals("a", result?.id)
    }

    @Test
    fun `slot 1 picks the first enabled task`() {
        val automations = listOf(task("a", false), task("b", true), task("c", true))
        assertEquals("b", TileTargetResolver.resolveTarget(automations, 1, null)?.id)
    }

    @Test
    fun `slot 2 picks the second enabled task`() {
        val automations = listOf(task("a", false), task("b", true), task("c", true))
        assertEquals("c", TileTargetResolver.resolveTarget(automations, 2, null)?.id)
    }

    @Test
    fun `slot beyond enabled count falls back to any task`() {
        val automations = listOf(task("a", false), task("b", false))
        assertEquals("a", TileTargetResolver.resolveTarget(automations, 4, null)?.id)
    }

    @Test
    fun `empty list resolves to null`() {
        assertNull(TileTargetResolver.resolveTarget(emptyList(), 1, null))
    }
}
