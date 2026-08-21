package com.nexaflow.core.execution.handler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.rom.RomCapabilityProvider
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PluginFireHandlerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val handler = PluginFireHandler()

    @Test
    fun highRiskPluginWithoutExplicitApproval_isRejectedBeforeItCanFire() = runBlocking {
        val result = handler.execute(
            action = Action(
                type = ActionType.PLUGIN_FIRE,
                config = mapOf(
                    "pluginApproval" to "approved",
                    "package" to "com.ADBPlugin",
                    "receiver" to "com.adbplugin.FireReceiver"
                )
            ),
            ctx = context()
        )

        assertFalse(result.success)
        assertTrue(result.message.contains("High-risk plugin action"))
    }

    @Test
    fun pluginWithoutGeneralApproval_isRejectedBeforeConfigurationIsRead() = runBlocking {
        val result = handler.execute(
            action = Action(
                type = ActionType.PLUGIN_FIRE,
                config = mapOf(
                    "package" to "com.example.plugin",
                    "receiver" to "com.example.plugin.FireReceiver"
                )
            ),
            ctx = context()
        )

        assertFalse(result.success)
        assertTrue(result.message.contains("has not been approved"))
    }

    private fun context() = ActionExecutionContext(
        appContext = context,
        controller = SystemController(
            context,
            RomCapabilityProvider(context, IntegrationLevel.NORMAL, RomFamily.AOSP)
        ),
        notificationSettings = NotificationSettings(enabled = true, executionEnabled = true),
        automationId = "plugin-handler-test",
        revertOnExit = false
    )
}
