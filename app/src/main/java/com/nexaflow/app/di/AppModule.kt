package com.nexaflow.app.di

import android.content.Context
import androidx.room.Room
import com.nexaflow.core.database.AppDatabase
import com.nexaflow.core.database.AutomationDao
import com.nexaflow.data.repository.AutomationRepositoryImpl
import com.nexaflow.domain.repositories.AutomationRepository
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
        ).build()
    }

    @Provides
    fun provideAutomationDao(database: AppDatabase): AutomationDao {
        return database.automationDao()
    }

    @Provides
    @Singleton
    fun provideAutomationRepository(dao: AutomationDao): AutomationRepository {
        return AutomationRepositoryImpl(dao)
    }
}
