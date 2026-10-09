package com.nexaflow.core.common

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

/**
 * A point-in-time view of the application's default network.
 *
 * Android can expose an active [android.net.Network] before its capabilities
 * are available, and a capability read can fail while the framework is
 * switching networks. Treating that state as disconnected causes false trigger
 * exits, so this model preserves the distinction between a confirmed absence of
 * a default network and an unreadable snapshot.
 */
sealed class DefaultNetworkSnapshot {
    /** The default network exists and supplied its current capabilities. */
    data class Available(val capabilities: NetworkCapabilities) : DefaultNetworkSnapshot()

    /** The framework confirms that the application has no default network. */
    object NoActiveNetwork : DefaultNetworkSnapshot()

    /** The snapshot cannot be read reliably; callers must not infer a state. */
    object Unavailable : DefaultNetworkSnapshot()
}

enum class NetworkTransportState {
    CONNECTED,
    DISCONNECTED,
    UNKNOWN
}

/** A tri-state view of a network capability; UNKNOWN must never imply NO. */
enum class NetworkCapabilityState {
    YES,
    NO,
    UNKNOWN
}

/**
 * Reads and classifies the application's default network using modern
 * [NetworkCapabilities]. Callback consumers should pass the capabilities
 * supplied by `onCapabilitiesChanged` through [DefaultNetworkSnapshot.Available]
 * rather than performing a synchronous read while Android is dispatching a
 * network callback.
 */
object DefaultNetworkStateReader {

    @SuppressLint("MissingPermission") // ACCESS_NETWORK_STATE is declared by the consuming app modules.
    fun read(context: Context): DefaultNetworkSnapshot = runCatching {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return@runCatching DefaultNetworkSnapshot.Unavailable
        val network = connectivity.activeNetwork
            ?: return@runCatching DefaultNetworkSnapshot.NoActiveNetwork
        val capabilities = connectivity.getNetworkCapabilities(network)
            ?: return@runCatching DefaultNetworkSnapshot.Unavailable
        DefaultNetworkSnapshot.Available(capabilities)
    }.getOrDefault(DefaultNetworkSnapshot.Unavailable)

    fun transportState(
        snapshot: DefaultNetworkSnapshot,
        transport: Int
    ): NetworkTransportState = when (snapshot) {
        is DefaultNetworkSnapshot.Available -> {
            if (snapshot.capabilities.hasTransport(transport)) {
                NetworkTransportState.CONNECTED
            } else {
                NetworkTransportState.DISCONNECTED
            }
        }
        DefaultNetworkSnapshot.NoActiveNetwork -> NetworkTransportState.DISCONNECTED
        DefaultNetworkSnapshot.Unavailable -> NetworkTransportState.UNKNOWN
    }

    fun isValidatedWifi(snapshot: DefaultNetworkSnapshot): Boolean =
        snapshot is DefaultNetworkSnapshot.Available &&
            snapshot.capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            snapshot.capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    fun isValidatedUnmetered(snapshot: DefaultNetworkSnapshot): Boolean =
        snapshot is DefaultNetworkSnapshot.Available &&
            snapshot.capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            snapshot.capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

    fun validatedState(snapshot: DefaultNetworkSnapshot): NetworkCapabilityState =
        capabilityState(snapshot, NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    fun captivePortalState(snapshot: DefaultNetworkSnapshot): NetworkCapabilityState =
        capabilityState(snapshot, NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)

    /** YES means the network is metered; the framework's NOT_METERED bit is authoritative. */
    fun meteredState(snapshot: DefaultNetworkSnapshot): NetworkCapabilityState = when (snapshot) {
        is DefaultNetworkSnapshot.Available -> if (
            snapshot.capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        ) {
            NetworkCapabilityState.NO
        } else {
            NetworkCapabilityState.YES
        }
        DefaultNetworkSnapshot.NoActiveNetwork -> NetworkCapabilityState.NO
        DefaultNetworkSnapshot.Unavailable -> NetworkCapabilityState.UNKNOWN
    }

    /**
     * Matches optional capability filters using ANY/YES/NO. Unknown framework
     * evidence remains null so callers can preserve an active occurrence
     * instead of treating a transient read failure as a negative transition.
     */
    fun matchesCapabilities(
        snapshot: DefaultNetworkSnapshot,
        validated: String = "ANY",
        captivePortal: String = "ANY",
        metered: String = "ANY"
    ): Boolean? {
        val results = listOf(
            matchRequirement(validatedState(snapshot), validated),
            matchRequirement(captivePortalState(snapshot), captivePortal),
            matchRequirement(meteredState(snapshot), metered)
        )
        return when {
            results.any { it == false } -> false
            results.any { it == null } -> null
            else -> true
        }
    }

    /**
     * Exact Wi-Fi identity matching. A redacted SSID/BSSID is UNKNOWN rather
     * than a mismatch, so a missing runtime grant cannot fire an exit or claim
     * that a different access point is connected.
     */
    @Suppress("DEPRECATION") // API 26-28 expose Wi-Fi identity only through WifiManager.connectionInfo.
    fun matchesWifiIdentity(
        context: Context,
        snapshot: DefaultNetworkSnapshot,
        expectedSsid: String? = null,
        expectedBssid: String? = null
    ): Boolean? {
        val ssid = expectedSsid?.trim()?.takeIf(String::isNotEmpty)
        val bssid = expectedBssid?.trim()?.takeIf(String::isNotEmpty)
        if (ssid == null && bssid == null) return true

        val capabilities = when (snapshot) {
            is DefaultNetworkSnapshot.Available -> snapshot.capabilities
            DefaultNetworkSnapshot.NoActiveNetwork -> return false
            DefaultNetworkSnapshot.Unavailable -> return null
        }
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val wifiInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                capabilities.transportInfo as? WifiInfo
            } else {
                (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo
            }
        }.getOrNull() ?: return null

        val actualSsid = wifiInfo.ssid
            ?.trim()
            ?.removeSurrounding("\"")
            ?.takeUnless { it.isBlank() || it.equals(WifiManager.UNKNOWN_SSID, ignoreCase = true) }
        val actualBssid = wifiInfo.bssid
            ?.trim()
            ?.takeUnless { it.isBlank() || it.equals(REDACTED_BSSID, ignoreCase = true) }

        val ssidMatches = when {
            ssid == null -> true
            actualSsid == null -> null
            else -> actualSsid == ssid
        }
        val bssidMatches = when {
            bssid == null -> true
            actualBssid == null -> null
            else -> actualBssid.equals(bssid, ignoreCase = true)
        }
        return when {
            ssidMatches == false || bssidMatches == false -> false
            ssidMatches == null || bssidMatches == null -> null
            else -> true
        }
    }

    private fun capabilityState(
        snapshot: DefaultNetworkSnapshot,
        capability: Int
    ): NetworkCapabilityState = when (snapshot) {
        is DefaultNetworkSnapshot.Available -> if (snapshot.capabilities.hasCapability(capability)) {
            NetworkCapabilityState.YES
        } else {
            NetworkCapabilityState.NO
        }
        DefaultNetworkSnapshot.NoActiveNetwork -> NetworkCapabilityState.NO
        DefaultNetworkSnapshot.Unavailable -> NetworkCapabilityState.UNKNOWN
    }

    private fun matchRequirement(
        state: NetworkCapabilityState,
        requirement: String
    ): Boolean? = when (requirement.trim().uppercase()) {
        "ANY" -> true
        "YES" -> when (state) {
            NetworkCapabilityState.YES -> true
            NetworkCapabilityState.NO -> false
            NetworkCapabilityState.UNKNOWN -> null
        }
        "NO" -> when (state) {
            NetworkCapabilityState.YES -> false
            NetworkCapabilityState.NO -> true
            NetworkCapabilityState.UNKNOWN -> null
        }
        else -> false
    }

    private const val REDACTED_BSSID = "02:00:00:00:00:00"
}
