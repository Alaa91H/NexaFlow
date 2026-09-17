package com.nexaflow.app

import android.content.pm.PermissionInfo
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
            assertEquals(name, PermissionInfo.PROTECTION_SIGNATURE, info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
        }
    }
}
