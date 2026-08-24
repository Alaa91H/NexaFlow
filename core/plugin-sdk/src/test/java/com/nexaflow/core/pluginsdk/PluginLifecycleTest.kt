package com.nexaflow.core.pluginsdk

import org.junit.Assert.*
import org.junit.Test

class PluginLifecycleTest {

    @Test
    fun `initial state is null for unknown plugin`() {
        val manager = InMemoryPluginLifecycleManager()
        assertNull(manager.stateFor("unknown.plugin"))
    }

    @Test
    fun `Discovered event sets state to DISCOVERED`() {
        val manager = InMemoryPluginLifecycleManager()
        manager.transition(PluginLifecycleEvent.Discovered("com.example.plugin"))
        assertEquals(PluginLifecycleState.DISCOVERED, manager.stateFor("com.example.plugin"))
    }

    @Test
    fun `ValidationPassed sets state to LOADED`() {
        val manager = InMemoryPluginLifecycleManager()
        manager.transition(PluginLifecycleEvent.Discovered("p"))
        manager.transition(PluginLifecycleEvent.ValidationPassed("p"))
        assertEquals(PluginLifecycleState.LOADED, manager.stateFor("p"))
    }

    @Test
    fun `ValidationFailed sets state to INVALID`() {
        val manager = InMemoryPluginLifecycleManager()
        manager.transition(PluginLifecycleEvent.Discovered("p"))
        manager.transition(PluginLifecycleEvent.ValidationFailed("p", listOf(PluginManifestIssue.INVALID_PACKAGE)))
        assertEquals(PluginLifecycleState.INVALID, manager.stateFor("p"))
    }

    @Test
    fun `HealthCheckPassed sets state to ACTIVE`() {
        val manager = InMemoryPluginLifecycleManager()
        manager.transition(PluginLifecycleEvent.Discovered("p"))
        manager.transition(PluginLifecycleEvent.ValidationPassed("p"))
        manager.transition(PluginLifecycleEvent.HealthCheckPassed("p"))
        assertEquals(PluginLifecycleState.ACTIVE, manager.stateFor("p"))
    }

    @Test
    fun `ACTIVE plugin appears in activePlugins`() {
        val manager = InMemoryPluginLifecycleManager()
        manager.transition(PluginLifecycleEvent.Discovered("p"))
        manager.transition(PluginLifecycleEvent.ValidationPassed("p"))
        manager.transition(PluginLifecycleEvent.HealthCheckPassed("p"))
        assertNotNull(manager.activePlugins())
        assertTrue(manager.activePlugins().contains("p"))
    }

    @Test
    fun `Paused plugin removed from activePlugins`() {
        val manager = InMemoryPluginLifecycleManager()
        manager.transition(PluginLifecycleEvent.Discovered("p"))
        manager.transition(PluginLifecycleEvent.ValidationPassed("p"))
        manager.transition(PluginLifecycleEvent.HealthCheckPassed("p"))
        manager.transition(PluginLifecycleEvent.Paused("p"))
        assertFalse(manager.activePlugins().contains("p"))
    }

    @Test
    fun `listener receives events`() {
        val manager = InMemoryPluginLifecycleManager()
        val received = mutableListOf<PluginLifecycleEvent>()
        manager.addListener { received.add(it) }
        manager.transition(PluginLifecycleEvent.Discovered("p"))
        manager.transition(PluginLifecycleEvent.ValidationPassed("p"))
        assertEquals(2, received.size)
        assertEquals(PluginLifecycleState.DISCOVERED, received[0].newState)
        assertEquals(PluginLifecycleState.LOADED, received[1].newState)
    }

    @Test
    fun `removeListener stops receiving events`() {
        val manager = InMemoryPluginLifecycleManager()
        val received = mutableListOf<PluginLifecycleEvent>()
        val listener = PluginLifecycleListener { received.add(it) }
        manager.addListener(listener)
        manager.transition(PluginLifecycleEvent.Discovered("p"))
        manager.removeListener(listener)
        manager.transition(PluginLifecycleEvent.ValidationPassed("p"))
        assertEquals(1, received.size)
    }

    @Test
    fun `Unloaded event sets state to UNLOADED`() {
        val manager = InMemoryPluginLifecycleManager()
        manager.transition(PluginLifecycleEvent.Discovered("p"))
        manager.transition(PluginLifecycleEvent.ValidationPassed("p"))
        manager.transition(PluginLifecycleEvent.HealthCheckPassed("p"))
        manager.transition(PluginLifecycleEvent.Unloaded("p", "Package uninstalled"))
        assertEquals(PluginLifecycleState.UNLOADED, manager.stateFor("p"))
    }
}
