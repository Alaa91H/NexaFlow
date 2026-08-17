package com.nexaflow.core.engine.di

import android.util.Log
import com.nexaflow.core.common.AppDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    private const val TAG = "CoroutinesModule"

    @Provides
    @Singleton
    fun provideAppDispatchers(): AppDispatchers = AppDispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatchers: AppDispatchers): CoroutineScope {
        // Background monitoring (scheduler collects, sensor/webhook/location
        // monitors, notification receivers) must never take the app down: one
        // bad automation row or one transient monitor error would otherwise
        // crash the whole process via the default uncaught handler. Log and
        // continue instead — a stuck monitor beats a force-closed app.
        val handler = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Background coroutine failed", throwable)
        }
        return CoroutineScope(SupervisorJob() + dispatchers.default + handler)
    }
}
