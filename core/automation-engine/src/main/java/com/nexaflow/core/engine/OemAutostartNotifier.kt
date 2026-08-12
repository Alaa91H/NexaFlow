package com.nexaflow.core.engine

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nexaflow.core.rom.OemCompat

/**
 * One-time engine-level alert shown when background monitoring starts on an
 * OEM ROM that gates background execution behind an autostart / app-sleeping
 * switch (MIUI/HyperOS, One UI, ColorOS, OxygenOS).
 *
 * On those ROMs the OEM aggressively kills background apps after a reboot
 * unless the user enables autostart — which silently stops the monitoring
 * service and the automations. The service cannot read the vendor's autostart
 * switch directly, so the presence of the vendor gate is the closest signal:
 * whenever monitoring starts (app launch, boot restore, restart alarm), a
 * single notification nudges the user to the vendor's auto-start screen.
 *
 * The hint is delivered at most once per install through a single shared
 * flag ([OemCompat.isHintDelivered]) that is also consulted by the in-app
 * Permission Manager OemCompat card: whichever channel claims the hint first
 * keeps the other silent, so the user is never alerted twice. The flag is set
 * before the notification is posted, and posting only happens when
 * notifications are actually enabled, so it never spams. Posting is
 * best-effort — a failure must never crash the monitoring service.
 */
object OemAutostartNotifier {

    private const val CHANNEL_ID = "nexaflow_autostart"
    private const val NOTIFICATION_ID = 3001
    private const val REQUEST_CODE = 43001

    /** Called from the monitoring service when background monitoring starts. */
    fun maybeShow(context: Context) {
        val appContext = context.applicationContext
        maybeShow(
            appContext,
            hasAutostartGate = OemCompat.hasVendorAutostartGate(),
            autostartDeepLink = OemCompat.autostartDeepLink(appContext)
        )
    }

    /**
     * Testable core: the vendor probes are passed in so unit tests can pin the
     * gated/clean-ROM decisions without touching real system properties.
     * [android.Manifest.permission.POST_NOTIFICATIONS] is checked just before
     * posting via [NotificationManagerCompat.areNotificationsEnabled] (returns
     * false when the permission is missing or the channel is blocked), so the
     * lint guard below documents the deliberate, already-guarded call.
     */
    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled() above
    internal fun maybeShow(
        context: Context,
        hasAutostartGate: Boolean,
        autostartDeepLink: Intent?
    ) {
        try {
            // Only relevant on vendor ROMs that gate background execution.
            if (!hasAutostartGate) return
            // Best-effort deep link: skip when the vendor screen isn't installed.
            if (autostartDeepLink == null) return
            // Never spam: one hint per install, shared with the in-app card.
            // The card claims it by dismissing/acting on it (markHintDelivered),
            // so a notification already delivered means the card stays hidden.
            if (OemCompat.isHintDelivered(context)) return
            // Silent when the user disabled notifications / denied the runtime
            // permission — a notification they can't see shouldn't consume the
            // one-time hint.
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            // Mark BEFORE posting so a failed post still counts as delivered.
            OemCompat.markHintDelivered(context)

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.oem_autostart_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )

            // Tapping the notification opens the vendor autostart screen.
            val openIntent = autostartDeepLink.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            val contentIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow)
                // M3: brand-tinted small icon + action icons.
                .setColor(context.getColor(com.nexaflow.core.rom.R.color.notification_brand_color))
                .setContentTitle(context.getString(R.string.oem_autostart_title))
                .setContentText(context.getString(R.string.oem_autostart_text))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.oem_autostart_text))
                )
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
            // Best-effort: never crash monitoring over a hint.
        }
    }
}
