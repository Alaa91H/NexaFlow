package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.core.rom.ShizukuShellBridge
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Environment change events that require **targeted** capability invalidation.
 * A Shizuku binder death, for example, must invalidate only Shizuku-backed
 * evidence — not trigger a full world rescan. The bus carries no payload
 * beyond the affected scope; subscribers re-query their own live state.
 */
sealed interface EnvironmentEvent {
    /** A Shizuku lifecycle transition (binder received/died, rebind). */
    data object ShizukuStateChanged : EnvironmentEvent
    /** Root grant was granted, revoked or re-confirmed. */
    data object RootGrantChanged : EnvironmentEvent
    /** Accessibility service enablement changed. */
    data object AccessibilityChanged : EnvironmentEvent
    /** Device-owner / managed-profile state changed. */
    data object DeviceOwnerChanged : EnvironmentEvent
    /** A runtime permission result was delivered. */
    data class PermissionChanged(val permission: String) : EnvironmentEvent
    /** A relevant package was added/updated/replaced. */
    data object PackageEnvironmentChanged : EnvironmentEvent
    /** The ROM/build environment changed (OTA, reboot into a different ROM). */
    data object RomEnvironmentChanged : EnvironmentEvent
}

class EnvironmentEventBus {
    private val _events = MutableSharedFlow<EnvironmentEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<EnvironmentEvent> = _events.asSharedFlow()

    suspend fun publish(event: EnvironmentEvent) {
        _events.emit(event)
    }

    /** Fire-and-forget variant for binder threads and listeners. */
    fun tryPublish(event: EnvironmentEvent) {
        _events.tryEmit(event)
    }
}

/**
 * Applies environment events to the evidence/health stores with the narrowest
 * possible blast radius. This is the single place that maps "an environment
 * changed" to "these evidence records are stale".
 */
class EnvironmentInvalidator(
    private val evidenceStore: CapabilityEvidenceStore,
    private val healthTracker: StrategyHealthTracker
) {
    suspend fun apply(event: EnvironmentEvent, deviceKey: String) {
        when (event) {
            EnvironmentEvent.ShizukuStateChanged -> invalidateStrategies(
                deviceKey, setOf(StrategyId.SHIZUKU_USER_SERVICE)
            )
            EnvironmentEvent.RootGrantChanged -> invalidateStrategies(
                deviceKey, setOf(StrategyId.ROOT_SHELL)
            )
            EnvironmentEvent.AccessibilityChanged -> invalidateStrategies(
                deviceKey, setOf(StrategyId.ANDROID_PUBLIC_API, StrategyId.OEM_SPECIFIC)
            )
            EnvironmentEvent.DeviceOwnerChanged -> invalidateStrategies(
                deviceKey, setOf(StrategyId.DEVICE_OWNER)
            )
            EnvironmentEvent.RomEnvironmentChanged -> {
                // Full local reset: the execution environment identity changed.
                evidenceStore.invalidate { it.deviceKey == deviceKey }
            }
            is EnvironmentEvent.PermissionChanged,
            EnvironmentEvent.PackageEnvironmentChanged -> {
                // Runtime permission and package changes affect per-operation
                // availability; strategy trust survives. Health is cheap to
                // relearn, evidence is not, so only health is reset.
                healthResetAll(deviceKey)
            }
        }
    }

    private fun invalidateStrategies(deviceKey: String, strategies: Set<StrategyId>) {
        evidenceStore.invalidate {
            it.deviceKey == deviceKey && it.strategy in strategies
        }
    }

    private fun healthResetAll(deviceKey: String) {
        StrategyId.entries.forEach { healthTracker.reset(it, deviceKey) }
    }
}

/** The strategies a semantic operation may name; used by parity tests. */
fun Iterable<StrategyId>.requireDistinct(): List<StrategyId> = distinct()

/**
 * Wires [EnvironmentEventBus] to the real platform listeners. Every
 * registration is idempotent (safe to call from DI and recovery paths alike)
 * and each listener publishes the **targeted** event only: a Shizuku binder
 * death invalidates Shizuku-backed evidence — it never triggers a full
 * capability rescan.
 */
class EnvironmentEventWiring(
    private val bus: EnvironmentEventBus,
    /** Listener-registration seam; the production default is the real bridge. */
    private val registerShizukuListener: ((() -> Unit) -> Unit) = ShizukuShellBridge::addStateListener
) {
    private val wired = java.util.concurrent.atomic.AtomicBoolean(false)

    fun wireShizukuLifecycle() {
        if (!wired.compareAndSet(false, true)) return
        // The bridge invokes each listener immediately on registration (sticky
        // binder state) and on every real transition: binder received, binder
        // dead, UserService connected/disconnected, rebind.
        registerShizukuListener {
            bus.tryPublish(EnvironmentEvent.ShizukuStateChanged)
        }
    }
}
