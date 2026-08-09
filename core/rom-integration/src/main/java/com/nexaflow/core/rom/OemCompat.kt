package com.nexaflow.core.rom

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.nexaflow.core.rom.model.RomFamily

/**
 * OEM compatibility helpers.
 *
 * Many vendor ROMs (Xiaomi MIUI/HyperOS, Samsung One UI, OPPO ColorOS,
 * OnePlus OxygenOS, Vivo OriginOS, Huawei EMUI...) aggressively kill
 * background apps unless the user enables autostart / disables app
 * sleeping. This object detects the current ROM family (via [RomDetector])
 * and produces the vendor-specific deep link to the right settings screen.
 *
 * The deep links are best-effort: [autostartDeepLink] only returns an intent
 * whose target component actually resolves on the device, so calling it is
 * always safe.
 */
object OemCompat {

    private const val PREFS = "nexaflow_oem"
    // Reuses the legacy notifier key so installs that already saw the one-time
    // notification keep their acknowledged state (no re-alert after upgrade).
    private const val KEY_AUTOSTART_HINT_DELIVERED = "autostart_hint_shown"

    /**
     * True once the OEM autostart guidance was delivered through either
     * channel: the in-app Permission Manager card or the engine notification.
     * A single shared flag guarantees the user is never alerted twice.
     */
    fun isHintDelivered(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTOSTART_HINT_DELIVERED, false)

    /**
     * Marks the guidance as delivered so the other channel stays silent:
     * the notification suppresses the card, and dismissing / acting on the
     * card suppresses the notification.
     */
    fun markHintDelivered(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTOSTART_HINT_DELIVERED, true)
            .apply()
    }

    /** True when the running ROM is one of the vendor families that gate
     *  background execution behind an autostart / app-sleeping switch. */
    fun hasVendorAutostartGate(): Boolean {
        return when (RomDetector.detect().family) {
            RomFamily.MIUI,
            RomFamily.HYPER_OS,
            RomFamily.ONE_UI,
            RomFamily.COLOR_OS,
            RomFamily.OXYGEN_OS -> true
            else -> false
        }
    }

    /**
     * Deep link to the vendor autostart / battery-saver screen, or null when
     * the device runs a clean ROM (Pixel/AOSP/custom) — where the standard
     * battery-optimization screen is sufficient — or when the vendor app
     * isn't installed. The returned intent, when present, resolves.
     */
    fun autostartDeepLink(context: Context): Intent? {
        val raw = when (RomDetector.detect().family) {
            RomFamily.MIUI, RomFamily.HYPER_OS -> Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            )
            RomFamily.COLOR_OS -> Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            )
            RomFamily.OXYGEN_OS -> Intent().setComponent(
                ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
            )
            RomFamily.ONE_UI -> Intent().setComponent(
                ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            )
            else -> null
        } ?: return null
        return raw.resolveActivity(context.packageManager)?.let { raw }
    }
}
