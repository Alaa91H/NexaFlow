package com.nexaflow.core.rom

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/**
 * Read-only network-mode capability snapshot.
 *
 * Android does not expose one universal, public "preferred network mode menu":
 * available choices are constrained by modem hardware, carrier policy, the
 * active subscription, and OEM code. This model therefore separates the
 * confirmed hardware/carrier-supported mask from the currently configured user
 * mask. The UI must only render [options] derived from [selectableMask].
 */
data class NetworkModeSnapshot(
    val subscriptions: List<Subscription>,
    val status: Status
) {
    enum class Status {
        AVAILABLE,
        NO_TELEPHONY,
        NO_ACTIVE_SUBSCRIPTION,
        UNREADABLE
    }

    data class Subscription(
        val subscriptionId: Int,
        val slotIndex: Int,
        val selectableMask: Long,
        val currentUserMask: Long?,
        val currentDataNetworkType: Int?,
        val options: List<NetworkModePolicy.Option>
    )
}

/**
 * Reads network capabilities from Android's subscription-scoped telephony
 * service. Every platform call is best-effort: reading privileged telephony
 * state is intentionally blocked on many stock ROMs, which must result in an
 * explicit [NetworkModeSnapshot.Status.UNREADABLE] state rather than guessed
 * generations.
 */
class NetworkModeCapabilities(private val context: Context) {

    fun read(): NetworkModeSnapshot {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return NetworkModeSnapshot(emptyList(), NetworkModeSnapshot.Status.NO_TELEPHONY)
        }
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return NetworkModeSnapshot(emptyList(), NetworkModeSnapshot.Status.NO_TELEPHONY)
        if (!hasReadPhoneState()) {
            return NetworkModeSnapshot(emptyList(), NetworkModeSnapshot.Status.UNREADABLE)
        }
        val subscriptions = activeSubscriptions()
        if (subscriptions.isEmpty()) {
            return NetworkModeSnapshot(emptyList(), NetworkModeSnapshot.Status.NO_ACTIVE_SUBSCRIPTION)
        }

        val snapshots = subscriptions.mapNotNull { subscription ->
            val scoped = telephony.createForSubscriptionId(subscription.subscriptionId)
            val supportedMask = readSupportedMask(scoped)
            val carrierMask = readAllowedMask(scoped, TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER)
            val platformSelectableMask = supportedMask?.let { supported ->
                // A readable carrier restriction is authoritative. When it is
                // unavailable, retain only confirmed hardware support.
                carrierMask?.let { carrier -> supported and carrier } ?: supported
            }?.and(NetworkModePolicy.BITMASK_SELECTABLE_CELLULAR)
                ?.takeIf { it > 0L }
            val platformUserMask = readAllowedMask(
                scoped,
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
            )?.and(NetworkModePolicy.BITMASK_SELECTABLE_CELLULAR)
                ?.takeIf { it > 0L }

            /*
             * READ_PHONE_STATE grants the app its runtime permission but does
             * not grant READ_PRIVILEGED_PHONE_STATE. Samsung and other OEM
             * telephony stacks commonly reserve supportedRadioAccessFamily for
             * the latter, so the public read above can still fail. When a live
             * Root/Shizuku bridge exists, use TelephonyShell's per-user mask as
             * a conservative fallback: it is a real value returned by the
             * device, and only its subsets are offered. It is never combined
             * with an invented universal 2G/3G/4G/5G list.
             */
            val elevatedUserMask = if (platformSelectableMask == null) {
                readElevatedUserMask(subscription.simSlotIndex)
            } else {
                null
            }
            val selectableMask = platformSelectableMask ?: elevatedUserMask
                ?: return@mapNotNull null
            val currentUserMask = platformUserMask ?: elevatedUserMask

            NetworkModeSnapshot.Subscription(
                subscriptionId = subscription.subscriptionId,
                slotIndex = subscription.simSlotIndex,
                selectableMask = selectableMask,
                currentUserMask = currentUserMask,
                currentDataNetworkType = readCurrentDataNetworkType(scoped),
                options = NetworkModePolicy.optionsFor(selectableMask)
            )
        }

        return NetworkModeSnapshot(
            subscriptions = snapshots,
            status = if (snapshots.isEmpty()) NetworkModeSnapshot.Status.UNREADABLE
            else NetworkModeSnapshot.Status.AVAILABLE
        )
    }

    @SuppressLint("MissingPermission")
    private fun readSupportedMask(telephony: TelephonyManager): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        // This public API is restricted to privileged phone-state callers on
        // many Android builds. A SecurityException is intentionally converted
        // to null so the caller exposes UNREADABLE rather than guessing modes.
        return runCatching { telephony.supportedRadioAccessFamily }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun readAllowedMask(telephony: TelephonyManager, reason: Int): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        // Stock Android often restricts this read to privileged callers. Treat
        // denial as unavailable capability rather than assuming a radio mask.
        return runCatching { telephony.getAllowedNetworkTypesForReason(reason) }.getOrNull()
    }

    /**
     * Reads the selected user's allowed network types through TelephonyShell.
     * The command accepts either a slot-scoped form or a default-subscription
     * form across Android/OEM variants, so both are tried. A successful command
     * with unparsable/zero output remains unavailable rather than becoming a
     * guessed capability.
     */
    private fun readElevatedUserMask(slotIndex: Int): Long? {
        if (!PrivilegedRunner.isShizukuGranted() && !PrivilegedRunner.isRootAvailable()) return null
        val variants = if (slotIndex >= 0) listOf("-s $slotIndex ", "") else listOf("")
        for (variant in variants) {
            val result = PrivilegedRunner.runShell(
                "cmd phone get-allowed-network-types-for-users $variant"
            )
            if (!result.success) continue
            NetworkModePolicy.parseReadBackMask(result.message)?.let { return it }
        }
        return null
    }

    private fun readCurrentDataNetworkType(telephony: TelephonyManager): Int? {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        return runCatching { telephony.dataNetworkType }.getOrNull()
    }

    private fun hasReadPhoneState(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    private fun activeSubscriptions(): List<android.telephony.SubscriptionInfo> {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        return runCatching {
            context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList
                .orEmpty()
        }.getOrDefault(emptyList())
    }
}
