package com.nexaflow.app

import android.app.Application
import com.nexaflow.core.engine.AutomationScheduler
import com.nexaflow.core.engine.BatteryMonitor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NexaFlowApplication : Application() {

    @Inject
    lateinit var scheduler: AutomationScheduler

    @Inject
    lateinit var batteryMonitor: BatteryMonitor

    override fun onCreate() {
        super.onCreate()
        scheduler.initialize()
        batteryMonitor.initialize()
    }
}
