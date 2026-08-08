package com.nexaflow.core.rom

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Single source of truth for the two "is this service enabled" checks the app
 * repeats across modules: accessibility service and notification listener
 * access. Both are stored as colon-separated component strings in
 * [Settings.Secure]; centralising them here removes five duplicated
 * implementations and keeps the parsing consistent.
 *
 * The app's own service component names are declared once as constants and
 * exposed through the no-arg overloads, so callers never hardcode class-name
 * strings (a rename would otherwise silently flip every check to false).
 */
object PermissionStatus {

    /** AccessibilityService component name (must match the manifest entry). */
    const val ACCESSIBILITY_SERVICE_CLASS = "com.nexaflow.core.engine.AppTriggerAccessibilityService"

    /** NotificationListenerService component name (must match the manifest entry). */
    const val NOTIFICATION_LISTENER_CLASS = "com.nexaflow.core.engine.NotificationListener"

    /** True when our AccessibilityService is among the enabled services. */
    fun isAccessibilityServiceEnabled(context: Context): Boolean =
        isAccessibilityServiceEnabled(
            context,
            ComponentName(context, ACCESSIBILITY_SERVICE_CLASS)
        )

    /**
     * True when [component] (an AccessibilityService declared by this app) is
     * currently among the enabled accessibility services.
     */
    fun isAccessibilityServiceEnabled(context: Context, component: ComponentName): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return containsComponent(enabledServices, component.flattenToString())
    }

    /** True when our NotificationListenerService has listener access. */
    fun isNotificationListenerGranted(context: Context): Boolean =
        isNotificationListenerGranted(
            context,
            ComponentName(context, NOTIFICATION_LISTENER_CLASS)
        )

    /**
     * True when [component] (a NotificationListenerService declared by this
     * app) has notification listener access.
     */
    fun isNotificationListenerGranted(context: Context, component: ComponentName): Boolean {
        return if (Build.VERSION.SDK_INT >= 27) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.isNotificationListenerAccessGranted(component)
        } else {
            // API 26 has no direct API — parse the enabled listeners setting
            // (the raw key is stable since API 18).
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            containsComponent(enabled, component.flattenToString())
        }
    }

    /**
     * Pure string check (JVM-testable, no Android dependency): is the
     * flattened component ([expected], e.g. "pkg/com.Class") present in the
     * colon-separated list of enabled services as stored in Settings.Secure?
     */
    internal fun containsComponent(flat: String, expected: String): Boolean {
        return flat.split(':').any { it == expected }
    }

    /** Opens the system accessibility settings screen (fail-soft). */
    fun openAccessibilitySettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Opens this app's details page in system settings (fail-soft). */
    fun openAppDetails(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Opens the "Modify system settings" screen for this app (fail-soft). */
    fun openWriteSettings(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Opens the Do Not Disturb access screen (fail-soft). */
    fun openNotificationPolicy(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Opens the notification listener access screen (fail-soft). */
    fun openNotificationAccessSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
