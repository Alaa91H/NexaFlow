package com.nexaflow.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nexaflow.core.database.AppDatabase
import com.nexaflow.core.database.AutomationDao
import com.nexaflow.core.database.CorruptionRecoveryFactory
import com.nexaflow.core.database.ExecutionDao
import com.nexaflow.core.database.Migrations
import com.nexaflow.core.database.VariableDao
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.LocationPreferences
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.datastore.PrivacyPreferences
import com.nexaflow.core.datastore.SmsPreferences
import com.nexaflow.core.datastore.ThemePreferences
import com.nexaflow.core.datastore.UpdatePreferences
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.capability.AndroidCapabilityDeviceStateReader
import com.nexaflow.core.execution.capability.AndroidIntentCapabilityBackend
import com.nexaflow.core.execution.capability.AndroidPublicCapabilityBackend
import com.nexaflow.core.execution.capability.AndroidPublicCapabilityCatalog
import com.nexaflow.core.execution.capability.AccessibilityCapabilityBackend
import com.nexaflow.core.execution.capability.AccessibilityCapabilityCatalog
import com.nexaflow.core.execution.capability.AccessibilityInteractionBridge
import com.nexaflow.core.execution.capability.CapabilityEnvironmentInspector
import com.nexaflow.core.execution.capability.CapabilityExecutionService
import com.nexaflow.core.execution.capability.CapabilityRegistry
import com.nexaflow.core.execution.capability.CapabilityResolver
import com.nexaflow.core.execution.capability.CapabilityStateStore
import com.nexaflow.core.execution.capability.PluginCapabilityBackend
import com.nexaflow.core.execution.capability.PluginCapabilityCatalog
import com.nexaflow.core.execution.capability.PluginConditionCapabilityCatalog
import com.nexaflow.core.execution.capability.PrivilegedCapabilityCatalog
import com.nexaflow.core.execution.capability.RootCapabilityBackend
import com.nexaflow.core.execution.capability.ShizukuCapabilityBackend
import com.nexaflow.core.execution.compat.AutomationWorkflowRunner
import com.nexaflow.core.execution.dryrun.WorkflowDryRunService
import com.nexaflow.core.execution.recovery.ExecutionRecoveryCoordinator
import com.nexaflow.core.logging.InMemoryLogStore
import com.nexaflow.core.logging.LogStore
import com.nexaflow.core.logging.RedactingLogStore
import com.nexaflow.core.pluginsdk.PluginDiscoveryRegistry
import com.nexaflow.core.security.KeystoreSecureStorage
import com.nexaflow.core.security.SecureStorage
import com.nexaflow.data.backup.BackupManager
import com.nexaflow.data.repository.AutomationRepositoryImpl
import com.nexaflow.data.repository.HealthRepositoryImpl
import com.nexaflow.data.repository.HistoryRepositoryImpl
import com.nexaflow.data.repository.PluginRepositoryImpl
import com.nexaflow.data.repository.VariableRepositoryImpl
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HealthRepository
import com.nexaflow.domain.repositories.HistoryRepository
import com.nexaflow.domain.repositories.PluginRepository
import com.nexaflow.domain.repositories.VariableRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions") // Hilt binding boundary; splitting homogeneous providers adds no runtime value.
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
            // SQLite corruption handler: copies the corrupt database for
            // analysis, then recreates it on the next open instead of
            // crashing on a permanently broken file.
            .openHelperFactory(CorruptionRecoveryFactory())
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
    fun providePluginDiscoveryRegistry(@ApplicationContext context: Context): PluginDiscoveryRegistry {
        return PluginDiscoveryRegistry(context)
    }

    @Provides
    @Singleton
    fun providePluginRepository(registry: PluginDiscoveryRegistry): PluginRepository {
        return PluginRepositoryImpl(registry)
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
    fun provideUpdatePreferences(@ApplicationContext context: Context): UpdatePreferences {
        return UpdatePreferences(context)
    }

    @Provides
    @Singleton
    fun provideActiveTriggerStore(@ApplicationContext context: Context): ActiveTriggerStore {
        return ActiveTriggerStore(context)
    }

    @Provides
    @Singleton
    fun provideActiveExecutionStore(@ApplicationContext context: Context): ActiveExecutionStore {
        return ActiveExecutionStore(context)
    }

    @Provides
    @Singleton
    fun provideExecutionRecoveryCoordinator(
        activeExecutionStore: ActiveExecutionStore
    ): ExecutionRecoveryCoordinator = ExecutionRecoveryCoordinator(activeExecutionStore)

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
    fun provideHealthRepository(
        automationRepository: AutomationRepository,
        historyRepository: HistoryRepository
    ): HealthRepository {
        return HealthRepositoryImpl(automationRepository, historyRepository)
    }

    @Provides
    @Singleton
    fun provideLogStore(): LogStore {
        // In-memory for now; Phase 3 (task-manager/debug UI) will swap in a
        // persistent Room-backed implementation behind the same interface.
        // Redaction is intentionally outside the store so every future backend
        // gets the same secret boundary by default.
        return RedactingLogStore(InMemoryLogStore())
    }

    @Provides
    @Singleton
    fun provideAccessibilityInteractionBridge(): AccessibilityInteractionBridge = AccessibilityInteractionBridge()

    @Provides
    @Singleton
    fun provideCapabilityRegistry(
        @ApplicationContext context: Context,
        automationRepository: AutomationRepository,
        pluginDiscoveryRegistry: PluginDiscoveryRegistry,
        accessibilityBridge: AccessibilityInteractionBridge
    ): CapabilityRegistry =
        CapabilityRegistry.of(
            descriptors = AndroidPublicCapabilityCatalog.descriptors() +
                PluginCapabilityCatalog.descriptors() +
                PluginConditionCapabilityCatalog.descriptors() +
                PrivilegedCapabilityCatalog.descriptors() +
                AccessibilityCapabilityCatalog.descriptors(),
            backends = listOf(
                AndroidPublicCapabilityBackend(context),
                AndroidIntentCapabilityBackend(context),
                PluginCapabilityBackend(context, automationRepository, pluginDiscoveryRegistry),
                // Both channels require explicit request-policy selection; the
                // resolver never falls through from Shizuku to Root or vice versa.
                ShizukuCapabilityBackend(),
                RootCapabilityBackend(),
                AccessibilityCapabilityBackend(context, accessibilityBridge)
            )
        )

    @Provides
    @Singleton
    fun provideCapabilityResolver(registry: CapabilityRegistry): CapabilityResolver =
        CapabilityResolver(registry)

    @Provides
    @Singleton
    fun provideCapabilityStateStore(
        registry: CapabilityRegistry,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope
    ): CapabilityStateStore = CapabilityStateStore(
        registry = registry,
        environmentInspector = CapabilityEnvironmentInspector.forContext(context),
        scope = scope
    )

    @Provides
    @Singleton
    fun provideCapabilityExecutionService(
        resolver: CapabilityResolver,
        @ApplicationContext context: Context
    ): CapabilityExecutionService = CapabilityExecutionService(
        resolver = resolver,
        deviceStateProvider = {
            AndroidCapabilityDeviceStateReader(context).capture(System.currentTimeMillis())
        }
    )

    @Provides
    @Singleton
    fun provideWorkflowDryRunService(
        resolver: CapabilityResolver,
        @ApplicationContext context: Context
    ): WorkflowDryRunService = WorkflowDryRunService(
        capabilityResolver = resolver,
        deviceStateProvider = {
            AndroidCapabilityDeviceStateReader(context).capture(System.currentTimeMillis())
        }
    )

    @Provides
    @Singleton
    fun provideExecutionEngine(
        @ApplicationContext context: Context,
        historyRepository: HistoryRepository,
        notificationPreferences: NotificationPreferences,
        logStore: LogStore,
        variableRepository: VariableRepository,
        capabilityExecutionService: CapabilityExecutionService,
        capabilityStateStore: CapabilityStateStore
    ): ExecutionEngine {
        return ExecutionEngine(
            context,
            historyRepository,
            notificationPreferences,
            logStore = logStore,
            variableRepository = variableRepository,
            capabilityExecutionService = capabilityExecutionService,
            capabilitySnapshotProvider = { capabilityStateStore.snapshot.value }
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
