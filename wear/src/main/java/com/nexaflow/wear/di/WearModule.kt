package com.nexaflow.wear.di

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides Wearable Data Layer clients for the watch app.
 *
 * [DataClient] bootstraps the last synchronized automation snapshot,
 * [MessageClient] sends commands to the phone, and [NodeClient] provides a
 * compatibility fallback while capabilities propagate after an upgrade.
 */
@Module
@InstallIn(SingletonComponent::class)
object WearModule {

    @Provides
    @Singleton
    fun provideDataClient(@ApplicationContext context: Context): DataClient =
        Wearable.getDataClient(context)

    @Provides
    @Singleton
    fun provideMessageClient(@ApplicationContext context: Context): MessageClient =
        Wearable.getMessageClient(context)

    @Provides
    @Singleton
    fun provideNodeClient(@ApplicationContext context: Context): NodeClient =
        Wearable.getNodeClient(context)
}
