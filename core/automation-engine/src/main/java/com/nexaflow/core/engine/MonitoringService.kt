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
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
    lateinit var webhookServer: WebhookServer

    @Inject
    lateinit var smsConsentManager: SmsConsentManager

    @Inject
    lateinit var smsPreferences: SmsPreferences

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startAsForeground()
        batteryMonitor.initialize()
        deviceEventMonitor.initialize()
        connectivityMonitor.initialize()
        locationMonitor.initialize()
        bluetoothMonitor.initialize()
        ringerModeMonitor.initialize()
        calendarMonitor.initialize()
        sensorMonitor.initialize()
        webhookServer.initialize()
        armSmsConsentIfEnabled()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // On OEM ROMs with an autostart gate (MIUI/HyperOS, One UI, ColorOS,
        // OxygenOS) background monitoring is silently killed after a reboot
        // unless the user enables auto-start. Nudge once, via a notification
        // that deep-links to the vendor screen, whenever monitoring starts.
        // The notification shares a single flag with the Permission Manager
        // OemCompat card (OemCompat.isHintDelivered), so the user is never
        // alerted by both channels.
        OemAutostartNotifier.maybeShow(this)
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

    private fun startAsForeground() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitoring_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow)
            .setContentTitle(getString(R.string.monitoring_title))
            .setContentText(getString(R.string.monitoring_text))
            .setOngoing(true)
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
