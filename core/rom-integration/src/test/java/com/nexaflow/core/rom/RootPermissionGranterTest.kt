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
        RootPermissionGranter.notificationListenerChecker = null
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

    @Test
    fun `runtime permission path requests root approval when su exists but app is not approved`() {
        assertEquals(
            RootPermissionGranter.RuntimePermissionGrantPath.REQUEST_ROOT_ACCESS,
            RootPermissionGranter.runtimePermissionGrantPath(
                elevatedShellAvailable = false,
                suBinaryAvailable = true
            )
        )
        assertEquals(
            RootPermissionGranter.RuntimePermissionGrantPath.ELEVATED_SHELL,
            RootPermissionGranter.runtimePermissionGrantPath(
                elevatedShellAvailable = true,
                suBinaryAvailable = true
            )
        )
        assertEquals(
            RootPermissionGranter.RuntimePermissionGrantPath.ANDROID_RUNTIME_FALLBACK,
            RootPermissionGranter.runtimePermissionGrantPath(
                elevatedShellAvailable = false,
                suBinaryAvailable = false
            )
        )
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
    fun `targeted runtime grant verifies phone state after root grant`() {
        assertTrue(grantRoot())
        var phoneStateGranted = false
        RootPermissionGranter.shellRunner = { command ->
            commands += command
            if (command == "pm grant com.nexaflow.app android.permission.READ_PHONE_STATE") {
                phoneStateGranted = true
            }
            SystemControlResult.ok("ok")
        }

        val result = RootPermissionGranter.grantRuntimePermissionsInternal(
            packageName = "com.nexaflow.app",
            permissions = listOf("android.permission.READ_PHONE_STATE"),
            grantedChecker = { phoneStateGranted }
        )

        assertTrue(result.allGranted)
        assertEquals(listOf("android.permission.READ_PHONE_STATE"), result.granted)
        assertTrue(commands.contains("pm grant com.nexaflow.app android.permission.READ_PHONE_STATE"))
    }

    @Test
    fun `targeted runtime grant keeps permission missing when root command does not land`() {
        assertTrue(grantRoot())
        RootPermissionGranter.shellRunner = { command ->
            commands += command
            SystemControlResult.ok("reported success without changing permission")
        }

        val result = RootPermissionGranter.grantRuntimePermissionsInternal(
            packageName = "com.nexaflow.app",
            permissions = listOf("android.permission.READ_PHONE_STATE"),
            grantedChecker = { false }
        )

        assertFalse(result.allGranted)
        assertEquals(listOf("android.permission.READ_PHONE_STATE"), result.remaining)
        assertTrue(result.failures.isEmpty())
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
        RootPermissionGranter.notificationListenerChecker = { true }

        val result = grantAll()

        assertTrue(result.secureSettingsWritten.isEmpty())
        assertFalse(commands.any { it.contains("enabled_accessibility_services") })
    }

    // ──────────────────────────────────────────────────────────────
    // Notification listener access
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `grantAllInternal enables notification listener via secure settings`() {
        assertTrue(grantRoot())
        RootPermissionGranter.permissionsProvider = { emptyList() }
        RootPermissionGranter.notificationListenerChecker = { false }
        RootPermissionGranter.shellRunner = { cmd ->
            commands += cmd
            if (cmd.startsWith("settings get secure enabled_notification_listeners")) {
                SystemControlResult.ok("com.other.app/com.other.Service")
            } else {
                SystemControlResult.ok("ok")
            }
        }

        val result = grantAll()

        assertTrue(result.notificationListenerGranted)
        assertTrue(result.secureSettingsWritten.contains("enabled_notification_listeners"))
        assertTrue(
            commands.any {
                it.contains("settings put secure enabled_notification_listeners") &&
                    it.contains("com.other.app/com.other.Service") &&
                    it.contains("com.nexaflow.app/com.nexaflow.core.engine.NotificationListener")
            }
        )
    }

    @Test
    fun `grantAllInternal skips notification listener when already granted`() {
        assertTrue(grantRoot())
        RootPermissionGranter.permissionsProvider = { emptyList() }
        RootPermissionGranter.notificationListenerChecker = { true }

        val result = grantAll()

        assertFalse(result.notificationListenerGranted)
        assertFalse(commands.any { it.contains("enabled_notification_listeners") })
    }

    // ──────────────────────────────────────────────────────────────
    // Verification pass — remaining
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `grantAllInternal reports still-missing capabilities after failed grants`() {
        assertTrue(grantRoot())
        RootPermissionGranter.permissionsProvider = { listOf("android.permission.CAMERA") }
        // The checker never flips to granted → the verification pass must
        // report the permission as still missing even though the shell command
        // itself reported success (real devices re-read platform state).
        RootPermissionGranter.grantedChecker = { false }
        RootPermissionGranter.batteryExemptChecker = { false }
        RootPermissionGranter.accessibilityChecker = { false }
        RootPermissionGranter.notificationListenerChecker = { false }
        RootPermissionGranter.shellRunner = { cmd ->
            commands += cmd
            SystemControlResult.ok("ok")
        }

        val result = grantAll()

        assertFalse(result.allGranted)
        assertTrue(result.remaining.contains("permission:android.permission.CAMERA"))
        assertTrue(result.remaining.contains("battery_optimization"))
        assertTrue(result.remaining.contains("accessibility_service"))
        assertTrue(result.remaining.contains("notification_listener"))
    }

    @Test
    fun `grantAllInternal reports empty remaining when everything is granted`() {
        assertTrue(grantRoot())
        RootPermissionGranter.permissionsProvider = { listOf("android.permission.CAMERA") }
        RootPermissionGranter.grantedChecker = { true }
        RootPermissionGranter.batteryExemptChecker = { true }
        RootPermissionGranter.accessibilityChecker = { true }
        RootPermissionGranter.notificationListenerChecker = { true }
        RootPermissionGranter.appOpsProvider = { emptyList() }

        val result = grantAll()

        assertTrue(result.remaining.isEmpty())
        assertTrue(result.allGranted)
        assertFalse(result.anyGranted) // nothing had to be granted
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `requestAndGrantAll grants directly when root already available`() {
        assertTrue(grantRoot())
        RootPermissionGranter.permissionsProvider = { emptyList() }
        RootPermissionGranter.appOpsProvider = { emptyList() }
        RootPermissionGranter.batteryExemptChecker = { true }
        RootPermissionGranter.accessibilityChecker = { true }
        RootPermissionGranter.notificationListenerChecker = { true }

        val result = RootPermissionGranter.requestAndGrantAllInternal {
            "com.nexaflow.app"
        }

        assertTrue(result.remaining.isEmpty())
        // No su prompt should be triggered — root is already granted.
        assertFalse(commands.any { it.contains("id") })
    }
}
