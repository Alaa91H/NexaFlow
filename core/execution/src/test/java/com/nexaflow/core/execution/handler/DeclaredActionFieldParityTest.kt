package com.nexaflow.core.execution.handler

import android.app.Application
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Declared action fields must decide what the handler does (T02-005).
 *
 * `APPLICATION_LAUNCH_APP` declares both the canonical `package` and the
 * legacy `packages` list, and `SYSTEM_OPEN_PLAY_STORE_APP` declares a required
 * `package`; the handler used to read neither the list nor the package. These
 * tests drive the real handlers against a Robolectric package manager and
 * assert the intent that the field produced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeclaredActionFieldParityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun controller(): SystemController = SystemController(
        context = context,
        capabilityProvider = RomCapabilityProvider(context, IntegrationLevel.NORMAL, RomFamily.AOSP)
    )

    private fun ctx() = ActionExecutionContext(
        appContext = context,
        controller = controller(),
        notificationSettings = NotificationSettings()
    )

    // No app is installed on this Robolectric device, so a launched package is
    // reported as missing and the message names the package the handler chose
    // from the configuration. That is exactly the field-resolution contract
    // under test, without depending on package-manager fixture plumbing.
    @Test
    fun launchAppFallsBackToTheLegacyPackagesList() = runBlocking {
        val result = AppActionsHandler().execute(
            Action(
                type = ActionType.APPLICATION_LAUNCH_APP,
                config = mapOf("packages" to "com.example.legacy, com.example.other")
            ),
            ctx()
        )

        assertTrue(result.message, result.message.contains("No launch intent for com.example.legacy"))
    }

    @Test
    fun launchAppPrefersTheCanonicalPackageOverTheLegacyList() = runBlocking {
        val result = AppActionsHandler().execute(
            Action(
                type = ActionType.APPLICATION_LAUNCH_APP,
                config = mapOf(
                    "package" to "com.example.canonical",
                    "packages" to "com.example.legacy"
                )
            ),
            ctx()
        )

        assertTrue(result.message, result.message.contains("No launch intent for com.example.canonical"))
    }

    @Test
    fun playStoreActionOpensTheConfiguredListing() = runBlocking {
        // No Play Store client is registered here, so execution takes the web
        // listing fallback; the URI still proves which package was passed on.
        val result = SystemActionsHandler().execute(
            Action(
                type = ActionType.SYSTEM_OPEN_PLAY_STORE_APP,
                config = mapOf("package" to "com.example.target")
            ),
            ctx()
        )

        assertTrue(result.message, result.success)
        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.example.target",
            started?.data.toString()
        )
    }

    @Test
    fun rebootActionPassesTheDeclaredModeToThePowerController() = runBlocking {
        // This device has no elevated runtime, so the declared target cannot be
        // executed; the handler still has to route the request through the
        // mode-aware reboot path and report the missing privilege instead of
        // claiming a plain reboot. The command mapping for each mode is pinned
        // in `SystemRebootAndStoreIntentTest`.
        val result = SystemActionsHandler().execute(
            Action(
                type = ActionType.SYSTEM_REBOOT,
                config = mapOf("mode" to "RECOVERY")
            ),
            ctx()
        )

        assertFalse(result.success)
        assertTrue(result.message.contains("elevated runtime"))
    }
}
