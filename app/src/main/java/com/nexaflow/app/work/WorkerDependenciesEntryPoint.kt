package com.nexaflow.app.work

import com.nexaflow.core.database.AppDatabase
import com.nexaflow.core.datastore.LocationPreferences
import com.nexaflow.core.datastore.UpdatePreferences
import com.nexaflow.core.engine.LocationMonitor
import com.nexaflow.data.repository.CanonicalWorkflowMigrationRunner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * WorkManager dependency bridge.
 *
 * Workers use WorkManager's default factory/initializer and resolve their
 * singleton dependencies lazily from the application graph when execution
 * begins. This avoids a custom WorkManager Configuration.Provider entirely,
 * which in turn keeps AndroidX Startup in its supported default path and
 * removes manifest-merge suppression markers.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerDependenciesEntryPoint {
    fun appDatabase(): AppDatabase
    fun canonicalWorkflowMigrationRunner(): CanonicalWorkflowMigrationRunner
    fun locationPreferences(): LocationPreferences
    fun locationMonitor(): LocationMonitor
    fun updatePreferences(): UpdatePreferences
}
