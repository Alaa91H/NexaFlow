package com.nexaflow.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nexaflow.core.database.AppDatabase
import com.nexaflow.core.database.AutomationDao
import com.nexaflow.core.database.ExecutionDao
import com.nexaflow.core.database.Migrations
import com.nexaflow.core.database.VariableDao
import com.nexaflow.core.datastore.LocationPreferences
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.datastore.PrivacyPreferences
import com.nexaflow.core.datastore.SmsPreferences
import com.nexaflow.core.datastore.ThemePreferences
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.compat.AutomationWorkflowRunner
import com.nexaflow.core.logging.InMemoryLogStore
import com.nexaflow.core.logging.LogStore
import com.nexaflow.core.security.KeystoreSecureStorage
import com.nexaflow.core.security.SecureStorage
import com.nexaflow.data.backup.BackupManager
import com.nexaflow.data.repository.AutomationRepositoryImpl
import com.nexaflow.data.repository.HistoryRepositoryImpl
import com.nexaflow.data.repository.PluginRepositoryImpl
import com.nexaflow.data.repository.VariableRepositoryImpl
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import com.nexaflow.domain.repositories.PluginRepository
import com.nexaflow.domain.repositories.VariableRepository
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
        )
            // WAL allows concurrent readers while writing, eliminating
            // read/write lock contention under the engine's write-heavy load.
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(*Migrations.ALL.toTypedArray())
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
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
    fun provideVariableDao(database: AppDatabase): VariableDao {
        return database.variableDao()
    }

    @Provides
    @Singleton
    fun provideSecureStorage(@ApplicationContext context: Context): SecureStorage {
        // Keystore AES-GCM; encrypts sensitive variable values at rest.
        return KeystoreSecureStorage(context)
    }

    @Provides
    @Singleton
    fun provideVariableRepository(dao: VariableDao, secureStorage: SecureStorage): VariableRepository {
        return VariableRepositoryImpl(dao, secureStorage)
    }

    @Provides
    @Singleton
    fun providePluginRepository(@ApplicationContext context: Context): PluginRepository {
        return PluginRepositoryImpl(context)
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
    fun provideSmsPreferences(@ApplicationContext context: Context): SmsPreferences {
        return SmsPreferences(context)
    }

    @Provides
    @Singleton
    fun providePrivacyPreferences(@ApplicationContext context: Context): PrivacyPreferences {
        return PrivacyPreferences(context)
    }

    @Provides
    @Singleton
    fun provideLocationPreferences(@ApplicationContext context: Context): LocationPreferences {
        return LocationPreferences(context)
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
        logStore: LogStore,
        variableRepository: VariableRepository
    ): ExecutionEngine {
        return ExecutionEngine(
            context,
            historyRepository,
            notificationPreferences,
            logStore = logStore,
            variableRepository = variableRepository
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
