package com.nexaflow.core.common

import android.net.NetworkCapabilities
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultNetworkStateReaderTest {

    @Test
    fun `no default network is a confirmed disconnected transport`() {
        assertEquals(
            NetworkTransportState.DISCONNECTED,
            DefaultNetworkStateReader.transportState(
                DefaultNetworkSnapshot.NoActiveNetwork,
                NetworkCapabilities.TRANSPORT_WIFI
            )
        )
        assertEquals(
            NetworkTransportState.DISCONNECTED,
            DefaultNetworkStateReader.transportState(
                DefaultNetworkSnapshot.NoActiveNetwork,
                NetworkCapabilities.TRANSPORT_CELLULAR
            )
        )
    }

    @Test
    fun `unreadable capabilities never become a false disconnected state`() {
        assertEquals(
            NetworkTransportState.UNKNOWN,
            DefaultNetworkStateReader.transportState(
                DefaultNetworkSnapshot.Unavailable,
                NetworkCapabilities.TRANSPORT_WIFI
            )
        )
        assertEquals(
            NetworkTransportState.UNKNOWN,
            DefaultNetworkStateReader.transportState(
                DefaultNetworkSnapshot.Unavailable,
                NetworkCapabilities.TRANSPORT_CELLULAR
            )
        )
    }
}
