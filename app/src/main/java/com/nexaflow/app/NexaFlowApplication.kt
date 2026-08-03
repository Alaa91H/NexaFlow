package com.nexaflow.app

import android.app.Application
import com.nexaflow.core.engine.AutomationScheduler
import com.nexaflow.core.engine.MonitoringService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NexaFlowApplication : Application() {

    @Inject
    lateinit var scheduler: AutomationScheduler

    override fun onCreate() {
        super.onCreate()
        scheduler.initialize()
        try {
            MonitoringService.start(this)
        } catch (_: Throwable) {
            // Foreground service start can be restricted by the OS (e.g. from boot);
            // scheduling still works, and monitoring resumes on the next app launch.
        }
    }
}
