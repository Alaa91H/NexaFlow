package com.nexaflow.core.capability

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.nexaflow.core.rom.model.RomCapability

object CapabilityGrantHelper {

    /**
     * Returns an Intent that deep-links the user to the system screen where
     * the given capability can be granted, or null if the capability can only
     * be obtained through a privileged/ROM install.
     */
    fun grantIntent(context: Context, capability: RomCapability): Intent? {
        val packageName = context.packageName
        return when (capability) {
            RomCapability.WRITE_SETTINGS ->
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            RomCapability.SYSTEM_ALERT_WINDOW ->
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            RomCapability.PACKAGE_USAGE_STATS ->
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            RomCapability.DND_ACCESS ->
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            RomCapability.SHIZUKU ->
                context.packageManager.getLaunchIntentForPackage("moe.shizuku.manager")
            else -> null
        }
    }

    fun launch(context: Context, intent: Intent) {
        val wrapped = if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else intent
        runCatching { context.startActivity(wrapped) }
    }
}
