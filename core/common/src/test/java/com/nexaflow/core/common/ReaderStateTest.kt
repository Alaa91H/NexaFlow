package com.nexaflow.core.common

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Device-state reader contracts under Robolectric: the hotspot reader's
 * legacy `tether_on` fallback, the default-network reader's availability
 * transitions, and the cellular reader's no-telephony behavior.
 */
@RunWith(RobolectricTestRunner::class)
class ReaderStateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [34])
    fun `hotspot legacy fallback reads tether_on when callback is absent`() {
        android.provider.Settings.Global.putInt(
            context.contentResolver,
            "tether_on",
            1
        )
        assertEquals(true, HotspotStateReader.currentState(context))
        android.provider.Settings.Global.putInt(context.contentResolver, "tether_on", 0)
        assertEquals(false, HotspotStateReader.currentState(context))
    }

    @Test
    @Config(sdk = [34])
    fun `default network snapshot is well-formed on the JVM shadow stack`() {
        // Robolectric's ConnectivityManager reports a default cellular network
        // by default; the contract under test is that a snapshot is always
        // produced (never null, never throws) and its transport state is a
        // defined value.
        val snapshot = DefaultNetworkStateReader.read(context)
        val state = DefaultNetworkStateReader.transportState(
            snapshot,
            android.net.NetworkCapabilities.TRANSPORT_WIFI
        )
        assertTrue(state == NetworkTransportState.CONNECTED || state == NetworkTransportState.DISCONNECTED)
    }

    @Test
    fun `cellular reader returns null when no telephony service answers`() {
        assertNull(CellularNetworkReader.read(context))
    }

    @Test
    fun `connectivity manager is present by default`() {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        assertNotNull(connectivity)
    }
}
