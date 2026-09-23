package com.nexaflow.wear

import android.app.Application
import com.nexaflow.wear.data.WearCapabilityPublisher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Hilt application class for the Wear OS companion app.
 */
@HiltAndroidApp
class WearApplication : Application() {

    @Inject
    lateinit var capabilityPublisher: WearCapabilityPublisher

    override fun onCreate() {
        super.onCreate()
        capabilityPublisher.start()
    }
}
