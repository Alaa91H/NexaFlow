package com.nexaflow.app

import android.app.Application
import com.nexaflow.core.engine.AutomationScheduler
import com.nexaflow.core.engine.MonitoringService
import com.nexaflow.core.rom.ShizukuShellBridge
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NexaFlowApplication : Application() {

    @Inject
    lateinit var scheduler: AutomationScheduler

    override fun onCreate() {
        super.onCreate()
        // Arm the Shizuku UserService (AIDL) channel early so elevated commands
        // already use it — Shizuku.newProcess is removed in Shizuku API 14.
        ShizukuShellBridge.initialize(this)
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
}
