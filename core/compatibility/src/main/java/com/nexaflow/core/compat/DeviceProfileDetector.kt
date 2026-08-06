package com.nexaflow.core.compat

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.RomIntegrationManager
import com.nexaflow.core.rom.SystemAppStatusDetector

/**
 * Builds a real [DeviceProfile] from the device: ROM family, integration level,
 * available capabilities, accessibility/shizuku/root/adb state.
 */
object DeviceProfileDetector {

    fun detect(context: Context): DeviceProfile {
        val appContext = context.applicationContext
        return DeviceProfile(
            integrationLevel = RomIntegrationManager.integrationLevel(appContext),
            romFamily = RomIntegrationManager.buildInfo(appContext).family,
            capabilities = RomIntegrationManager.availableCapabilities(appContext).toSet(),
            accessibilityEnabled = isAccessibilityEnabled(appContext),
            shizukuGranted = PrivilegedRunner.isShizukuGranted(),
            rootAvailable = SystemAppStatusDetector.isRootAvailable(),
            adbConnected = false, // wireless-debugging pairing detection arrives with the ADB provider
            androidSdk = Build.VERSION.SDK_INT
        )
    }

    /** True when our AccessibilityService is among the enabled services. */
    private fun isAccessibilityEnabled(context: Context): Boolean {
        return runCatching {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_accessibility_services"
            ) ?: return false
            enabled.split(':').any { it == context.packageName }
        }.getOrDefault(false)
    }
}
