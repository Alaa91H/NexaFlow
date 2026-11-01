package com.nexaflow.core.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [DeviceOemProfiler.snapshot] reads the power state unconditionally now that
 * `minSdk` is 26 — the pre-API-23 "assume not exempt/never idle" fallbacks were
 * dead. The snapshot must stay a best-effort read: every field resolves on a
 * plain framework device without the app holding a power permission.
 */
@RunWith(RobolectricTestRunner::class)
class DeviceOemProfilerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun snapshotReadsEveryPowerFieldOnANonIdleDevice() {
        val profile = DeviceOemProfiler(context).snapshot()

        assertFalse(profile.isInDoze)
        assertFalse(profile.isIgnoringBatteryOptimizations)
        assertNotNull(profile.thermalState)
    }
}
