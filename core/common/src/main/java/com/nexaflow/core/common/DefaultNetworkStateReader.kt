package com.nexaflow.core.common

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

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
}
