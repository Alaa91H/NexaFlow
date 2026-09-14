package com.nexaflow.core.execution.capability

import com.nexaflow.core.rom.ShizukuShellBridge
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityAvailabilityReport
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityEnvironmentReport
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.capability.ExecutionPolicy
import com.nexaflow.domain.capability.VerificationMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CapabilityStateStore(
    private val registry: CapabilityRegistry,
    private val environmentInspector: CapabilityEnvironmentInspector,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val registerShizukuStateListener: ((() -> Unit) -> Unit) = ShizukuShellBridge::addStateListener,
    private val minRefreshIntervalMs: Long = DEFAULT_MIN_REFRESH_INTERVAL_MS
) {
    private val diagnostics = CapabilityDiagnostics(registry)
    private val refreshMutex = Mutex()
    private val _snapshot = MutableStateFlow(CapabilitySnapshot())
    private val _environmentReports = MutableStateFlow<List<CapabilityEnvironmentReport>>(emptyList())
    private val schedulerLock = Any()
    private var workerJob: kotlinx.coroutines.Job? = null
    private var refreshQueued = false
    @Volatile private var lastRefreshCompletedAtMs = Long.MIN_VALUE

    val snapshot: StateFlow<CapabilitySnapshot> = _snapshot.asStateFlow()
    val environmentReports: StateFlow<List<CapabilityEnvironmentReport>> = _environmentReports.asStateFlow()

    init { registerShizukuStateListener(::invalidate) }

    fun invalidate() {
        synchronized(schedulerLock) {
            refreshQueued = true
            if (workerJob != null) return
            workerJob = scope.launch {
                try {
                    while (true) {
                        synchronized(schedulerLock) {
                            if (!refreshQueued) return@launch
                            refreshQueued = false
                        }
                        val wait = refreshDelayMs(nowMs())
                        if (wait > 0) delay(wait)
                        try {
                            refreshNow()
                        } catch (error: Throwable) {
                            if (error is CancellationException) throw error
                        }
                    }
                } finally {
                    val shouldRestart = synchronized(schedulerLock) {
                        workerJob = null
                        refreshQueued
                    }
                    if (shouldRestart) invalidate()
                }
            }
        }
    }

    fun refresh() = invalidate()

    private fun refreshDelayMs(now: Long): Long {
        if (lastRefreshCompletedAtMs == Long.MIN_VALUE) return 0L
        val elapsed = (now - lastRefreshCompletedAtMs).coerceAtLeast(0L)
        return (minRefreshIntervalMs - elapsed).coerceAtLeast(0L)
    }

    private suspend fun refreshNow() = refreshMutex.withLock {
        val reports = registry.descriptors().associate { descriptor ->
            descriptor.id to diagnosticReportFor(descriptor.id)
        }
        _snapshot.value = CapabilitySnapshot(reports = reports, observedAtMs = nowMs())
        _environmentReports.value = environmentInspector.reports()
        lastRefreshCompletedAtMs = nowMs()
    }

    companion object { const val DEFAULT_MIN_REFRESH_INTERVAL_MS = 30_000L }

    private suspend fun diagnosticReportFor(capability: CapabilityId): CapabilityAvailabilityReport {
        val reports = buildList {
            for (request in diagnosticRequestsFor(capability)) add(diagnostics.report(request))
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
            CapabilityRequest(capability = capability, parameters = mapOf("url" to "https://example.com"), verification = VerificationMode.NONE)
        )
        CapabilityId.SETTINGS_LAUNCH -> listOf(
            CapabilityRequest(capability = capability, parameters = mapOf("page" to "WIFI"), verification = VerificationMode.NONE)
        )
        else -> listOf(CapabilityRequest(capability = capability, verification = VerificationMode.NONE))
    }

    private fun privilegedProbe(capability: CapabilityId, backend: CapabilityBackendId) = CapabilityRequest(
        capability = capability,
        policy = ExecutionPolicy(allowedBackends = listOf(backend), allowPrivilegedBackends = true),
        verification = VerificationMode.NONE
    )
}
