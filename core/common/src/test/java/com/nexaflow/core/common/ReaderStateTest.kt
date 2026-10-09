package com.nexaflow.core.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowWifiInfo

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
    fun `network capability states distinguish validated captive and metered`() {
        val validatedUnmetered = ShadowNetworkCapabilities.newInstance()
        shadowOf(validatedUnmetered).apply {
            addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        val captiveMetered = ShadowNetworkCapabilities.newInstance()
        shadowOf(captiveMetered).apply {
            addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        }

        assertEquals(
            NetworkCapabilityState.YES,
            DefaultNetworkStateReader.validatedState(DefaultNetworkSnapshot.Available(validatedUnmetered))
        )
        assertEquals(
            NetworkCapabilityState.NO,
            DefaultNetworkStateReader.captivePortalState(DefaultNetworkSnapshot.Available(validatedUnmetered))
        )
        assertEquals(
            NetworkCapabilityState.NO,
            DefaultNetworkStateReader.meteredState(DefaultNetworkSnapshot.Available(validatedUnmetered))
        )
        assertEquals(
            NetworkCapabilityState.YES,
            DefaultNetworkStateReader.captivePortalState(DefaultNetworkSnapshot.Available(captiveMetered))
        )
        assertEquals(
            NetworkCapabilityState.YES,
            DefaultNetworkStateReader.meteredState(DefaultNetworkSnapshot.Available(captiveMetered))
        )
        assertEquals(
            NetworkCapabilityState.NO,
            DefaultNetworkStateReader.validatedState(DefaultNetworkSnapshot.Available(captiveMetered))
        )
    }

    @Test
    fun `unavailable capability reads stay unknown while confirmed disconnection stays no`() {
        assertEquals(
            NetworkCapabilityState.UNKNOWN,
            DefaultNetworkStateReader.validatedState(DefaultNetworkSnapshot.Unavailable)
        )
        assertEquals(
            NetworkCapabilityState.UNKNOWN,
            DefaultNetworkStateReader.captivePortalState(DefaultNetworkSnapshot.Unavailable)
        )
        assertEquals(
            NetworkCapabilityState.UNKNOWN,
            DefaultNetworkStateReader.meteredState(DefaultNetworkSnapshot.Unavailable)
        )
        assertEquals(
            NetworkCapabilityState.NO,
            DefaultNetworkStateReader.validatedState(DefaultNetworkSnapshot.NoActiveNetwork)
        )
        assertEquals(
            NetworkCapabilityState.NO,
            DefaultNetworkStateReader.captivePortalState(DefaultNetworkSnapshot.NoActiveNetwork)
        )
        assertEquals(
            NetworkCapabilityState.NO,
            DefaultNetworkStateReader.meteredState(DefaultNetworkSnapshot.NoActiveNetwork)
        )
    }

    @Test
    fun `network capability filters are conjunctive and unavailable evidence stays unknown`() {
        val validatedUnmetered = ShadowNetworkCapabilities.newInstance()
        shadowOf(validatedUnmetered).apply {
            addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        val snapshot = DefaultNetworkSnapshot.Available(validatedUnmetered)

        assertEquals(
            true,
            DefaultNetworkStateReader.matchesCapabilities(
                snapshot,
                validated = "YES",
                captivePortal = "NO",
                metered = "NO"
            )
        )
        assertEquals(
            false,
            DefaultNetworkStateReader.matchesCapabilities(snapshot, metered = "YES")
        )
        assertEquals(
            null,
            DefaultNetworkStateReader.matchesCapabilities(
                DefaultNetworkSnapshot.Unavailable,
                validated = "YES"
            )
        )
        assertEquals(
            true,
            DefaultNetworkStateReader.matchesCapabilities(DefaultNetworkSnapshot.Unavailable)
        )
    }

    @Test
    @Config(sdk = [34])
    fun `wifi identity filters compare exact ssid and case-insensitive bssid and fail unknown closed`() {
        shadowOf(context.applicationContext as android.app.Application)
            .grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(
            android.content.pm.PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        )
        val wifiInfo = ShadowWifiInfo.newInstance()
        shadowOf(wifiInfo).apply {
            setSSID("Studio Wi-Fi")
            setBSSID("AA:BB:CC:DD:EE:FF")
        }
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).apply {
            addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            setTransportInfo(wifiInfo)
        }
        assertEquals(wifiInfo, capabilities.transportInfo)
        val snapshot = DefaultNetworkSnapshot.Available(capabilities)

        assertEquals(
            true,
            DefaultNetworkStateReader.matchesWifiIdentity(context, snapshot, "Studio Wi-Fi", null)
        )
        assertEquals(true, DefaultNetworkStateReader.matchesWifiIdentity(context, snapshot, null, "aa:bb:cc:dd:ee:ff"))
        assertEquals(false, DefaultNetworkStateReader.matchesWifiIdentity(context, snapshot, "Other", null))
        assertEquals(
            null,
            DefaultNetworkStateReader.matchesWifiIdentity(context, DefaultNetworkSnapshot.Unavailable, "Studio Wi-Fi", null)
        )
        assertEquals(
            false,
            DefaultNetworkStateReader.matchesWifiIdentity(context, DefaultNetworkSnapshot.NoActiveNetwork, "Studio Wi-Fi", null)
        )
    }

    @Test
    @Config(sdk = [34])
    fun `wifi identity is unknown when location permission is revoked`() {
        shadowOf(context.applicationContext as android.app.Application)
            .denyPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val wifiInfo = ShadowWifiInfo.newInstance()
        shadowOf(wifiInfo).setSSID("Studio Wi-Fi")
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).apply {
            addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            setTransportInfo(wifiInfo)
        }

        assertEquals(
            null,
            DefaultNetworkStateReader.matchesWifiIdentity(
                context,
                DefaultNetworkSnapshot.Available(capabilities),
                expectedSsid = "Studio Wi-Fi"
            )
        )
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
