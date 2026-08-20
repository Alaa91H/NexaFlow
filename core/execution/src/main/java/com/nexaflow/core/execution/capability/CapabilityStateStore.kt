package com.nexaflow.core.execution.capability

import com.nexaflow.core.rom.ShizukuShellBridge
import com.nexaflow.core.rom.SystemAppStatusDetector
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityAvailabilityReport
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityEnvironmentReport
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.capability.ExecutionPolicy
import com.nexaflow.domain.capability.VerificationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Application-scoped, event-invalidated cache of the capability engine's live
 * answers. This class never polls: a caller requests an explicit refresh or an
 * adapter invalidates it after a verified platform event (for example Shizuku
 * binder/UserService lifecycle changes).
 */
class CapabilityStateStore(
    private val registry: CapabilityRegistry,
    private val environmentInspector: CapabilityEnvironmentInspector,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val registerShizukuStateListener: ((() -> Unit) -> Unit) = ShizukuShellBridge::addStateListener
) {
    private val diagnostics = CapabilityDiagnostics(registry)
    private val refreshMutex = Mutex()
    private val _snapshot = MutableStateFlow(CapabilitySnapshot())
    private val _environmentReports = MutableStateFlow<List<CapabilityEnvironmentReport>>(emptyList())

    /** Immutable availability observation used by action, trigger, template and workflow filtering. */
    val snapshot: StateFlow<CapabilitySnapshot> = _snapshot.asStateFlow()

    /** Read-only explanation of optional execution-environment state for Settings only. */
    val environmentReports: StateFlow<List<CapabilityEnvironmentReport>> = _environmentReports.asStateFlow()

    init {
        // addStateListener invokes immediately, producing the initial snapshot;
        // subsequent callbacks originate only from binder/UserService lifecycle.
        registerShizukuStateListener(::invalidate)
    }

    /** Queues a refresh after a real capability-state event; duplicate calls are serialized. */
    fun invalidate() {
        scope.launch { refreshNow() }
    }

    /** Explicit user- or lifecycle-initiated refresh. This is not a periodic probe. */
    fun refresh() = invalidate()

    private suspend fun refreshNow() = refreshMutex.withLock {
        // Root does not offer a normal-app callback; clear only its short-lived
        // detector cache on an explicit/event-driven refresh before observing it.
        SystemAppStatusDetector.refreshRootAvailability()
        val reports = registry.descriptors().associate { descriptor ->
            descriptor.id to diagnosticReportFor(descriptor.id)
        }
        _snapshot.value = CapabilitySnapshot(reports = reports, observedAtMs = nowMs())
        _environmentReports.value = environmentInspector.reports()
    }

    /**
     * A capability snapshot asks whether a descriptor has at least one usable
     * backend, not whether a future action's parameters have already been
     * chosen. Privileged probes opt in to one channel at a time because the
     * backends correctly reject implicit Root/Shizuku fallback during execution.
     */
    private suspend fun diagnosticReportFor(capability: CapabilityId): CapabilityAvailabilityReport {
        val reports = buildList {
            for (request in diagnosticRequestsFor(capability)) {
                add(diagnostics.report(request))
            }
        }
        val candidates = reports.flatMap(CapabilityAvailabilityReport::backends)
        val availability = when {
            candidates.any { it.availability == CapabilityAvailability.AVAILABLE } -> CapabilityAvailability.AVAILABLE
            candidates.any { it.availability == CapabilityAvailability.PARTIAL } -> CapabilityAvailability.PARTIAL
            candidates.any { it.availability == CapabilityAvailability.PERMISSION_REQUIRED } -> CapabilityAvailability.PERMISSION_REQUIRED
            candidates.any { it.availability == CapabilityAvailability.UNAVAILABLE } -> CapabilityAvailability.UNAVAILABLE
            else -> CapabilityAvailability.UNSUPPORTED
        }
        return CapabilityAvailabilityReport(
            capability = capability,
            availability = availability,
            backends = candidates.distinctBy { Triple(it.backend, it.availability, it.reason) },
            reason = candidates.firstOrNull { it.availability == availability }?.reason
                ?: candidates.firstOrNull { it.reason != null }?.reason
        )
    }

    private fun diagnosticRequestsFor(capability: CapabilityId): List<CapabilityRequest> = when (capability) {
        CapabilityId.PACKAGE_FORCE_STOP,
        CapabilityId.PACKAGE_SET_ENABLED,
        CapabilityId.SYSTEM_SETTING_WRITE,
        CapabilityId.FILE_COPY -> listOf(
            privilegedProbe(capability, CapabilityBackendId.SHIZUKU),
            privilegedProbe(capability, CapabilityBackendId.ROOT)
        )
        CapabilityId.INTENT_LAUNCH -> listOf(
            CapabilityRequest(
                capability = capability,
                parameters = mapOf("url" to "https://example.com"),
                verification = VerificationMode.NONE
            )
        )
        CapabilityId.SETTINGS_LAUNCH -> listOf(
            CapabilityRequest(
                capability = capability,
                parameters = mapOf("page" to "WIFI"),
                verification = VerificationMode.NONE
            )
        )
        else -> listOf(CapabilityRequest(capability = capability, verification = VerificationMode.NONE))
    }

    private fun privilegedProbe(
        capability: CapabilityId,
        backend: CapabilityBackendId
    ) = CapabilityRequest(
        capability = capability,
        policy = ExecutionPolicy(
            allowedBackends = listOf(backend),
            allowPrivilegedBackends = true
        ),
        verification = VerificationMode.NONE
    )
}
