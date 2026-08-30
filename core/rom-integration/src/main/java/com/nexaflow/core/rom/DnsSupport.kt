package com.nexaflow.core.rom

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build
import android.provider.Settings

/** Read-only snapshot of the DNS state Android exposes for active network links. */
data class CurrentDnsState(
    val privateDnsMode: PrivateDnsMode,
    val privateDnsHostname: String?,
    val privateDnsActive: Boolean,
    val dnsServers: List<String>,
    val networksRead: Int,
    val readError: String? = null
)

/** A provider profile that can be used as a Private DNS hostname request. */
data class DnsProviderProfile(
    val id: String,
    val displayName: String,
    val hostname: String,
    val source: DnsProviderSource = DnsProviderSource.BUILT_IN
)

enum class DnsProviderSource { BUILT_IN, ROM_EXPOSED, CURRENT }

/**
 * Safe provider profiles. Hostnames are used instead of IP addresses because
 * Android Private DNS is DNS-over-TLS and requires a provider hostname in strict
 * mode. ROM-exposed entries are appended only when the ROM publishes a string
 * array under a known resource name; unknown resources are ignored.
 */
object DnsProviderCatalog {
    private val builtIns = listOf(
        DnsProviderProfile("cloudflare", "Cloudflare", "one.one.one.one"),
        DnsProviderProfile("google", "Google", "dns.google"),
        DnsProviderProfile("quad9", "Quad9", "dns.quad9.net"),
        DnsProviderProfile("adguard", "AdGuard", "dns.adguard-dns.com"),
        DnsProviderProfile("cleanbrowsing", "CleanBrowsing Security", "security-filter-dns.cleanbrowsing.org")
    )

    fun all(context: Context? = null, currentHostname: String? = null): List<DnsProviderProfile> {
        val profiles = LinkedHashMap<String, DnsProviderProfile>()
        builtIns.forEach { profiles[it.hostname] = it }
        context?.let { readRomHostnames(it).forEach { hostname ->
            profiles.putIfAbsent(
                hostname,
                DnsProviderProfile(
                    id = "rom-${hostname.hashCode().toUInt().toString(16)}",
                    displayName = hostname,
                    hostname = hostname,
                    source = DnsProviderSource.ROM_EXPOSED
                )
            )
        } }
        currentHostname?.trim()?.lowercase()?.takeIf(PrivateDnsPolicy::isValidHostname)?.let { hostname ->
            profiles.putIfAbsent(
                hostname,
                DnsProviderProfile("current", "Current provider", hostname, DnsProviderSource.CURRENT)
            )
        }
        return profiles.values.toList()
    }

    private fun readRomHostnames(context: Context): List<String> {
        val resources = context.resources
        val packageName = "android"
        val resourceNames = listOf(
            "config_privateDnsProviderHostnames",
            "config_privateDnsProviderNames",
            "config_dnsResolverProviders"
        )
        return resourceNames.flatMap { name ->
            val id = resources.getIdentifier(name, "array", packageName)
            if (id == 0) emptyList() else runCatching {
                resources.getStringArray(id).toList()
            }.getOrDefault(emptyList())
        }.mapNotNull { value ->
            value.trim().lowercase().takeIf(PrivateDnsPolicy::isValidHostname)
        }.distinct()
    }
}

/** Read-only DNS inspection through public Android networking APIs. */
object DnsStateReader {
    @SuppressLint("MissingPermission")
    fun read(context: Context): CurrentDnsState {
        return runCatching {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return CurrentDnsState(
                    privateDnsMode = PrivateDnsMode.AUTOMATIC,
                    privateDnsHostname = null,
                    privateDnsActive = false,
                    dnsServers = emptyList(),
                    networksRead = 0,
                    readError = "Connectivity service unavailable"
                )
            val links = activeLinks(connectivity)
            val dnsServers = links.flatMap { link ->
                link.dnsServers.mapNotNull { it.hostAddress?.trim()?.takeIf(String::isNotEmpty) }
            }.distinct()
            val strictHostname = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                links.asSequence()
                    .mapNotNull { it.privateDnsServerName?.trim()?.lowercase() }
                    .firstOrNull { PrivateDnsPolicy.isValidHostname(it) }
            } else {
                null
            }
            val privateDnsActive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                links.any { it.isPrivateDnsActive }
            val configuredMode = runCatching {
                Settings.Global.getString(context.contentResolver, PrivateDnsPolicy.MODE_KEY)
            }.getOrNull()
            val mode = PrivateDnsMode.fromConfig(configuredMode) ?: when {
                strictHostname != null -> PrivateDnsMode.HOSTNAME
                privateDnsActive -> PrivateDnsMode.AUTOMATIC
                else -> PrivateDnsMode.OFF
            }
            CurrentDnsState(
                privateDnsMode = mode,
                privateDnsHostname = strictHostname,
                privateDnsActive = privateDnsActive,
                dnsServers = dnsServers,
                networksRead = links.size
            )
        }.getOrElse { failure ->
            CurrentDnsState(
                privateDnsMode = PrivateDnsMode.AUTOMATIC,
                privateDnsHostname = null,
                privateDnsActive = false,
                dnsServers = emptyList(),
                networksRead = 0,
                readError = failure.message ?: failure.javaClass.simpleName
            )
        }
    }

    private fun activeLinks(connectivity: ConnectivityManager): List<LinkProperties> {
        val networks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            listOfNotNull(connectivity.activeNetwork)
        } else {
            @Suppress("DEPRECATION")
            connectivity.allNetworks.filter { network ->
                connectivity.getNetworkInfo(network)?.isConnected == true
            }
        }
        return networks.mapNotNull { network ->
            runCatching { connectivity.getLinkProperties(network) }.getOrNull()
        }
    }
}
