package com.nexaflow.core.rom

import android.content.Context
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVibrator

/**
 * `minSdk` is 26, so [SystemController.vibrate] always issues a
 * [VibrationEffect]; the pre-Oreo amplitude-free overload is gone. The
 * duration is still clamped to 1..60 seconds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemControllerVibrateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun vibrator(): ShadowVibrator =
        shadowOf(
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        )

    private fun controller(): SystemController = SystemController(
        context = context,
        capabilityProvider = RomCapabilityProvider(context, IntegrationLevel.NORMAL, RomFamily.AOSP),
    )

    @Test
    fun vibrateUsesOneShotEffectAndClampsShortDurations() {
        val result = controller().vibrate(0)

        assertTrue(result.success)
        assertTrue(vibrator().isVibrating)
        assertEquals(1_000L, vibrator().milliseconds)
    }

    @Test
    fun vibrateCapsLongDurations() {
        assertTrue(controller().vibrate(600).success)
        assertEquals(60_000L, vibrator().milliseconds)
    }
}
