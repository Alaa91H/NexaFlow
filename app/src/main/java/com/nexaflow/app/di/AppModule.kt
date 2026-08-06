package com.nexaflow.app.di

import android.content.Context
import androidx.room.Room
import com.nexaflow.core.database.AppDatabase
import com.nexaflow.core.database.AutomationDao
import com.nexaflow.core.database.ExecutionDao
import com.nexaflow.core.database.Migrations
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.datastore.ThemePreferences
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.compat.AutomationWorkflowRunner
import com.nexaflow.core.logging.InMemoryLogStore
import com.nexaflow.core.logging.LogStore
import com.nexaflow.data.backup.BackupManager
import com.nexaflow.data.repository.AutomationRepositoryImpl
import com.nexaflow.data.repository.HistoryRepositoryImpl
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "nexaflow.db"
        ).addMigrations(*Migrations.ALL.toTypedArray())
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun provideAutomationDao(database: AppDatabase): AutomationDao {
        return database.automationDao()
    }

    @Provides
    fun provideExecutionDao(database: AppDatabase): ExecutionDao {
        return database.executionDao()
    }

    @Provides
    @Singleton
    fun provideThemePreferences(@ApplicationContext context: Context): ThemePreferences {
        return ThemePreferences(context)
    }

    @Provides
    @Singleton
    fun provideNotificationPreferences(@ApplicationContext context: Context): NotificationPreferences {
        return NotificationPreferences(context)
    }

    @Provides
    @Singleton
    fun provideAutomationRepository(dao: AutomationDao): AutomationRepository {
        return AutomationRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideBackupManager(automationRepository: AutomationRepository): BackupManager {
        return BackupManager(automationRepository)
    }

    @Provides
    @Singleton
    fun provideHistoryRepository(executionDao: ExecutionDao): HistoryRepository {
        return HistoryRepositoryImpl(executionDao)
    }

    @Provides
    @Singleton
    fun provideLogStore(): LogStore {
        // In-memory for now; Phase 3 (task-manager/debug UI) will swap in a
        // persistent Room-backed implementation behind the same interface.
        return InMemoryLogStore()
    }

    @Provides
    @Singleton
    fun provideExecutionEngine(
        @ApplicationContext context: Context,
        historyRepository: HistoryRepository,
        notificationPreferences: NotificationPreferences,
        logStore: LogStore
    ): ExecutionEngine {
        return ExecutionEngine(
            context,
            historyRepository,
            notificationPreferences,
            logStore = logStore
        )
    }

    @Provides
    @Singleton
    fun provideAutomationWorkflowRunner(
        @ApplicationContext context: Context,
        historyRepository: HistoryRepository,
        notificationPreferences: NotificationPreferences,
        logStore: LogStore
    ): AutomationWorkflowRunner {
        // Phase 4 compatibility runner: executes legacy automations through the
        // Phase-3 workflow engine (mapper + interpreter + state transaction).
        return AutomationWorkflowRunner.forDevice(
            context = context,
            historyRepository = historyRepository,
            notificationPreferences = notificationPreferences,
            logStore = logStore
        )
    }
}
