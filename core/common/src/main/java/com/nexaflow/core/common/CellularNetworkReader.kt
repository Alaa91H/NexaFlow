package com.nexaflow.core.common

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.NetworkRegistrationInfo
import android.telephony.ServiceState
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.telephony.SubscriptionManager

/**
 * Reads the generation of the active default-data cellular subscription.
 *
 * The reader deliberately returns null when the generation is unavailable. An
 * unknown read is not the same thing as AUTO: AUTO is a trigger choice that
 * matches any known generation, while null means that no reliable generation
 * was observed.
 */
object CellularNetworkReader {

    const val GENERATION_2G = "2G"
    const val GENERATION_3G = "3G"
    const val GENERATION_4G = "4G"
    const val GENERATION_5G = "5G"
    const val AUTO = "AUTO"

    /**
     * Reads the active default-data subscription. [displayInfo] is supplied by
     * the live telephony callback when available and is especially useful for
     * LTE-anchored 5G NSA networks.
     */
    @SuppressLint("MissingPermission")
    fun read(context: Context, displayInfo: TelephonyDisplayInfo? = null): String? {
        val telephony = telephonyForDefaultDataSubscription(context) ?: return null
        return read(telephony, displayInfo)
    }

    /** Reads a subscription-specific manager without touching the main thread policy. */
    @SuppressLint("MissingPermission")
    fun read(telephony: TelephonyManager, displayInfo: TelephonyDisplayInfo? = null): String? {
        // A display override is the strongest synchronous signal for 5G NSA.
        if (displayInfo?.let(::generationOf) == GENERATION_5G) return GENERATION_5G

        // ServiceState exposes packet-switched NR registration even when the
        // legacy network type remains LTE on an NSA connection.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            runCatching { telephony.serviceState }.getOrNull()?.hasNrPacketRegistration() == true
        ) {
            return GENERATION_5G
        }

        // Prefer the data network type because this reader models the default
        // data SIM, then use the legacy type for older and OEM-specific APIs.
        val dataType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { telephony.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        } else {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }
        generationOf(dataType)?.let { return it }
        generationOf(runCatching { telephony.networkType }
            .getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN))?.let { return it }

        // A non-NR display callback can still carry a more current LTE/3G/2G
        // type than the synchronous fallback on some vendor implementations.
        return displayInfo?.let(::generationOf)
    }

    /** Returns the subscription id currently selected for mobile data, or null. */
    @SuppressLint("MissingPermission")
    fun activeDataSubscriptionId(): Int? {
        // getActiveDataSubscriptionId() is API 30; older devices have no
        // framework-level notion of the active data subscription.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val id = runCatching { SubscriptionManager.getActiveDataSubscriptionId() }
            .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        return id.takeUnless { it == SubscriptionManager.INVALID_SUBSCRIPTION_ID }
    }

    /**
     * Creates a TelephonyManager for the active data SIM. On devices where the
     * subscription cannot be read, the framework's default manager remains a
     * useful fallback for single-SIM devices.
     */
    @SuppressLint("MissingPermission")
    fun telephonyForDefaultDataSubscription(context: Context): TelephonyManager? {
        val base = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return null
        val subId = activeDataSubscriptionId() ?: return base
        return runCatching { base.createForSubscriptionId(subId) }.getOrDefault(base)
    }

    /** Maps a framework network type to the trigger vocabulary. */
    @Suppress("DEPRECATION")
    fun generationOf(networkType: Int): String? = when (networkType) {
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN -> GENERATION_2G

        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> GENERATION_3G

        TelephonyManager.NETWORK_TYPE_LTE,
        TelephonyManager.NETWORK_TYPE_IWLAN -> GENERATION_4G

        TelephonyManager.NETWORK_TYPE_NR -> GENERATION_5G
        else -> null
    }

    /** Maps the platform's display override and reported type to a generation. */
    fun generationOf(displayInfo: TelephonyDisplayInfo): String? {
        // TelephonyDisplayInfo only exists on API 30+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return when (displayInfo.overrideNetworkType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE -> GENERATION_5G
            else -> generationOf(displayInfo.networkType)
        }
    }

    /** AUTO matches known generations only; an unreadable state never matches. */
    fun matchesNetworkMode(desired: String, actual: String?): Boolean =
        actual != null && (desired == AUTO || desired == actual)

    private fun ServiceState.hasNrPacketRegistration(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return networkRegistrationInfoList.orEmpty().any { info ->
            info.domain == NetworkRegistrationInfo.DOMAIN_PS &&
                info.accessNetworkTechnology == ACCESS_NETWORK_TECHNOLOGY_NR
        }
    }

    // NetworkRegistrationInfo.ACCESS_NETWORK_TECHNOLOGY_NR is 20 on API 30+.
    private const val ACCESS_NETWORK_TECHNOLOGY_NR = 20
}
