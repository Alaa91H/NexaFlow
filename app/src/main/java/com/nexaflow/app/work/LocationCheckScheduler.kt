package com.nexaflow.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Keeps the periodic location check (Settings > Location) in sync with the
 * chosen interval. Manual (0) cancels the job; any positive interval schedules
 * a periodic worker with that exact period (WorkManager minimum is 15 minutes,
 * which matches the smallest preset).
 */
object LocationCheckScheduler {

    fun schedule(context: Context, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)
        if (intervalMinutes <= 0) {
            workManager.cancelUniqueWork(LocationCheckWorker.UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<LocationCheckWorker>(
            intervalMinutes.toLong(),
            TimeUnit.MINUTES
        ).build()
        // UPDATE replaces the existing request with the new interval.
        workManager.enqueueUniquePeriodicWork(
            LocationCheckWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
