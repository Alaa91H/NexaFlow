package com.nexaflow.core.wearprotocol

/**
 * Process-local, typed snapshot of known Wear OS devices and their current
 * reachability. The phone-side Data Layer bridge is the only writer; trigger
 * evaluation and routing are read-only consumers.
 *
 * A missing device is UNKNOWN, never silently treated as disconnected. For the
 * "any watch" selector, DISCONNECTED becomes true only after at least one watch
 * has been discovered and none of the known watches is currently reachable.
 */
object WearRuntimeState {

    data class DeviceState(
        val watchInstallId: String,
        val nodeId: String?,
        val reachable: Boolean,
        val observedAtEpochMs: Long,
    )

    data class ConnectionTransition(
        val watchInstallId: String,
        val nodeId: String?,
        val connected: Boolean,
        val observedAtEpochMs: Long,
    )

    @Volatile
    private var devices: Map<String, DeviceState> = emptyMap()

    fun snapshot(): List<DeviceState> = devices.values.toList()

    fun stateFor(watchInstallId: String): DeviceState? = devices[watchInstallId]

    /**
     * Adds or refreshes a known device. The first observation establishes
     * state without emitting a transition; process/bootstrap reconciliation
     * handles already-connected watches without fabricating a new event.
     */
    @Synchronized
    fun upsertKnownDevice(
        watchInstallId: String,
        nodeId: String?,
        reachable: Boolean,
        observedAtEpochMs: Long,
    ): ConnectionTransition? {
        val existing = devices[watchInstallId]
        if (
            existing != null &&
            observedAtEpochMs < existing.observedAtEpochMs
        ) {
            return null
        }

        val updated = DeviceState(
            watchInstallId = watchInstallId,
            nodeId = nodeId,
            reachable = reachable,
            observedAtEpochMs = observedAtEpochMs,
        )
        devices = devices + (watchInstallId to updated)

        if (existing == null || existing.reachable == reachable) return null
        return ConnectionTransition(
            watchInstallId = watchInstallId,
            nodeId = nodeId,
            connected = reachable,
            observedAtEpochMs = observedAtEpochMs,
        )
    }

    /**
     * Applies the authoritative set of currently reachable Data Layer node ids
     * and returns only genuine connected/disconnected transitions.
     */
    @Synchronized
    fun updateReachableNodeIds(
        reachableNodeIds: Set<String>,
        observedAtEpochMs: Long,
    ): List<ConnectionTransition> {
        if (devices.isEmpty()) return emptyList()

        val transitions = ArrayList<ConnectionTransition>()
        val updated = LinkedHashMap<String, DeviceState>(devices.size)
        devices.forEach { (installId, current) ->
            val reachable = current.nodeId != null && current.nodeId in reachableNodeIds
            val next = current.copy(
                reachable = reachable,
                observedAtEpochMs = maxOf(current.observedAtEpochMs, observedAtEpochMs),
            )
            updated[installId] = next
            if (current.reachable != reachable) {
                transitions += ConnectionTransition(
                    watchInstallId = installId,
                    nodeId = current.nodeId,
                    connected = reachable,
                    observedAtEpochMs = observedAtEpochMs,
                )
            }
        }
        devices = updated
        return transitions
    }

    /**
     * Returns true/false only when the requested current state is known.
     * A blank selector means "any known watch".
     */
    fun conditionSatisfied(
        watchInstallId: String?,
        wantConnected: Boolean,
    ): Boolean? {
        val current = devices
        val selector = watchInstallId?.trim().orEmpty()
        if (selector.isNotEmpty()) {
            val device = current[selector] ?: return null
            return device.reachable == wantConnected
        }

        if (current.isEmpty()) return null
        val anyReachable = current.values.any { it.reachable }
        return if (wantConnected) anyReachable else !anyReachable
    }

    @Synchronized
    internal fun resetForTests() {
        devices = emptyMap()
    }
}
