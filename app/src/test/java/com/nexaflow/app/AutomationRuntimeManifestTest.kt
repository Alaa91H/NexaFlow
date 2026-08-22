package com.nexaflow.app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.engine.AutomationAlarmReceiver
import com.nexaflow.core.engine.MonitoringService
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runtime contract for automatic execution. A valid scheduler is ineffective
 * when its receiver, boot permission, exact-alarm access declaration, or
 * foreground monitoring service disappears from the merged app manifest.
 */
@RunWith(RobolectricTestRunner::class)
class AutomationRuntimeManifestTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun automaticExecutionComponents_areDeclaredAndEnabled() {
        val packageManager = context.packageManager
        val receiver = packageManager.getReceiverInfo(
            ComponentName(context, AutomationAlarmReceiver::class.java),
            0
        )
        val monitoring = packageManager.getServiceInfo(
            ComponentName(context, MonitoringService::class.java),
            0
        )

        assertTrue("Alarm receiver must be enabled", receiver.isEnabled)
        assertTrue("Monitoring service must be enabled", monitoring.isEnabled)
        assertTrue(
            "Monitoring service must declare foreground special use",
            monitoring.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0
        )
    }

    @Test
    fun automaticExecutionPermissions_areDeclared() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()

        assertTrue("Boot recovery permission must be declared", Manifest.permission.RECEIVE_BOOT_COMPLETED in permissions)
        assertTrue("Exact-alarm permission must be declared", Manifest.permission.SCHEDULE_EXACT_ALARM in permissions)
        assertTrue("Wake lock permission must be declared", Manifest.permission.WAKE_LOCK in permissions)
        assertTrue(
            "Monitoring service special-use permission must be declared",
            Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE in permissions
        )
    }
}
