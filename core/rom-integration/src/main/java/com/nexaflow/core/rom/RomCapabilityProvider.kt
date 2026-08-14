package com.nexaflow.core.rom

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.RomFamily

class RomCapabilityProvider(
    private val context: Context,
    private val integrationLevel: IntegrationLevel,
    private val romFamily: RomFamily
) {
    fun availableCapabilities(): List<RomCapability> {
        return RomCapability.values().filter { isAvailable(it) }
    }

    fun isAvailable(capability: RomCapability): Boolean {
        return when (capability) {
            RomCapability.WRITE_SETTINGS -> Settings.System.canWrite(context)
            RomCapability.WRITE_SECURE_SETTINGS ->
                hasPermission("android.permission.WRITE_SECURE_SETTINGS")
            RomCapability.SYSTEM_ALERT_WINDOW -> Settings.canDrawOverlays(context)
            RomCapability.PACKAGE_USAGE_STATS ->
                hasPermission("android.permission.PACKAGE_USAGE_STATS")
            RomCapability.READ_LOGS -> hasPermission("android.permission.READ_LOGS")
            RomCapability.MODIFY_PHONE_STATE ->
                hasPermission("android.permission.MODIFY_PHONE_STATE")
            RomCapability.STATUS_BAR_CONTROL ->
                hasPermission("android.permission.STATUS_BAR") ||
                    hasPermission("android.permission.EXPAND_STATUS_BAR")
            RomCapability.FORCE_STOP_PACKAGES ->
                hasPermission("android.permission.FORCE_STOP_PACKAGES")
            RomCapability.KILL_BACKGROUND_PROCESSES ->
                hasPermission("android.permission.KILL_BACKGROUND_PROCESSES")
            RomCapability.DND_ACCESS -> {
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.isNotificationPolicyAccessGranted
            }
            // Evolution X is a fork of LineageOS and ships its full privileged
            // SDK + vendor hardware HALs, so the LineageOS-derived capabilities
            // apply to both families whenever the app runs elevated.
            RomCapability.LINEAGEOS_SDK ->
                isLineageDerived() && isElevated()
            RomCapability.LINEAGEOS_HARDWARE ->
                isLineageDerived() && isElevated()
            RomCapability.EVOLUTION_X_SETTINGS ->
                romFamily == RomFamily.EVOLUTION_X && isElevated()
            RomCapability.MIUI_HIDDEN_API ->
                (romFamily == RomFamily.MIUI || romFamily == RomFamily.HYPER_OS) && isElevated()
            RomCapability.COLOROS_HIDDEN_API ->
                (romFamily == RomFamily.COLOR_OS ||
                    romFamily == RomFamily.OXYGEN_OS ||
                    romFamily == RomFamily.REALME_UI) && isElevated()
            RomCapability.ONE_UI_HIDDEN_API ->
                romFamily == RomFamily.ONE_UI && isElevated()
            RomCapability.OEM_HIDDEN_API ->
                isGenericOemFamily(romFamily) && isElevated()
            RomCapability.ROOT_SHELL -> integrationLevel == IntegrationLevel.ROOT
            RomCapability.SHIZUKU -> integrationLevel == IntegrationLevel.SHIZUKU
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isElevated(): Boolean {
        return integrationLevel == IntegrationLevel.PLATFORM_SIGNED_SYSTEM_APP ||
            integrationLevel == IntegrationLevel.PRIVILEGED_SYSTEM_APP ||
            integrationLevel == IntegrationLevel.SYSTEM_APP ||
            integrationLevel == IntegrationLevel.ROOT ||
            integrationLevel == IntegrationLevel.SHIZUKU
    }

    /** LineageOS and its forks (Evolution X, crDroid, ...) share the same SDK/HALs. */
    private fun isLineageDerived(): Boolean {
        return RomSettingSchema.isLineageDerived(romFamily)
    }

    /** OEM skins without a dedicated capability enum entry (OriginOS, EMUI, ZenUI, ...). */
    private fun isGenericOemFamily(romFamily: RomFamily): Boolean {
        return romFamily == RomFamily.VIVO_ORIGIN_OS ||
            romFamily == RomFamily.EMUI ||
            romFamily == RomFamily.HARMONY_OS ||
            romFamily == RomFamily.ASUS_ZEN_UI ||
            romFamily == RomFamily.NOTHING_OS ||
            romFamily == RomFamily.MOTOROLA ||
            romFamily == RomFamily.SONY_XPERIA
    }
}
