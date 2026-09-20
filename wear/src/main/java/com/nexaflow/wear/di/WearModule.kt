package com.nexaflow.wear.di

import android.content.Context
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
 * [MessageClient] is used by [WearDataLayerClient] to send commands to the
 * phone. [NodeClient] is used to resolve the connected phone's node ID before
 * sending each message.
 */
@Module
@InstallIn(SingletonComponent::class)
object WearModule {

    @Provides
    @Singleton
    fun provideMessageClient(@ApplicationContext context: Context): MessageClient =
        Wearable.getMessageClient(context)

    @Provides
    @Singleton
    fun provideNodeClient(@ApplicationContext context: Context): NodeClient =
        Wearable.getNodeClient(context)
}
