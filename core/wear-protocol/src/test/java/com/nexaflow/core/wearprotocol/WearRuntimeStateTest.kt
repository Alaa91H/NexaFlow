package com.nexaflow.core.wearprotocol

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WearRuntimeStateTest {

    @Before
    fun setUp() {
        WearRuntimeState.resetForTests()
    }

    @After
    fun tearDown() {
        WearRuntimeState.resetForTests()
    }

    @Test
    fun `unknown watch state never fabricates disconnected`() {
        assertNull(WearRuntimeState.conditionSatisfied(null, wantConnected = true))
        assertNull(WearRuntimeState.conditionSatisfied(null, wantConnected = false))
        assertNull(
            WearRuntimeState.conditionSatisfied(
                watchInstallId = "missing-watch",
                wantConnected = false,
            )
        )
    }

    @Test
    fun `first observation establishes state without fake transition`() {
        val transition = WearRuntimeState.upsertKnownDevice(
            watchInstallId = "watch-1",
            nodeId = "node-1",
            reachable = true,
            observedAtEpochMs = 1_000L,
        )

        assertNull(transition)
        assertEquals(true, WearRuntimeState.conditionSatisfied("watch-1", true))
        assertEquals(false, WearRuntimeState.conditionSatisfied("watch-1", false))
        assertEquals(true, WearRuntimeState.conditionSatisfied(null, true))
        assertEquals(false, WearRuntimeState.conditionSatisfied(null, false))
    }

    @Test
    fun `reachable node update emits disconnect transition and changes condition`() {
        WearRuntimeState.upsertKnownDevice(
            watchInstallId = "watch-1",
            nodeId = "node-1",
            reachable = true,
            observedAtEpochMs = 1_000L,
        )

        val transitions = WearRuntimeState.updateReachableNodeIds(
            reachableNodeIds = emptySet(),
            observedAtEpochMs = 2_000L,
        )

        assertEquals(1, transitions.size)
        assertFalse(transitions.single().connected)
        assertEquals("watch-1", transitions.single().watchInstallId)
        assertEquals(false, WearRuntimeState.conditionSatisfied(null, true))
        assertEquals(true, WearRuntimeState.conditionSatisfied(null, false))
    }

    @Test
    fun `any-watch selector remains connected while one of multiple watches is reachable`() {
        WearRuntimeState.upsertKnownDevice("watch-1", "node-1", true, 1_000L)
        WearRuntimeState.upsertKnownDevice("watch-2", "node-2", true, 1_000L)

        WearRuntimeState.updateReachableNodeIds(
            reachableNodeIds = setOf("node-2"),
            observedAtEpochMs = 2_000L,
        )

        assertTrue(WearRuntimeState.conditionSatisfied(null, true) == true)
        assertFalse(WearRuntimeState.conditionSatisfied(null, false) == true)
        assertEquals(true, WearRuntimeState.conditionSatisfied("watch-1", false))
        assertEquals(true, WearRuntimeState.conditionSatisfied("watch-2", true))
    }

    @Test
    fun `stale observation cannot roll back newer connection state`() {
        WearRuntimeState.upsertKnownDevice(
            watchInstallId = "watch-1",
            nodeId = "node-current",
            reachable = true,
            observedAtEpochMs = 2_000L,
        )

        val transition = WearRuntimeState.upsertKnownDevice(
            watchInstallId = "watch-1",
            nodeId = "node-stale",
            reachable = false,
            observedAtEpochMs = 1_000L,
        )

        assertNull(transition)
        val current = WearRuntimeState.stateFor("watch-1")
            ?: error("Expected watch-1")
        assertEquals("node-current", current.nodeId)
        assertTrue(current.reachable)
        assertEquals(2_000L, current.observedAtEpochMs)
    }
}
