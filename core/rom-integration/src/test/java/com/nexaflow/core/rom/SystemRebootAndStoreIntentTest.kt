package com.nexaflow.core.rom

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.core.rom.model.SystemControlResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The reboot action's schema has always declared a target (`NORMAL`,
 * `RECOVERY`, `BOOTLOADER`) and the Play Store action declares a required
 * package, but the runtime ignored both. These tests pin the corrected
 * contract: the declared value decides the elevated command and the launched
 * intent, and an unsupported value fails without any side effect.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemRebootAndStoreIntentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val commands = mutableListOf<String>()

    private fun controller(): SystemController = SystemController(
        context = context,
        capabilityProvider = RomCapabilityProvider(context, IntegrationLevel.NORMAL, RomFamily.AOSP)
    )

    private fun grantRootShell() {
        PrivilegedRunner.rootProbeOverride = { true }
        PrivilegedRunner.suRunnerProbe = { SystemControlResult.ok("ok") }
        PrivilegedRunner.shellRouteProbe = { command ->
            commands += command
            "root" to command
        }
    }

    @After
    fun tearDown() {
        PrivilegedRunner.shellRouteProbe = null
        PrivilegedRunner.rootProbeOverride = null
        PrivilegedRunner.suRunnerProbe = null
        PrivilegedRunner.shizukuGrantProbe = null
    }

    @Test
    fun rebootDefaultsToPlainReboot() {
        grantRootShell()

        val result = controller().rebootDevice()

        assertTrue(result.message, result.success)
        assertEquals(listOf("'reboot'"), commands)
        assertEquals("Reboot requested", result.message)
    }

    @Test
    fun recoveryRebootTargetsRecovery() {
        grantRootShell()

        val result = controller().rebootDevice(RebootMode.RECOVERY)

        assertTrue(result.message, result.success)
        assertEquals(listOf("'reboot' 'recovery'"), commands)
        assertEquals("Recovery reboot requested", result.message)
    }

    @Test
    fun bootloaderRebootTargetsBootloader() {
        grantRootShell()

        val result = controller().rebootDevice("bootloader")

        assertTrue(result.message, result.success)
        assertEquals(listOf("'reboot' 'bootloader'"), commands)
    }

    @Test
    fun unsupportedModeFailsWithoutRunningACommand() {
        grantRootShell()

        val result = controller().rebootDevice("FASTBOOT")

        assertFalse(result.success)
        assertTrue(result.message.contains("FASTBOOT"))
        assertEquals(emptyList<String>(), commands)
    }

    @Test
    fun shutdownRunsThePowerOffCommand() {
        grantRootShell()

        val result = controller().shutdownDevice()

        assertTrue(result.message, result.success)
        assertEquals(listOf("'reboot' '-p'"), commands)
        assertEquals("Shutdown requested", result.message)
    }

    @Test
    fun systemUiRestartKillsTheUiProcess() {
        grantRootShell()

        val result = controller().restartSystemUi()

        assertTrue(result.message, result.success)
        assertEquals(listOf("'pkill' '-f' 'com.android.systemui'"), commands)
        assertEquals("System UI restarted", result.message)
    }

    @Test
    fun systemUiRestartFallsBackToTheSoftRefresh() {
        // Killing the process is denied on this ROM; the broadcast is allowed.
        PrivilegedRunner.rootProbeOverride = { true }
        PrivilegedRunner.shellRouteProbe = { command ->
            commands += command
            "root" to command
        }
        PrivilegedRunner.suRunnerProbe = { cmd ->
            if (cmd.last().contains("CLOSE_SYSTEM_DIALOGS")) SystemControlResult.ok("ok")
            else SystemControlResult.fail("Permission denied")
        }

        val result = controller().restartSystemUi()

        assertTrue(result.message, result.success)
        assertEquals("System UI refresh requested", result.message)
        assertEquals(2, commands.size)
        assertTrue(commands.last().contains("CLOSE_SYSTEM_DIALOGS"))
    }

    @Test
    fun systemUiRestartReportsTheFailureWhenNoRouteWorks() {
        PrivilegedRunner.rootProbeOverride = { true }
        PrivilegedRunner.shellRouteProbe = { command ->
            commands += command
            "root" to command
        }
        PrivilegedRunner.suRunnerProbe = { SystemControlResult.fail("Permission denied") }

        val result = controller().restartSystemUi()

        assertFalse(result.success)
        assertTrue(result.message.contains("System UI restart requires root"))
    }

    @Test
    fun rebootModeMappingIsClosed() {
        assertEquals(emptyList<String>(), RebootMode.argumentsFor(null))
        assertEquals(emptyList<String>(), RebootMode.argumentsFor("  "))
        assertEquals(listOf("recovery"), RebootMode.argumentsFor(" recovery "))
        assertEquals(null, RebootMode.argumentsFor("SIDELOAD"))
        assertEquals(listOf("NORMAL", "RECOVERY", "BOOTLOADER"), RebootMode.selectable)
    }

    @Test
    @Suppress("DEPRECATION") // addResolveInfoForIntent is the documented Robolectric seam
    fun playStoreListingOpensTheChosenPackage() {
        val pm = context.packageManager
        val market = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.example.target"))
        shadowOf(pm).addResolveInfoForIntent(
            market,
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "com.android.vending"
                    name = "com.android.vending.AssetBrowserActivity"
                    applicationInfo = ApplicationInfo().apply {
                        packageName = "com.android.vending"
                    }
                }
            }
        )

        val result = controller().openPlayStoreApp("com.example.target")

        assertTrue(result.message, result.success)
        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals("market://details?id=com.example.target", started?.data.toString())
    }

    @Test
    fun playStoreListingFallsBackToTheWebListing() {
        // No package manager entry for the market scheme on this device.
        val result = controller().openPlayStoreApp("com.example.target")

        assertTrue(result.message, result.success)
        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.example.target",
            started?.data.toString()
        )
    }

    @Test
    fun playStoreRejectsANonPackageArgument() {
        val result = controller().openPlayStoreApp("com.example.app; id")

        assertFalse(result.success)
        assertTrue(result.message.contains("Invalid package name"))
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test
    fun playStoreWithoutAPackageKeepsTheStoreHomeBehaviour() {
        // Robolectric has no Play Store client installed, so the historical
        // home-surface path reports that honestly instead of launching nothing.
        val result = controller().openPlayStoreApp(null)

        assertFalse(result.success)
        assertTrue(result.message.contains("Play Store not installed"))
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test
    @Suppress("DEPRECATION") // addActivityIfNotPresent/addIntentFilterForActivity are Robolectric seams
    fun playStoreHomeOpensTheInstalledStoreClient() {
        val pm = context.packageManager
        val component = android.content.ComponentName(
            "com.android.vending",
            "com.android.vending.AssetBrowserActivity"
        )
        shadowOf(pm).addActivityIfNotPresent(component)
        shadowOf(pm).addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        )

        val result = controller().openPlayStoreApp()

        assertTrue(result.message, result.success)
        assertEquals("Play Store opened", result.message)
        assertNotNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test
    fun playStoreWebListingReportsALaunchFailureInsteadOfCrashing() {
        // No store client, and the browser launch is blocked: the fallback must
        // report the failure rather than crash the automation.
        val blocked = object : android.content.ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                throw IllegalStateException("background launch blocked")
            }
        }

        val result = AppStoreLauncher.open(blocked, "com.example.target")

        assertFalse(result.success)
        assertTrue(result.message.contains("Play Store listing failed"))
        assertTrue(result.message.contains("background launch blocked"))
    }

    @Test
    @Suppress("DEPRECATION") // addResolveInfoForIntent is the documented Robolectric seam
    fun playStoreListingReportsALaunchFailureInsteadOfCrashing() {
        val pm = context.packageManager
        shadowOf(pm).addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.example.target")),
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "com.android.vending"
                    name = "com.android.vending.AssetBrowserActivity"
                    applicationInfo = ApplicationInfo().apply {
                        packageName = "com.android.vending"
                    }
                }
            }
        )
        val blocked = object : android.content.ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                throw IllegalStateException("background launch blocked")
            }
        }

        val result = AppStoreLauncher.open(blocked, "com.example.target")

        assertFalse(result.success)
        assertTrue(result.message.contains("Play Store listing failed"))
        assertTrue(result.message.contains("background launch blocked"))
    }
}
