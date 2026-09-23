package com.nexaflow.core.execution

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActionStateReaderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun screenRotationReportsLiveValueAndTargetMatch() {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            1
        )

        val state = ActionStateReader.read(
            context,
            Action(ActionType.SYSTEM_SCREEN_ROTATION, mapOf("autoRotate" to "true"))
        )

        assertEquals(ActionStateValueKind.BOOLEAN, state?.kind)
        assertEquals("true", state?.rawValue)
        assertTrue(state?.matchesTarget == true)
    }

    @Test
    fun screenRotationReportsMismatchWithoutChangingDeviceState() {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0
        )

        val state = ActionStateReader.read(
            context,
            Action(ActionType.SYSTEM_SCREEN_ROTATION, mapOf("autoRotate" to "true"))
        )

        assertEquals("false", state?.rawValue)
        assertFalse(state?.matchesTarget ?: true)
        assertEquals(
            0,
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION
            )
        )
    }

    @Test
    fun brightnessTargetUsesSameClampingAsExecution() {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            255
        )

        val state = ActionStateReader.read(
            context,
            Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("value" to "999"))
        )

        assertEquals(ActionStateValueKind.NUMBER, state?.kind)
        assertEquals("255", state?.rawValue)
        assertTrue(state?.matchesTarget == true)
    }

    @Test
    fun unsupportedMomentaryActionHasNoCurrentState() {
        val state = ActionStateReader.read(
            context,
            Action(ActionType.SYSTEM_SEND_NOTIFICATION, emptyMap())
        )

        assertNull(state)
    }
}
