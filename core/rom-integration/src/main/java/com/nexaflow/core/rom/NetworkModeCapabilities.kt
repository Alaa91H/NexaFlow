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
    /** The data subscription Android currently reports, when the platform exposes one. */
    val activeDataSubscriptionId: Int?,
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
        /** Confirmed hardware/carrier mask used to construct selectable profiles. */
        val selectableMask: Long,
        /** The USER allowed-network-types reason; this is configuration, not the live RAT. */
        val configuredUserMask: Long?,
        /** The readable USER ∩ CARRIER restriction. Null means no complete read-back is available. */
        val knownEffectiveMask: Long?,
        /** The radio technology currently reported for this subscription's packet data, if readable. */
        val currentDataNetworkType: Int?,
        val isActiveDataSubscription: Boolean,
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
            return NetworkModeSnapshot(
                subscriptions = emptyList(),
                activeDataSubscriptionId = null,
                status = NetworkModeSnapshot.Status.NO_TELEPHONY
            )
        }
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return NetworkModeSnapshot(
                subscriptions = emptyList(),
                activeDataSubscriptionId = null,
                status = NetworkModeSnapshot.Status.NO_TELEPHONY
            )
        if (!hasReadPhoneState()) {
            return NetworkModeSnapshot(
                subscriptions = emptyList(),
                activeDataSubscriptionId = null,
                status = NetworkModeSnapshot.Status.UNREADABLE
            )
        }
        val subscriptions = activeSubscriptions()
        if (subscriptions.isEmpty()) {
            return NetworkModeSnapshot(
                subscriptions = emptyList(),
                activeDataSubscriptionId = null,
                status = NetworkModeSnapshot.Status.NO_ACTIVE_SUBSCRIPTION
            )
        }
        val activeDataSubscriptionId = activeDataSubscriptionId()

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
            val elevatedUserMask = if (platformSelectableMask == null || platformUserMask == null) {
                readElevatedUserMask(
                    slotIndex = subscription.simSlotIndex,
                    subscriptionId = subscription.subscriptionId
                )
            } else {
                null
            }
            val selectableMask = platformSelectableMask ?: elevatedUserMask
                ?: return@mapNotNull null
            val configuredUserMask = platformUserMask ?: elevatedUserMask
            // Android applies the intersection of every active reason. The app
            // can only report an effective mask when both USER and CARRIER are
            // readable; other reasons remain intentionally undisclosed instead
            // of being guessed from the current RAT.
            val knownEffectiveMask = NetworkModePolicy.effectiveMask(
                userMask = platformUserMask,
                carrierMask = carrierMask
            )

            NetworkModeSnapshot.Subscription(
                subscriptionId = subscription.subscriptionId,
                slotIndex = subscription.simSlotIndex,
                selectableMask = selectableMask,
                configuredUserMask = configuredUserMask,
                knownEffectiveMask = knownEffectiveMask,
                currentDataNetworkType = readCurrentDataNetworkType(scoped),
                isActiveDataSubscription = subscription.subscriptionId == activeDataSubscriptionId,
                options = NetworkModePolicy.optionsFor(selectableMask)
            )
        }

        return NetworkModeSnapshot(
            subscriptions = snapshots,
            activeDataSubscriptionId = activeDataSubscriptionId,
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
     * Reads the selected user's allowed network types through the reviewed
     * Shizuku/Root operation. Both slot-scoped and default forms are tried
     * because AOSP and OEM TelephonyShell implementations differ. A successful
     * call with unparsable or zero output remains unavailable rather than
     * becoming an invented capability list.
     */
    private fun readElevatedUserMask(slotIndex: Int, subscriptionId: Int): Long? {
        if (!PrivilegedRunner.isShizukuGranted() && !PrivilegedRunner.isRootAvailable()) return null
        val variants = buildList {
            if (slotIndex in 0..8) add(slotIndex)
            add(-1)
        }.distinct()
        for (variant in variants) {
            val result = PrivilegedRunner.runElevatedOperation(
                PrivilegedOperation.ReadAllowedNetworkTypes(
                    slotIndex = variant,
                    subscriptionId = subscriptionId
                )
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

    /**
     * Uses the Android 11 active-data identity when available and the older
     * default-data identity otherwise. Both are subscription ids, never slots.
     */
    private fun activeDataSubscriptionId(): Int? {
        val id = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { SubscriptionManager.getActiveDataSubscriptionId() }
                .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        } else {
            runCatching { SubscriptionManager.getDefaultDataSubscriptionId() }
                .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        }
        return id.takeUnless { it == SubscriptionManager.INVALID_SUBSCRIPTION_ID }
    }

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
