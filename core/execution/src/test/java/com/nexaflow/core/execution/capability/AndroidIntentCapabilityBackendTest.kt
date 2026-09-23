package com.nexaflow.core.execution.capability

import android.provider.Settings
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilityStatus
import com.nexaflow.domain.capability.VerificationMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidIntentCapabilityBackendTest {

    @Test
    fun successfulSettingsHandoffIsTerminalCapabilitySuccess() = runTest {
        val application = RuntimeEnvironment.getApplication()
        val backend = AndroidIntentCapabilityBackend(application)

        val result = backend.execute(
            CapabilityRequest(
                capability = CapabilityId.SETTINGS_LAUNCH,
                parameters = mapOf("page" to "WIFI"),
                verification = VerificationMode.NONE
            )
        )

        assertEquals(CapabilityStatus.SUCCESS, result.status)
        assertEquals(
            Settings.ACTION_WIFI_SETTINGS,
            shadowOf(application).nextStartedActivity.action
        )
    }
}
