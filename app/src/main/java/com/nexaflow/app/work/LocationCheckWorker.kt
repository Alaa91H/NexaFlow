package com.nexaflow.app.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexaflow.core.database.AppDatabase
import com.nexaflow.core.datastore.LocationPreferences
import com.nexaflow.core.engine.LocationAccess
import com.nexaflow.core.engine.LocationMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Periodic background location check (interval chosen in Settings > Location):
 *
 * When the user keeps the system location switch OFF but has enabled
 * location-triggered tasks, this worker periodically re-enables location
 * through the elevated runtime (Shizuku / root / WRITE_SECURE_SETTINGS),
 * obtains a fresh fix so [LocationMonitor] can evaluate every geofence
 * (ENTER/EXIT), then switches location back off — fully silently, no dialogs,
 * no settings screens. If no elevated runtime exists the worker does nothing
 * (the foreground editor still offers the one-tap settings fallback).
 */
@HiltWorker
class LocationCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val locationPreferences: LocationPreferences,
    private val database: AppDatabase,
    private val locationMonitor: LocationMonitor
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val interval = locationPreferences.checkIntervalMinutes.first()
            if (interval <= 0) return Result.success()

            val automations = database.automationDao().getAllAutomations().first()
            // JSON columns are opaque strings here; match the serialized
            // trigger type exactly (a LOCATION-trigger, not the
            // SYSTEM_LOCATION action whose JSON reads "SYSTEM_LOCATION").
            val needsLocation = automations.any {
                it.enabled && it.triggersJson.contains("\"type\":\"LOCATION\"")
            }
            if (!needsLocation) return Result.success()
            if (!LocationAccess.hasLocationPermission(applicationContext)) return Result.success()
            if (LocationAccess.isLocationEnabled(applicationContext)) return Result.success()

            val previousMode = LocationAccess.currentLocationMode(applicationContext)
            if (!LocationAccess.enableLocationSilently(applicationContext)) {
                // No elevated runtime — nothing we can do from the background.
                return Result.success()
            }
            try {
                // Providers were off when monitoring registered; re-register so
                // fixes start flowing, then feed at least one fresh fix into
                // the geofence evaluation before restoring the previous mode.
                locationMonitor.refresh()
                val fix = LocationAccess.getCurrentLocation(applicationContext, FIX_TIMEOUT_MS)
                if (fix != null) locationMonitor.checkLocation(fix)
                // A short settle lets a couple of live updates land too, so the
                // exit side of a geofence has a chance to evaluate as well.
                delay(FIX_SETTLE_MS)
            } finally {
                LocationAccess.restoreLocationModeIfWeChanged(applicationContext, previousMode)
            }
            // Re-align the schedule with the current preference (the user may
            // have changed the interval since this run was enqueued), including
            // cancelling the chain when they switched back to manual.
            LocationCheckScheduler.schedule(applicationContext, interval)
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "Periodic location check failed", t)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "nexaflow.periodic.location"
        private const val TAG = "LocationCheckWorker"
        private const val FIX_TIMEOUT_MS = 20_000L
        private const val FIX_SETTLE_MS = 10_000L
    }
}
