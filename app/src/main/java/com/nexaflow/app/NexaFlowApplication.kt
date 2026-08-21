package com.nexaflow.app

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nexaflow.app.work.LocationCheckScheduler
import com.nexaflow.app.work.MaintenanceWorker
import com.nexaflow.app.work.UpdateCheckScheduler
import com.nexaflow.core.datastore.LocationPreferences
import com.nexaflow.core.datastore.UpdatePreferences
import com.nexaflow.core.engine.AutomationScheduler
import com.nexaflow.core.engine.MonitoringService
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.capability.CapabilityStateStore
import com.nexaflow.core.execution.recovery.ExecutionRecoveryCoordinator
import com.nexaflow.core.rom.ShizukuShellBridge
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NexaFlowApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var scheduler: AutomationScheduler

    @Inject
    lateinit var sentryReporter: SentryReporter

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var locationPreferences: LocationPreferences

    @Inject
    lateinit var updatePreferences: UpdatePreferences

    @Inject
    lateinit var executionRecoveryCoordinator: ExecutionRecoveryCoordinator

    /** Eager singleton attachment for event-driven capability-state invalidation. */
    @Inject
    lateinit var capabilityStateStore: CapabilityStateStore

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    /**
     * WorkManager must construct MaintenanceWorker through Hilt (it has an
     * @AssistedInject constructor — the default factory would fail with "no
     * default constructor"). The default androidx.startup initializer is
     * removed in the manifest so this configuration is the one used.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }
        // Everything below is best-effort startup wiring: a failure in any
        // single piece (Shizuku bridge, Sentry, WorkManager, scheduler,
        // monitoring service) must never prevent the app from opening.
        runCatching { ShizukuShellBridge.initialize(this) }
            .onFailure { Log.e(TAG, "Shizuku init failed", it) }
        runCatching { sentryReporter.attach() }
            .onFailure { Log.e(TAG, "Sentry attach failed", it) }
        runCatching { MaintenanceWorker.schedule(this) }
            .onFailure { Log.e(TAG, "Maintenance worker schedule failed", it) }
        // Periodic location re-check (Settings > Location): schedule at the
        // user's chosen interval so location-triggered tasks keep verifying
        // even while the system location switch is off.
        runCatching {
            appScope.launch {
                LocationCheckScheduler.schedule(
                    this@NexaFlowApplication,
                    locationPreferences.checkIntervalMinutes.first()
                )
            }
        }.onFailure { Log.e(TAG, "Location check schedule failed", it) }
        // The persisted update settings are the single source of truth. The
        // default disabled value cancels stale work from older installs; later
        // user edits replace or cancel the unique periodic request immediately.
        appScope.launch {
            updatePreferences.settings.collect { settings ->
                runCatching {
                    UpdateCheckScheduler.schedule(this@NexaFlowApplication, settings)
                }.onFailure { Log.e(TAG, "Update check schedule failed", it) }
            }
        }
        runCatching { scheduler.initialize() }
            .onFailure { Log.e(TAG, "Scheduler init failed", it) }
        // Recovery claims only durable checkpoints. It never replays an action
        // at startup; unknown side effects remain explicitly diagnostic until a
        // workflow-aware verifier/compensator handles them.
        appScope.launch {
            runCatching { executionRecoveryCoordinator.reconcileStartup() }
                .onFailure { Log.e(TAG, "execution recovery scan failed", it) }
        }
        try {
            MonitoringService.start(this)
        } catch (_: Throwable) {
            // Foreground service start can be restricted by the OS (e.g. a
            // process recreation in the background). Schedule a short alarm
            // instead; the receiver starts monitoring from the alarm context.
            MonitoringService.scheduleStart(this)
        }
    }

    /**
     * Debug-only watchdog: logs main-thread disk I/O, unbounded receivers,
     * leaked activities, and other policy violations, and crashes ONLY on
     * main-thread NETWORK — the same class of bug as the in-app update
     * checker downloading APKs on the main thread. Disk access stays logged
     * (not fatal): Room/DataStore are legitimately reached from the main
     * dispatcher (e.g. viewModelScope), and penaltyDeath on disk made debug
     * builds FC on service creation and "Run now". Never active in release.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .penaltyDeathOnNetwork()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build()
        )
    }

    private companion object {
        const val TAG = "NexaFlowApp"
    }
}
