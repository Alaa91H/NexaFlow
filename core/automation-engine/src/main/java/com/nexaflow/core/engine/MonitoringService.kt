package com.nexaflow.core.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        batteryMonitor.stop()
        deviceEventMonitor.stop()
        connectivityMonitor.stop()
        locationMonitor.stop()
        bluetoothMonitor.stop()
        ringerModeMonitor.stop()
        super.onDestroy()
    }

    /** Restarts the service shortly after the task is removed from recents. */
    private fun scheduleRestart() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(this, MonitoringService::class.java)
            val pendingIntent = android.app.PendingIntent.getService(
                this,
                RESTART_REQUEST_CODE,
                intent,
                android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.set(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + RESTART_DELAY_MS,
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
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
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
        private const val RESTART_DELAY_MS = 15_000L

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitoringService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }
    }
}
