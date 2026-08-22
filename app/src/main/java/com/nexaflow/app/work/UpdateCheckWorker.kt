package com.nexaflow.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexaflow.app.MainActivity
import com.nexaflow.app.R
import com.nexaflow.core.datastore.UpdatePreferences
import com.nexaflow.core.datastore.UpdateSettings
import com.nexaflow.feature.settings.UpdateChecker
import com.nexaflow.feature.settings.UpdateVersion
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Deferrable automatic update check. It never opens UI and never reports a
 * same-version result: notification is reserved only for a proven newer,
 * canonical release not previously announced by this device.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val updatePreferences: UpdatePreferences
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settings = updatePreferences.settings.first()
        if (!settings.automaticChecksEnabled) return Result.success()

        val info = UpdateChecker.fetchLatestJson()?.let(UpdateChecker::parseRelease)
            ?: return Result.success()
        if (!UpdateVersion.shouldOfferUpdate(info.version, installedVersionName())) {
            return Result.success()
        }
        val canonicalVersion = UpdateVersion.canonical(info.version) ?: return Result.success()
        if (!UpdateNotification.canPost(applicationContext)) return Result.success()
        if (!updatePreferences.claimNotification(canonicalVersion)) return Result.success()

        UpdateNotification.show(applicationContext, info.version)
        return Result.success()
    }

    private fun installedVersionName(): String = runCatching {
        applicationContext.packageManager
            .getPackageInfo(applicationContext.packageName, 0)
            .versionName
            .orEmpty()
    }.getOrDefault("")

    companion object {
        const val UNIQUE_WORK_NAME = "nexaflow.periodic.update-check"
    }
}

/** Keeps the one periodic request aligned with persisted user settings. */
object UpdateCheckScheduler {

    fun schedule(context: Context, settings: UpdateSettings) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.automaticChecksEnabled) {
            workManager.cancelUniqueWork(UpdateCheckWorker.UNIQUE_WORK_NAME)
            UpdateNotification.cancel(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            settings.frequency.repeatDays,
            TimeUnit.DAYS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            // The manual button remains the immediate path. Automatic checks
            // begin only after the chosen cadence, not as a surprise on enable.
            .setInitialDelay(settings.frequency.repeatDays, TimeUnit.DAYS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UpdateCheckWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

internal object UpdateNotification {
    private const val CHANNEL_ID = "nexaflow_updates"
    internal const val NOTIFICATION_ID = 5_038_800

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED)

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun show(context: Context, version: String) {
        // Keep this check in the same method as notify so Android Lint can
        // prove that POST_NOTIFICATIONS is respected on Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow)
            .setContentTitle(context.getString(R.string.update_notification_title))
            .setContentText(context.getString(R.string.update_notification_text, version))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.update_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.update_notification_channel_sub)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
