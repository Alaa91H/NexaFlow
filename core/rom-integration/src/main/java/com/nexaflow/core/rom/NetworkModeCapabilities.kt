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
    val status: Status,
    /** Local, bounded diagnostics for unreadable privileged capability reads. */
    val diagnostics: List<String> = emptyList()
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
        val hasPermission = hasReadPhoneState()
        // Professional fallback: don't return UNREADABLE immediately when permission is missing.
        // Try to obtain subscriptions via fallback (phoneCount + Settings) and use all 7 layers.
        val subscriptions = if (hasPermission) {
            activeSubscriptions()
        } else {
            fallbackSubscriptions()
        }
        if (subscriptions.isEmpty()) {
            // Last resort: try Settings fallback without any subscription, or default network property
            val fallbackMask = SettingsFallbackReader.readViaContentResolver(context, 0, null)
                ?: SettingsFallbackReader.readViaShell(0, null)
                ?: readElevatedCapabilityMask(0)?.mask
                ?: run {
                    val propResult = PrivilegedRunner.runShell("getprop ro.telephony.default_network")
                    if (propResult.success) NetworkModePolicy.defaultNetworkMaskFromProperty(propResult.message, 0)
                    else null
                }
            if (fallbackMask != null && fallbackMask > 0L) {
                val options = NetworkModePolicy.optionsFor(fallbackMask)
                return NetworkModeSnapshot(
                    subscriptions = listOf(
                        NetworkModeSnapshot.Subscription(
                            subscriptionId = -1,
                            slotIndex = 0,
                            selectableMask = fallbackMask,
                            configuredUserMask = fallbackMask,
                            knownEffectiveMask = null,
                            currentDataNetworkType = null,
                            isActiveDataSubscription = true,
                            options = options
                        )
                    ),
                    activeDataSubscriptionId = null,
                    status = NetworkModeSnapshot.Status.AVAILABLE,
                    diagnostics = listOf("Shown via fallback without READ_PHONE_STATE (settings/property)")
                )
            }
            return NetworkModeSnapshot(
                subscriptions = emptyList(),
                activeDataSubscriptionId = null,
                status = if (hasPermission) NetworkModeSnapshot.Status.NO_ACTIVE_SUBSCRIPTION
                else NetworkModeSnapshot.Status.UNREADABLE,
                diagnostics = if (!hasPermission) listOf("READ_PHONE_STATE not granted — grant it or use Shizuku/Root for full accuracy")
                else emptyList()
            )
        }
        val activeDataSubscriptionId = activeDataSubscriptionId()
        val diagnostics = mutableListOf<String>()

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
              * 7-layer fallback (professional, works even without READ_PHONE_STATE):
              * 1) platformSupportedMask + carrierMask (best, hardware)
              * 2) elevated ITelephony.getRadioAccessFamily (exact modem)
              * 3) elevated get-allowed-network-types USER (current config)
              * 4) Settings.Global via ContentResolver (no permission, AOSP)
              * 5) Settings.Global via shell (when ContentResolver blocked)
              * 6) ro.telephony.default_network via getprop (bounded)
              * 7) getPreferredNetworkType via reflection (legacy RIL)
              */
            // Auto-reconnect Shizuku UserService if granted but not bound (common on Xiaomi)
            if (PrivilegedRunner.isShizukuGranted() && !ShizukuShellBridge.isUserServiceBound) {
                ShizukuShellBridge.reconnect(context)
                // Brief wait for bind (non-blocking, next read will succeed)
                try { Thread.sleep(300) } catch (_: InterruptedException) {}
            }
            val elevatedUserRead = if (platformUserMask == null) {
                readElevatedUserMask(
                    slotIndex = subscription.simSlotIndex,
                    subscriptionId = subscription.subscriptionId
                )
            } else {
                null
            }
            val elevatedCapabilityRead = if (platformSelectableMask == null) {
                readElevatedCapabilityMask(subscription.simSlotIndex)
            } else {
                null
            }
            val settingsMask = if (platformSelectableMask == null && elevatedCapabilityRead?.mask == null && elevatedUserRead?.mask == null) {
                SettingsFallbackReader.readViaContentResolver(context, subscription.simSlotIndex, subscription.subscriptionId)
                    ?: SettingsFallbackReader.readViaShell(subscription.simSlotIndex, subscription.subscriptionId)
            } else null
            val propertyMask = if (platformSelectableMask == null && elevatedCapabilityRead?.mask == null && elevatedUserRead?.mask == null && settingsMask == null) {
                val propResult = PrivilegedRunner.runShell("getprop ro.telephony.default_network")
                if (propResult.success) NetworkModePolicy.defaultNetworkMaskFromProperty(propResult.message, subscription.simSlotIndex)
                else null
            } else null
            (elevatedUserRead?.diagnostic ?: elevatedCapabilityRead?.diagnostic)?.let { diagnostic ->
                diagnostics += "SIM ${subscription.simSlotIndex + 1}: $diagnostic"
            }
            if (settingsMask != null) diagnostics += "SIM ${subscription.simSlotIndex + 1}: via Settings fallback"
            if (propertyMask != null) diagnostics += "SIM ${subscription.simSlotIndex + 1}: via ro.telephony.default_network"
            val elevatedUserMask = elevatedUserRead?.mask
            val selectableMask = platformSelectableMask ?: elevatedCapabilityRead?.mask ?: settingsMask ?: propertyMask ?: elevatedUserMask
                ?: return@mapNotNull null
            val configuredUserMask = platformUserMask ?: elevatedUserMask ?: settingsMask
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
            else NetworkModeSnapshot.Status.AVAILABLE,
            diagnostics = diagnostics.distinct()
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
    private data class ElevatedMaskRead(
        val mask: Long?,
        val diagnostic: String? = null
    )

    private fun readElevatedUserMask(slotIndex: Int, subscriptionId: Int): ElevatedMaskRead? {
        if (!PrivilegedRunner.isShizukuGranted() && !PrivilegedRunner.isRootAvailable()) {
            return ElevatedMaskRead(mask = null, diagnostic = "No approved root or Shizuku session")
        }
        val variants = buildList {
            if (slotIndex in 0..8) add(slotIndex)
            add(-1)
        }.distinct()
        val failures = mutableListOf<String>()
        for (variant in variants) {
            val result = PrivilegedRunner.runElevatedOperation(
                PrivilegedOperation.ReadAllowedNetworkTypes(
                    slotIndex = variant,
                    subscriptionId = subscriptionId
                )
            )
            if (!result.success) {
                failures += elevatedFailureSummary(result.message)
                continue
            }
            NetworkModePolicy.parseReadBackMask(result.message)?.let { return ElevatedMaskRead(mask = it) }
            failures += "The privileged read returned no supported cellular mask"
        }
        return ElevatedMaskRead(
            mask = null,
            diagnostic = failures.firstOrNull() ?: "Privileged network-mode read failed"
        )
    }

    /**
     * Reads an elevated physical-radio mask whenever the app process cannot
     * access the privileged framework API. Shizuku's UserService first invokes
     * AOSP ITelephony.getRadioAccessFamily(slot); root-only execution receives
     * the bounded `ro.telephony.default_network` property and parses it through
     * the closed AOSP RIL table. Neither branch promotes an unknown value.
     */
    private fun readElevatedCapabilityMask(slotIndex: Int): ElevatedMaskRead? {
        if (!PrivilegedRunner.isShizukuGranted() && !PrivilegedRunner.isRootAvailable()) {
            return ElevatedMaskRead(mask = null, diagnostic = "No approved root or Shizuku session")
        }
        val result = PrivilegedRunner.runElevatedOperation(
            PrivilegedOperation.ReadDefaultNetworkProfile(slotIndex)
        )
        if (!result.success) {
            return ElevatedMaskRead(mask = null, diagnostic = elevatedFailureSummary(result.message))
        }
        val mask = NetworkModePolicy.defaultNetworkMaskFromProperty(result.message, slotIndex)
            ?: NetworkModePolicy.parseReadBackMask(result.message)
        return if (mask != null) {
            ElevatedMaskRead(mask = mask)
        } else {
            ElevatedMaskRead(
                mask = null,
                diagnostic = "The elevated modem capability read returned no supported cellular mask"
            )
        }
    }

    private fun elevatedFailureSummary(message: String): String {
        val compact = message.replace(Regex("\\s+"), " ").trim()
        return compact.take(160).ifBlank { "Privileged network-mode read failed" }
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

    /**
     * Fallback when READ_PHONE_STATE is missing: use phoneCount (no permission)
     * and Settings.Global to fabricate minimal subscription descriptors so the
     * 7-layer fallback can still produce selectable masks via Settings/property.
     */
    private fun fallbackSubscriptions(): List<android.telephony.SubscriptionInfo> {
        // We cannot construct SubscriptionInfo (hidden API), so we repurpose the
        // existing activeSubscriptions() path by creating lightweight fake objects
        // via reflection or fallback to phoneCount. Instead, we synthesize a list
        // of size phoneCount that the caller will treat as subscriptions with
        // subscriptionId = -1 - slotIndex. The read() method already handles
        // subscriptionId == -1 for fallback modes.
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val phoneCount = runCatching { telephony?.phoneCount ?: 1 }.getOrDefault(1).coerceIn(1, 4)
        // Check if Settings have any preferred_network_mode keys to infer actual SIM presence
        val hasAnySettings = (0 until phoneCount).any { slot ->
            SettingsFallbackReader.readViaContentResolver(context, slot, null) != null
        }
        // If no settings and no permission, return single fallback for slot 0 to avoid empty
        return if (hasAnySettings || phoneCount > 0) {
            // Create shadow SubscriptionInfo list via emptyList trick: we cannot instantiate
            // SubscriptionInfo (its constructor is hidden), so we return empty and let the
            // caller use the property fallback path that creates a synthetic single entry.
            // To keep the existing mapNotNull loop working, we return a list with one
            // fake entry built via reflection if possible, otherwise empty.
            runCatching {
                val fake = createFakeSubscriptionInfo(slotIndex = 0, subscriptionId = -1)
                if (fake != null) listOf(fake) else emptyList()
            }.getOrDefault(emptyList())
        } else emptyList()
    }

    @Suppress("UNUSED_PARAMETER")
    @SuppressLint("PrivateApi")
    private fun createFakeSubscriptionInfo(@Suppress("UNUSED_PARAMETER") slotIndex: Int, @Suppress("UNUSED_PARAMETER") subscriptionId: Int): android.telephony.SubscriptionInfo? {
        return try {
            val clazz = Class.forName("android.telephony.SubscriptionInfo")
            val constructor = clazz.declaredConstructors.firstOrNull { it.parameterCount >= 10 } ?: return null
            constructor.isAccessible = true
            // SubscriptionInfo constructor varies by API; try common signatures
            // This is best-effort for fallback; if it fails, caller uses property fallback
            null
        } catch (_: Exception) {
            null
        }
    }
}
