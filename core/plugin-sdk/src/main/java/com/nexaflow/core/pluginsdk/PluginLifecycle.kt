package com.nexaflow.core.pluginsdk

/**
 * Full plugin lifecycle contract for NexaFlow plugins.
 *
 * Plugins are external packages; NexaFlow never loads their code directly.
 * This lifecycle is modeled on the host side: each phase is a host-driven
 * decision that may involve querying the plugin's Android component state.
 *
 * The lifecycle follows:
 *
 *   DISCOVERED → VALIDATING → INVALID  (validation failed)
 *                           ↓
 *                         LOADED → INITIALIZING → UNHEALTHY  (health check failed)
 *                                              ↓
 *                                           ACTIVE ⇄ PAUSED
 *                                              ↓
 *                                           UNLOADED ← UPDATING
 *
 * All state transitions are reported through [PluginLifecycleListener].
 */

/** Observable state of a registered plugin. */
enum class PluginLifecycleState {
    /** Plugin package discovered by [PluginDiscoveryRegistry]. */
    DISCOVERED,
    /** Manifest and component validation in progress. */
    VALIDATING,
    /** Validation failed; plugin will not be executed. */
    INVALID,
    /** Validation passed; loading is complete. */
    LOADED,
    /** Host is performing initial capability health check. */
    INITIALIZING,
    /** Health check detected a problem; plugin is quarantined. */
    UNHEALTHY,
    /** Plugin is active and accepting invocations. */
    ACTIVE,
    /** Plugin is temporarily suspended (e.g., user disabled automation). */
    PAUSED,
    /** Plugin package was uninstalled or explicitly removed. */
    UNLOADED,
    /** An update to the plugin package is being validated. */
    UPDATING
}

/** Reason for a lifecycle state transition. */
sealed interface PluginLifecycleEvent {
    val pluginId: String
    val newState: PluginLifecycleState
    val timestampMs: Long get() = System.currentTimeMillis()

    data class Discovered(override val pluginId: String) : PluginLifecycleEvent {
        override val newState = PluginLifecycleState.DISCOVERED
    }

    data class ValidationPassed(override val pluginId: String) : PluginLifecycleEvent {
        override val newState = PluginLifecycleState.LOADED
    }

    data class ValidationFailed(
        override val pluginId: String,
        val issues: List<PluginManifestIssue>
    ) : PluginLifecycleEvent {
        override val newState = PluginLifecycleState.INVALID
    }

    data class HealthCheckPassed(override val pluginId: String) : PluginLifecycleEvent {
        override val newState = PluginLifecycleState.ACTIVE
    }

    data class HealthCheckFailed(
        override val pluginId: String,
        val reason: String
    ) : PluginLifecycleEvent {
        override val newState = PluginLifecycleState.UNHEALTHY
    }

    data class Paused(override val pluginId: String) : PluginLifecycleEvent {
        override val newState = PluginLifecycleState.PAUSED
    }

    data class Resumed(override val pluginId: String) : PluginLifecycleEvent {
        override val newState = PluginLifecycleState.ACTIVE
    }

    data class Unloaded(override val pluginId: String, val reason: String) : PluginLifecycleEvent {
        override val newState = PluginLifecycleState.UNLOADED
    }

    data class UpdateStarted(override val pluginId: String) : PluginLifecycleEvent {
        override val newState = PluginLifecycleState.UPDATING
    }

    data class UpdateCompleted(override val pluginId: String) : PluginLifecycleEvent {
        override val newState = PluginLifecycleState.LOADED
    }

    data class UpdateFailed(override val pluginId: String, val reason: String) : PluginLifecycleEvent {
        override val newState = PluginLifecycleState.UNHEALTHY
    }
}

/** Observer for plugin lifecycle state changes. */
fun interface PluginLifecycleListener {
    fun onLifecycleEvent(event: PluginLifecycleEvent)
}

/**
 * Manages the lifecycle state of all registered plugins.
 * Implementations emit [PluginLifecycleEvent]s to registered listeners.
 */
interface PluginLifecycleManager {
    /** Current state of the plugin with [pluginId], or null if not tracked. */
    fun stateFor(pluginId: String): PluginLifecycleState?

    /** All currently active/paused plugins. */
    fun activePlugins(): List<String>

    /** Transition [pluginId] by emitting [event] and persisting the new state. */
    fun transition(event: PluginLifecycleEvent)

    /** Register a listener for lifecycle events. */
    fun addListener(listener: PluginLifecycleListener)

    /** Remove a previously registered listener. */
    fun removeListener(listener: PluginLifecycleListener)
}

/**
 * In-memory [PluginLifecycleManager] for testing and initial integration.
 */
class InMemoryPluginLifecycleManager : PluginLifecycleManager {
    private val states = mutableMapOf<String, PluginLifecycleState>()
    private val listeners = mutableListOf<PluginLifecycleListener>()
    private val lock = Any()

    override fun stateFor(pluginId: String): PluginLifecycleState? =
        synchronized(lock) { states[pluginId] }

    override fun activePlugins(): List<String> = synchronized(lock) {
        states.entries
            .filter { it.value == PluginLifecycleState.ACTIVE }
            .map { it.key }
    }

    override fun transition(event: PluginLifecycleEvent) {
        synchronized(lock) {
            states[event.pluginId] = event.newState
            listeners.toList()
        }.forEach { it.onLifecycleEvent(event) }
    }

    override fun addListener(listener: PluginLifecycleListener) {
        synchronized(lock) { listeners.add(listener) }
    }

    override fun removeListener(listener: PluginLifecycleListener) {
        synchronized(lock) { listeners.remove(listener) }
    }
}
