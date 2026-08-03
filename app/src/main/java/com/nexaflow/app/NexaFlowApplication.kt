package com.nexaflow.app

import android.app.Application
import com.nexaflow.core.engine.AutomationScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NexaFlowApplication : Application() {

    @Inject
    lateinit var scheduler: AutomationScheduler

    override fun onCreate() {
        super.onCreate()
        scheduler.initialize()
    }
}
