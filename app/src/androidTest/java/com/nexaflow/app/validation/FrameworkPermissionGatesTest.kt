package com.nexaflow.app

import android.content.pm.PermissionInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class FrameworkPermissionGatesTest {
    @Test fun exportedComponentAllowlistUsesPlatformSignatureGates() {
        val pm = ApplicationProvider.getApplicationContext<android.content.Context>().packageManager
        for (name in listOf("BROADCAST_SMS", "BIND_SCREENING_SERVICE", "BIND_ACCESSIBILITY_SERVICE",
            "BIND_NOTIFICATION_LISTENER_SERVICE", "INTERACT_ACROSS_USERS_FULL", "BIND_JOB_SERVICE",
            "DUMP", "BIND_QUICK_SETTINGS_TILE")) {
            val info = pm.getPermissionInfo("android.permission.$name", 0)
            assertEquals(name, PermissionInfo.PROTECTION_SIGNATURE, info.baseProtection())
        }
    }

    /**
     * `PermissionInfo#getProtection()` only exists from API 28, while the app
     * ships down to API 26; the deprecated field carries the same base value
     * there, so the allowlist stays verified on the oldest supported device.
     */
    private fun PermissionInfo.baseProtection(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            protection
        } else {
            @Suppress("DEPRECATION")
            protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        }
}
