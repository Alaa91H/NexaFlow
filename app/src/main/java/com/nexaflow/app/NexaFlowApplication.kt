package com.nexaflow.app

import android.app.Application
import android.os.StrictMode
import com.nexaflow.app.work.MaintenanceWorker
import com.nexaflow.core.engine.AutomationScheduler
import com.nexaflow.core.engine.MonitoringService
import com.nexaflow.core.rom.ShizukuShellBridge
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NexaFlowApplication : Application() {

    @Inject
    lateinit var scheduler: AutomationScheduler

    @Inject
    lateinit var sentryReporter: SentryReporter

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }
        // Arm the Shizuku UserService (AIDL) channel early so elevated commands
        // already use it — Shizuku.newProcess is removed in Shizuku API 14.
        ShizukuShellBridge.initialize(this)
        // Opt-in crash/ANR reporting: reacts to the Settings > Privacy toggle.
        sentryReporter.attach()
        // Periodic history pruning through WorkManager (Doze-aware, survives
        // service death). KEEP so an update never spawns duplicate jobs.
        MaintenanceWorker.schedule(this)
        scheduler.initialize()
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
     * Debug-only watchdog: crashes (via the uncaught handler) on main-thread
     * disk/network I/O, unbounded receivers, leaked activities, and other
     * policy violations — the same class of bug as the in-app update checker
     * downloading APKs on the main thread. Never active in release builds.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyDeath()
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
}
