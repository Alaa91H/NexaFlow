package com.nexaflow.core.execution

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceStateSnapshotOwnershipTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun restorePreservesFontScaleChangedByTheUserAfterAutomation() {
        val resolver = context.contentResolver
        val priorValue = Settings.System.getFloat(resolver, "font_scale", 1f)
        val action = Action(ActionType.SYSTEM_FONT_SCALE, mapOf("scale" to "1.15"))

        try {
            Settings.System.putFloat(resolver, "font_scale", 1.15f)
            val original = DeviceStateSnapshot.capture(context)
            val afterAutomation = DeviceStateSnapshot.capture(context)
            Settings.System.putFloat(resolver, "font_scale", 1.25f)

            val result = original.restoreIfUnchanged(context, listOf(action to afterAutomation))

            assertTrue(result.success)
            assertTrue(result.message.contains("preserved 1 value"))
            assertEquals(1.25f, Settings.System.getFloat(resolver, "font_scale", 1f), 0.001f)
        } finally {
            Settings.System.putFloat(resolver, "font_scale", priorValue)
        }
    }
}
