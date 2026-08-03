package com.nexaflow.app

import android.app.Application
import com.nexaflow.core.engine.AutomationScheduler
import com.nexaflow.core.engine.BatteryMonitor
import com.nexaflow.core.engine.ConnectivityMonitor
import com.nexaflow.core.engine.DeviceEventMonitor
import com.nexaflow.core.engine.LocationMonitor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NexaFlowApplication : Application() {

    @Inject
    lateinit var scheduler: AutomationScheduler

    @Inject
    lateinit var batteryMonitor: BatteryMonitor

    @Inject
    lateinit var deviceEventMonitor: DeviceEventMonitor

    @Inject
    lateinit var connectivityMonitor: ConnectivityMonitor

    @Inject
    lateinit var locationMonitor: LocationMonitor

    override fun onCreate() {
        super.onCreate()
        scheduler.initialize()
        batteryMonitor.initialize()
        deviceEventMonitor.initialize()
        connectivityMonitor.initialize()
        locationMonitor.initialize()
    }
}
