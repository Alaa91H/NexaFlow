package com.nexaflow.core.engine

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.datastore.SmsPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MonitoringService : Service() {

    @Inject
    lateinit var batteryMonitor: BatteryMonitor

    @Inject
    lateinit var deviceEventMonitor: DeviceEventMonitor

    @Inject
    lateinit var connectivityMonitor: ConnectivityMonitor

    @Inject
    lateinit var locationMonitor: LocationMonitor

    @Inject
    lateinit var bluetoothMonitor: BluetoothMonitor

    @Inject
    lateinit var ringerModeMonitor: RingerModeMonitor

    @Inject
    lateinit var calendarMonitor: CalendarMonitor

    @Inject
    lateinit var sensorMonitor: SensorMonitor

    @Inject
    lateinit var romSettingMonitor: RomSettingMonitor

    @Inject
    lateinit var webhookServer: WebhookServer

    @Inject
    lateinit var smsConsentManager: SmsConsentManager

    @Inject
    lateinit var smsPreferences: SmsPreferences

    @Inject
    lateinit var notificationPreferences: NotificationPreferences

    @Inject
    lateinit var triggerIndex: TriggerIndex

    @Inject
    lateinit var activeTriggerStore: ActiveTriggerStore

    /**
     * Mirror of the Notification Manager's «monitoring notification» toggle.
     * Starts hidden (matching the [NotificationSettings.monitoringEnabled]
     * default) and is kept in sync by [applyMonitoringChannel] while the
     * service runs, so the very first [startAsForeground] already uses the
     * right visibility without waiting for the DataStore read.
     */
    @Volatile
    private var notificationVisible = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        runCatching { startAsForeground() }
            .onFailure { Log.w(TAG, "startAsForeground failed", it) }
        // Live binding: when the user toggles «Monitoring service» in
        // Settings > Notifications, the FGS notification is hidden (channel
        // drops to IMPORTANCE_NONE — no card anywhere, not even the shade)
        // or re-shown (IMPORTANCE_MIN — silent, no status-bar icon). The
        // flow emits the current value immediately on start, so the channel
        // is corrected milliseconds after the first startForeground.
        scope.launch {
            notificationPreferences.settings.collect { settings ->
                applyMonitoringChannel(settings.enabled && settings.monitoringEnabled)
            }
        }
        // Boot hygiene runs BEFORE any monitor re-arms from the ledger, so a
        // key armed days ago (device crashed while a trigger was active, exit
        // never ran) is dropped here and can never fire a late exit on boot.
        // The whole init sequence lives in one coroutine so the purge commits
        // before the first re-arm read — no race between the two.
        scope.launch {
            activeTriggerStore.purgeExpired()
            startMonitors()
            runCatching { romSettingMonitor.initialize() }
                .onFailure { Log.w(TAG, "monitor 'rom-setting' failed to initialize", it) }
            armSmsConsentIfEnabled()
            // Build the O(1) trigger index and keep it in sync with the
            // database (rebuilt on every save/enable-toggle via the Room-
            // backed flow).
            triggerIndex.start()
        }
    }

    private fun startMonitors() {
        val monitors = listOf(
            "battery" to { batteryMonitor.initialize() },
            "device-event" to { deviceEventMonitor.initialize() },
            "connectivity" to { connectivityMonitor.initialize() },
            "location" to { locationMonitor.initialize() },
            "bluetooth" to { bluetoothMonitor.initialize() },
            "ringer-mode" to { ringerModeMonitor.initialize() },
            "calendar" to { calendarMonitor.initialize() },
            "sensor" to { sensorMonitor.initialize() },
            "webhook" to { webhookServer.initialize() }
        )
        monitors.forEach { (name, init) ->
            runCatching { init() }
                .onFailure { Log.w(TAG, "monitor '$name' failed to initialize", it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // On OEM ROMs with an autostart gate (MIUI/HyperOS, One UI, ColorOS,
        // OxygenOS) background monitoring is silently killed after a reboot
        // unless the user enables auto-start. Nudge once, via a notification
        // that deep-links to the vendor screen, whenever monitoring starts.
        // The notification shares a single flag with the Permission Manager
        // OemCompat card (OemCompat.isHintDelivered), so the user is never
        // alerted by both channels.
        // Run off the main thread: maybeShow triggers ROM-family detection
        // (OemCompat -> RomDetector), and the first call lazily parses
        // /system/build.prop — a StrictMode DiskReadViolation on main.
        scope.launch {
            runCatching { OemAutostartNotifier.maybeShow(this@MonitoringService) }
                .onFailure { Log.w(TAG, "autostart notifier failed", it) }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The user swiped the app away: schedule an immediate restart so the
        // monitoring service keeps running in the background.
        super.onTaskRemoved(rootIntent)
        scheduleRestart()
    }

    override fun onDestroy() {
        isRunning = false
        stopMonitors()
        super.onDestroy()
    }

    /**
     * Android 15+ time-limit callback. For time-limited types (dataSync,
     * mediaProcessing) the system grants 6 hours per 24h and calls this when
     * the quota runs out; the service must stopSelf() within seconds or the
     * system throws. We stop the monitors cleanly and schedule a delayed
     * resume — re-starting immediately would throw
     * ForegroundServiceStartNotAllowedException until the window rolls over.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        isRunning = false
        stopMonitors()
        if (MonitoringTimeoutPolicy.isTimeLimitedType(fgsType)) {
            scheduleResumeAfterTimeout()
        }
        stopSelf(startId)
    }

    private fun stopMonitors() {
        batteryMonitor.stop()
        deviceEventMonitor.stop()
        connectivityMonitor.stop()
        locationMonitor.stop()
        bluetoothMonitor.stop()
        ringerModeMonitor.stop()
        calendarMonitor.stop()
        sensorMonitor.stop()
        webhookServer.stop()
    }

    /**
     * Re-arms the service via an inexact alarm after the time-limit window
     * may have rolled over. The attempt can still be rejected by the system
     * until the 24h window resets; START_STICKY / the next alarm retry.
     */
    private fun scheduleResumeAfterTimeout() {
        scheduleServiceStart(RESUME_REQUEST_CODE, MonitoringTimeoutPolicy.RESUME_RETRY_MS)
    }

    /**
     * When the user opted into the SMS User Consent path (Android 17 safe),
     * arm the consent request so OTP/verification SMS are delivered instantly
     * despite the 3-hour block on SMS_RECEIVED for targetSdk 37 apps.
     */
    private fun armSmsConsentIfEnabled() {
        scope.launch {
            val enabled = runCatching { smsPreferences.settings.first().userConsentEnabled }
                .getOrDefault(false)
            if (enabled) smsConsentManager.startListening()
        }
    }

    @Inject
    @com.nexaflow.core.engine.di.ApplicationScope
    lateinit var scope: kotlinx.coroutines.CoroutineScope

    /** Restarts the service shortly after the task is removed from recents. */
    private fun scheduleRestart() {
        scheduleServiceStart(RESTART_REQUEST_CODE, RESTART_DELAY_MS)
    }

    private fun scheduleServiceStart(requestCode: Int, delayMs: Long) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getService(
                this,
                requestCode,
                Intent(this, MonitoringService::class.java),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + delayMs,
                pendingIntent
            )
        } catch (_: Throwable) {
            // Best-effort; START_STICKY also brings the service back.
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Applies the user's «monitoring notification» choice to the live FGS
     * notification. The channel is DELETED and recreated at the target
     * importance — never just re-created in place: once Android has created a
     * channel, calling [NotificationManager.createNotificationChannel] again
     * with a *lower* importance is silently ignored (the first importance is
     * cached for the app's lifetime). That is exactly why the «مراقبة NexaFlow»
     * card kept appearing after the user turned it off. Deleting first forces
     * the system to accept the new importance, and removing the card explicitly
     * ([NotificationManager.cancel]) guarantees nothing stays in the shade.
     *
     * - hidden: IMPORTANCE_NONE — no card in the shade, no badge, no sound.
     * - visible: IMPORTANCE_MIN — silent card (no status-bar icon), the
     *   quietest form Android permits while a foreground service runs.
     *
     * Re-issuing startForeground with the same service and id simply replaces
     * the notification in place, so the FGS keeps running either way.
     */
    private fun applyMonitoringChannel(visible: Boolean) {
        notificationVisible = visible
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (visible) {
            recreateChannel(notificationManager, visible)
            startAsForeground()
        } else {
            // Delete-then-recreate so IMPORTANCE_NONE actually takes effect,
            // then remove any card already posted so nothing lingers in the
            // shade while the FGS continues running.
            recreateChannel(notificationManager, visible)
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    /**
     * Deletes and recreates the monitoring channel at the given importance.
     * Deleting a channel also removes any notification currently posted on it,
     * so the hidden state is guaranteed to leave the shade empty even if a
     * card was shown at a higher importance by an earlier app version.
     */
    private fun recreateChannel(
        notificationManager: NotificationManager,
        visible: Boolean
    ) {
        // The delete-then-recreate dance guarantees the importance change is
        // never swallowed by the system's channel cache. The binder calls can
        // transiently throw (DeadObjectException while the system server is
        // under load, e.g. right after boot); swallowing keeps the service up
        // — createNotificationChannel is idempotent, so a failed delete still
        // leaves the channel in its desired state whenever the next call lands.
        runCatching { notificationManager.deleteNotificationChannel(CHANNEL_ID) }
        runCatching {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.monitoring_channel_name),
                    channelImportance(visible)
                ).apply { setShowBadge(visible) }
            )
        }
    }

    private fun startAsForeground() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val visible = notificationVisible
        // IMPORTANCE_MIN keeps the monitoring notification out of the status
        // bar and completely silent — no sound, no heads-up, no icon. When the
        // user hides it (Settings > Notifications) the channel drops to
        // IMPORTANCE_NONE and the notification never appears at all. Android
        // still requires the FGS notification to exist while the service runs,
        // but this is the quietest form it can take. Delete-then-recreate so
        // the importance change is never swallowed by the channel cache.
        recreateChannel(notificationManager, visible)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow)
            // M3: brand-tinted small icon; service category for FGS semantics.
            // Colorized (API 31+) lets the brand color fill the header/app
            // icon area — the Google 2026 treatment. Ignored below API 31.
            .setColor(getColor(com.nexaflow.core.rom.R.color.notification_brand_color))
            .setColorized(true)
            .setContentTitle(getString(R.string.monitoring_title))
            .setContentText(getString(R.string.monitoring_text))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            // Never re-alert while the same foreground notification is live.
            .setOnlyAlertOnce(true)
            // No launcher badge for the service card (set on the channel) — it
            // is ambient, not an event, and badges would make it look unread.
            .setLocalOnly(true)
            // Android 14+: show the FGS notification immediately instead of
            // deferring it into the 10-second quiet window.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // Not ongoing: on Android 13+ the user can swipe the notification
            // away and it stays dismissed (the service keeps running).
            .setOngoing(false)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
    }

    companion object {
        /**
         * Channel importance for the monitoring notification: IMPORTANCE_NONE
         * hides it entirely (no card in the shade), IMPORTANCE_MIN shows it
         * silently (no sound, no heads-up, no status-bar icon).
         */
        internal fun channelImportance(visible: Boolean): Int =
            if (visible) NotificationManager.IMPORTANCE_MIN else NotificationManager.IMPORTANCE_NONE

        private const val TAG = "MonitoringService"
        private const val CHANNEL_ID = "nexaflow_monitoring"
        private const val NOTIFICATION_ID = 2001
        private const val RESTART_REQUEST_CODE = 42001
        private const val RESUME_REQUEST_CODE = 42002
        private const val START_REQUEST_CODE = 42003
        private const val RESTART_DELAY_MS = 15_000L

        /** Broadcast action: an alarm fired to start monitoring after boot. */
        const val ACTION_START_MONITORING = "com.nexaflow.core.engine.action.START_MONITORING"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitoringService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }

        /**
         * Android 15+ best practice: never start the monitoring FGS directly
         * from a BOOT_COMPLETED receiver (time-limited types are banned there
         * and OEM ROMs restrict even specialUse). Instead schedule a short
         * exact alarm that fires [MonitoringTimeoutPolicy.START_DELAY_MS]
         * after boot and start the service from the alarm receiver.
         */
        fun scheduleStart(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, AutomationAlarmReceiver::class.java)
                    .setAction(ACTION_START_MONITORING)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    START_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !alarmManager.canScheduleExactAlarms()
                ) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + MonitoringTimeoutPolicy.START_DELAY_MS, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + MonitoringTimeoutPolicy.START_DELAY_MS,
                        pendingIntent
                    )
                }
            } catch (_: Throwable) {
                // Fall back to a direct start when scheduling is unavailable;
                // guarded so a restricted start cannot crash the caller.
                runCatching { start(context) }
            }
        }
    }
}
