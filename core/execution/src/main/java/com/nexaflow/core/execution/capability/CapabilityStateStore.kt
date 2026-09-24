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
    private var immediateRefreshQueued = false
    @Volatile private var lastRefreshCompletedAtMs = Long.MIN_VALUE

    val snapshot: StateFlow<CapabilitySnapshot> = _snapshot.asStateFlow()
    val environmentReports: StateFlow<List<CapabilityEnvironmentReport>> = _environmentReports.asStateFlow()

    init { registerShizukuStateListener(::invalidate) }

    fun invalidate() = scheduleRefresh(immediate = false)

    /**
     * Explicit/user-visible refresh. Unlike passive invalidation, this bypasses
     * the long anti-storm backoff so a newly granted permission is observable
     * immediately. Repeated events are still coalesced into one worker.
     */
    fun refresh() = scheduleRefresh(immediate = true)

    private fun scheduleRefresh(immediate: Boolean) {
        synchronized(schedulerLock) {
            refreshQueued = true
            if (immediate) {
                immediateRefreshQueued = true
            }
            if (workerJob != null) {
                if (immediate) {
                    // Wake a worker that may currently be sleeping in the
                    // passive 30s backoff. Cancellation is safe: the mutex and
                    // last coherent snapshot are preserved, and finally below
                    // restarts the queued urgent refresh.
                    workerJob?.cancel()
                }
                return
            }
            workerJob = scope.launch {
                try {
                    while (true) {
                        val runImmediately = synchronized(schedulerLock) {
                            if (!refreshQueued) return@launch
                            refreshQueued = false
                            immediateRefreshQueued.also { immediateRefreshQueued = false }
                        }
                        val wait = if (runImmediately) 0L else refreshDelayMs(nowMs())
                        if (wait > 0) delay(wait)
                        try {
                            refreshNow()
                        } catch (error: Throwable) {
                            if (error is CancellationException) throw error
                        }
                    }
                } finally {
                    val restart = synchronized(schedulerLock) {
                        workerJob = null
                        refreshQueued to immediateRefreshQueued
                    }
                    if (restart.first) {
                        // Preserve an urgent request that arrived while the
                        // previous worker was shutting down.
                        scheduleRefresh(immediate = restart.second)
                    }
                }
            }
        }
    }

    /**
     * Returns a snapshot the caller can base a decision on: requests a
     * refresh, then waits (bounded) for an observation made at or after the
     * request. Save flows must use this instead of reading [snapshot].value
     * directly — the async worker otherwise loses the race and the pre-scan
     * answer silently disables runnable tasks.
     *
     * Explicit refresh bypasses the passive invalidation backoff, while the
     * wait remains bounded so a slow OEM/privileged probe can never hang UI.
     */
    suspend fun freshSnapshot(budgetMs: Long = DEFAULT_FRESH_SNAPSHOT_BUDGET_MS): CapabilitySnapshot {
        val before = _snapshot.value.observedAtMs
        refresh()
        val deadline = nowMs() + budgetMs
        var current = _snapshot.value
        while (current.observedAtMs == before && nowMs() < deadline) {
            delay(50)
            current = _snapshot.value
        }
        return current
    }

    private fun refreshDelayMs(now: Long): Long {
        if (lastRefreshCompletedAtMs == Long.MIN_VALUE) return 0L
        val elapsed = (now - lastRefreshCompletedAtMs).coerceAtLeast(0L)
        return (minRefreshIntervalMs - elapsed).coerceAtLeast(0L)
    }

    private suspend fun refreshNow() = refreshMutex.withLock {
        val reports = registry.descriptors().associate { descriptor ->
            descriptor.id to diagnosticReportFor(descriptor.id)
        }
        val environmentReports = environmentInspector.reports()
        val observedAtMs = nowMs()

        _snapshot.value = CapabilitySnapshot(reports = reports, observedAtMs = observedAtMs)
        _environmentReports.value = environmentReports
        lastRefreshCompletedAtMs = observedAtMs
    }

    companion object {
        const val DEFAULT_MIN_REFRESH_INTERVAL_MS = 30_000L

        /** Upper bound on how long a decision flow may wait for a fresh scan. */
        const val DEFAULT_FRESH_SNAPSHOT_BUDGET_MS = 4_000L
    }

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
