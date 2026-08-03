package com.nexaflow.app.di

import android.content.Context
import androidx.room.Room
import com.nexaflow.core.database.AppDatabase
import com.nexaflow.core.database.AutomationDao
import com.nexaflow.core.database.ExecutionDao
import com.nexaflow.core.execution.ExecutionEngine
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
        ).fallbackToDestructiveMigration().build()
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
    fun provideAutomationRepository(dao: AutomationDao): AutomationRepository {
        return AutomationRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideHistoryRepository(executionDao: ExecutionDao): HistoryRepository {
        return HistoryRepositoryImpl(executionDao)
    }

    @Provides
    @Singleton
    fun provideExecutionEngine(
        @ApplicationContext context: Context,
        historyRepository: HistoryRepository
    ): ExecutionEngine {
        return ExecutionEngine(context, historyRepository)
    }
}
