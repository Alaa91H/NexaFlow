package com.nexaflow.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexaflow.core.database.ExecutionDao
import com.nexaflow.core.database.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic background maintenance, scheduled through WorkManager so it keeps
 * running even when the monitoring service is dead and respects Doze / battery
 * saver (unlike a naive in-service timer).
 *
 * Currently prunes the execution history by both age and count (defense in
 * depth on top of the per-insert retention in [ExecutionDao.insertWithRetention]
 * — covers records inserted before the retention logic existed, and reclaims
 * space even when the insert path is bypassed). Widget refresh is left to the
 * existing AUTOMATIONS_CHANGED broadcast path.
 */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: AppDatabase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val dao = database.executionDao()
            dao.pruneOlderThan(System.currentTimeMillis() - ExecutionDao.RETENTION_MS)
            dao.pruneExcess(ExecutionDao.RETAIN_LIMIT)
            Result.success()
        } catch (_: Throwable) {
            // Transient DB lock / corruption — retry with backoff next period.
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "nexaflow.periodic.maintenance"

        /** Enqueue once (keep the existing job on re-installs/updates). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
