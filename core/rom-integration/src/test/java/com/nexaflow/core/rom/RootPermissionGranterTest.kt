package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.SystemControlResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RootPermissionGranter] — the root auto-grant flow.
 *
 * The real shell ([PrivilegedRunner.runShell]) and Android Context lookups are
 * replaced by the internal seams ([shellRunner], [permissionsProvider],
 * [grantedChecker], [batteryExemptChecker], [accessibilityChecker]) and the
 * pure pipeline [RootPermissionGranter.grantAllInternal] is exercised so the
 * whole flow is deterministic on any host without Android/Robolectric.
 */
class RootPermissionGranterTest {

    private val commands = mutableListOf<String>()

    @Before
    fun setUp() {
        commands.clear()
        SystemAppStatusDetector.refreshRootAvailability()
        // Every shell command succeeds by default.
        RootPermissionGranter.shellRunner = { cmd ->
            commands += cmd
            SystemControlResult.ok("ok")
        }
    }

    @After
    fun tearDown() {
        RootPermissionGranter.shellRunner = null
        RootPermissionGranter.packageNameProvider = null
        RootPermissionGranter.permissionsProvider = null
        RootPermissionGranter.grantedChecker = null
        RootPermissionGranter.appOpsProvider = null
        RootPermissionGranter.batteryExemptChecker = null
        RootPermissionGranter.accessibilityChecker = null
        SystemAppStatusDetector.pathResolution = null
        SystemAppStatusDetector.rootProbe = null
        SystemAppStatusDetector.refreshRootAvailability()
    }

    private fun grantRoot(): Boolean {
        SystemAppStatusDetector.pathResolution = { true }
        SystemAppStatusDetector.rootProbe = { true }
        return SystemAppStatusDetector.isRootAvailable()
    }

    private fun grantAll() = RootPermissionGranter.grantAllInternal(
        packageName = "com.nexaflow.app"
    )

    // ──────────────────────────────────────────────────────────────
    // Gate: no root / Shizuku → nothing is attempted
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `canAutoGrant false without root or Shizuku`() {
        SystemAppStatusDetector.pathResolution = { false }
        SystemAppStatusDetector.rootProbe = { false }
        assertFalse(RootPermissionGranter.canAutoGrant())
    }

    @Test
    fun `grantAllInternal runs no shell command without root`() {
        SystemAppStatusDetector.pathResolution = { false }
        SystemAppStatusDetector.rootProbe = { false }
        val result = grantAll()
        assertFalse(result.anyGranted)
        assertTrue(result.failures.isNotEmpty())
        assertTrue("no shell command may run without an elevated runtime", commands.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────
    // Runtime permissions — pm grant
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `grantAllInternal grants declared runtime permissions via pm grant`() {
        assertTrue(grantRoot())
        RootPermissionGranter.permissionsProvider = {
            listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.POST_NOTIFICATIONS"
            )
        }
        RootPermissionGranter.grantedChecker = { false } // nothing granted yet

        val result = grantAll()

        assertTrue(result.anyGranted)
        assertEquals(2, result.runtimeGranted.size)
        assertTrue(commands.any { it == "pm grant com.nexaflow.app android.permission.ACCESS_FINE_LOCATION" })
        assertTrue(commands.any { it == "pm grant com.nexaflow.app android.permission.POST_NOTIFICATIONS" })
    }

    @Test
    fun `grantAllInternal skips permissions already granted`() {
        assertTrue(grantRoot())
        RootPermissionGranter.permissionsProvider = {
            listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.POST_NOTIFICATIONS"
            )
        }
        RootPermissionGranter.grantedChecker = { it == "android.permission.ACCESS_FINE_LOCATION" }

        val result = grantAll()

        assertEquals(1, result.runtimeGranted.size)
        assertEquals("android.permission.POST_NOTIFICATIONS", result.runtimeGranted.first())
        assertFalse(commands.any { it.contains("ACCESS_FINE_LOCATION") })
    }

    @Test
    fun `grantAllInternal records pm grant failures without aborting`() {
        assertTrue(grantRoot())
        RootPermissionGranter.permissionsProvider = { listOf("android.permission.CAMERA") }
        RootPermissionGranter.grantedChecker = { false }
        RootPermissionGranter.shellRunner = { cmd ->
            commands += cmd
            SystemControlResult.fail("SecurityException")
        }

        val result = grantAll()

        assertTrue(result.runtimeGranted.isEmpty())
        assertTrue(result.failures.any { it.contains("pm grant") })
    }

    // ──────────────────────────────────────────────────────────────
    // Special app-ops + battery + accessibility
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `grantAllInternal issues appops set allow for special permissions`() {
        assertTrue(grantRoot())
        RootPermissionGranter.permissionsProvider = { emptyList() }
        RootPermissionGranter.grantedChecker = { false }
        RootPermissionGranter.appOpsProvider = {
            listOf(
                "android.permission.WRITE_SETTINGS" to "android:write_settings",
                "android.permission.SYSTEM_ALERT_WINDOW" to "android:system_alert_window"
            )
        }

        val result = grantAll()

        assertTrue(result.appOpsGranted.isNotEmpty())
        assertTrue(commands.any { it.startsWith("appops set com.nexaflow.app") && it.endsWith(" allow") })
        assertTrue(commands.any { it.contains("android:write_settings") })
        assertTrue(commands.any { it.contains("android:system_alert_window") })
    }

    @Test
    fun `grantAllInternal adds battery exemption when not exempt`() {
        assertTrue(grantRoot())
        RootPermissionGranter.permissionsProvider = { emptyList() }
        RootPermissionGranter.batteryExemptChecker = { false }

        val result = grantAll()

        assertTrue(result.batteryExempted)
        assertTrue(commands.any { it == "dumpsys deviceidle whitelist +com.nexaflow.app" })
    }

    @Test
    fun `grantAllInternal skips battery whitelist when already exempt`() {
        assertTrue(grantRoot())
        RootPermissionGranter.permissionsProvider = { emptyList() }
        RootPermissionGranter.batteryExemptChecker = { true }

        val result = grantAll()

        assertFalse(result.batteryExempted)
        assertFalse(commands.any { it.contains("deviceidle whitelist") })
    }

    @Test
    fun `grantAllInternal enables accessibility service via secure settings`() {
        assertTrue(grantRoot())
        RootPermissionGranter.permissionsProvider = { emptyList() }
        RootPermissionGranter.accessibilityChecker = { false }

        val result = grantAll()

        assertTrue(result.secureSettingsWritten.isNotEmpty())
        assertTrue(commands.any { it.startsWith("settings put secure enabled_accessibility_services") })
        assertTrue(commands.any { it == "settings put secure accessibility_enabled 1" })
    }

    @Test
    fun `grantAllInternal skips accessibility when already enabled`() {
        assertTrue(grantRoot())
        RootPermissionGranter.permissionsProvider = { emptyList() }
        RootPermissionGranter.accessibilityChecker = { true }

        val result = grantAll()

        assertTrue(result.secureSettingsWritten.isEmpty())
        assertFalse(commands.any { it.contains("enabled_accessibility_services") })
    }
}
