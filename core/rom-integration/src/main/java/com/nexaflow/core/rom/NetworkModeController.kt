package com.nexaflow.core.rom

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.nexaflow.core.rom.model.SystemControlResult

/**
 * Applies a confirmed preferred cellular-radio profile to the selected active
 * SIM, or preserves the historical all-active-SIM behavior for legacy tasks. A bare `settings put global preferred_network_mode` write is
 * ignored on Android 10+ (the framework reads it once as a default only) and
 * is per-subscription on multi-SIM devices — so this uses the modern
 * `setAllowedNetworkTypesForReason` bitmask API (stable since Android 11, the
 * exact path the system network-mode UI uses) via reflection when the app
 * holds MODIFY_PHONE_STATE / runs as a system app, falls back to the elevated
 * `cmd phone` shell API (Shizuku/root), then to the legacy per-sub
 * `setPreferredNetworkType` ITelephony call, and finally to the
 * per-subscription global setting. Every step is verified by reading the
 * resulting state back, and a step that cannot be confirmed falls through to
 * the next one instead of aborting, so the ladder stays adaptive across ROMs.
 */
class NetworkModeController(
    private val context: Context
) {

    fun setNetworkMode(
        mode: String,
        requestedMask: Long? = null,
        subscriptionId: Int? = null
    ): SystemControlResult {
        val request = requestedMask
            ?.takeIf { it > 0L }
            ?.let(NetworkModePolicy::requestForMask)
            ?: NetworkModePolicy.request(
                mode,
                nrSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            )
        return try {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return SystemControlResult.fail("Telephony service unavailable")
            if (requestedMask != null) {
                val selectedSubId = subscriptionId
                    ?: return SystemControlResult.fail("Dynamic network mode requires a selected SIM subscription")
                val capabilities = NetworkModeCapabilities(context).read()
                val confirmed = capabilities.subscriptions.firstOrNull {
                    it.subscriptionId == selectedSubId
                }
                if (capabilities.status != NetworkModeSnapshot.Status.AVAILABLE || confirmed == null) {
                    return SystemControlResult.fail(
                        "Network capabilities for the selected SIM are no longer readable"
                    )
                }
                if ((request.bitmask and confirmed.selectableMask) != request.bitmask ||
                    confirmed.options.none { it.allowedNetworkTypes == request.bitmask }
                ) {
                    return SystemControlResult.fail(
                        "Saved network mode is no longer supported by the selected SIM"
                    )
                }
            }
            val activeSubIds = activeSubscriptionIds(telephony)
            if (activeSubIds.isEmpty()) {
                return SystemControlResult.fail("No active SIM subscription found")
            }
            val subIds = subscriptionId?.let { requested ->
                if (requested !in activeSubIds) {
                    return SystemControlResult.fail("Selected SIM subscription is no longer active")
                }
                listOf(requested)
            } ?: activeSubIds
            val applied = subIds.map { subId -> setModeForSubscription(telephony, subId, request) }
            val okCount = applied.count { it.first }
            when {
                okCount == applied.size ->
                    SystemControlResult.ok("Network mode set to ${request.label}")
                okCount > 0 ->
                    SystemControlResult.ok("Network mode set on $okCount/${applied.size} subscriptions to ${request.label}")
                else ->
                    SystemControlResult.fail(
                        "Network mode not applied: ${applied.firstNotNullOfOrNull { it.second }}"
                    )
            }
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to set network mode: ${t.message}")
        }
    }

    /**
     * Captures the current user restriction for every readable active
     * subscription. A null result means the platform did not expose a reliable
     * read-back, so restore-original must not pretend it has a value to restore.
     */
    fun captureNetworkModeSnapshot(): String? {
        if (!hasReadPhoneState()) return null
        // Reuse the capability reader so restore-original benefits from the
        // same Root/Shizuku TelephonyShell fallback that made the selected
        // options readable on OEM ROMs which block the app-level API.
        val masks = NetworkModeCapabilities(context).read().subscriptions
            .mapNotNull { subscription ->
                subscription.currentUserMask
                    ?.takeIf { it > 0L }
                    ?.let { subscription.subscriptionId to it }
            }
            .toMap()
        return NetworkModePolicy.encodeSnapshot(masks)
    }

    /** Restores a versioned per-SIM snapshot, preserving legacy string tasks. */
    fun restoreNetworkMode(snapshotOrLegacyMode: String): SystemControlResult {
        val snapshots = NetworkModePolicy.decodeSnapshot(snapshotOrLegacyMode)
            ?: return setNetworkMode(snapshotOrLegacyMode)
        if (!hasReadPhoneState()) {
            return SystemControlResult.fail("Cannot restore per-SIM network modes without phone-state permission")
        }
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return SystemControlResult.fail("Telephony service unavailable")
        val active = activeSubscriptionIds(telephony).toSet()
        val applicable = snapshots.filterKeys { it in active }
        if (applicable.isEmpty()) {
            return SystemControlResult.fail("No captured SIM subscription is active")
        }
        val applied = applicable.map { (subId, mask) ->
            setModeForSubscription(telephony, subId, NetworkModePolicy.requestForMask(mask))
        }
        val count = applied.count { it.first }
        return when {
            count == applicable.size -> SystemControlResult.ok("Original network mode restored")
            count > 0 -> SystemControlResult.ok("Original mode restored on $count/${applicable.size} subscriptions")
            else -> SystemControlResult.fail("Network mode restore failed: ${applied.firstNotNullOfOrNull { it.second }}")
        }
    }

    /**
     * Applies [request] to one subscription through a multi-step ladder, each
     * step verified by a read-back where the platform exposes one. A step that
     * dispatches but cannot be confirmed NEVER aborts the ladder — it records
     * a diagnostic note and falls through, because ROMs disagree on both the
     * write message and the read-back format. Returns (applied, note).
     */
    @SuppressLint("MissingPermission")
    private fun setModeForSubscription(
        telephony: TelephonyManager,
        subId: Int,
        request: NetworkModePolicy.Request
    ): Pair<Boolean, String> {
        val label = "sub$subId"
        val notes = mutableListOf<String>()

        // 1) Modern subscription-scoped bitmask API (Android 13+). The
        //    TelephonyManager instance must be pinned to the subscription;
        //    passing a subId as a hidden extra argument is not Android's API
        //    signature and silently prevents the modern path from working.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val scoped = telephony.createForSubscriptionId(subId)
            val dispatched = runCatching {
                scoped.setAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                    request.bitmask
                )
                true
            }.getOrDefault(false)
            if (dispatched) {
                val confirmed = runCatching {
                    NetworkModePolicy.matches(
                        request.bitmask,
                        scoped.getAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
                        )
                    )
                }.getOrDefault(false)
                if (confirmed) {
                    return true to "$label via allowed-network-types"
                }
                notes += "$label via allowed-network-types (unconfirmed read-back)"
            }
        }
        // 2) Elevated shell: the `cmd phone` API (Android 14+ TelephonyShell-
        //    Command). NOTE: on AOSP the SET command prints
        //    "set-allowed-network-types-for-users completed" OR "... failed"
        //    and returns exit code 0 in BOTH cases, so the exit code alone
        //    cannot prove the write took effect — the message is checked too,
        //    and the write is always confirmed with a read-back. The `-s`
        //    slot flag is optional across ROMs, so each variant is attempted
        //    and the first one that both reports success and confirms wins.
        if (PrivilegedRunner.isShizukuGranted() || PrivilegedRunner.isRootAvailable()) {
            val variants = setCommandVariants(slotIndexFor(subId))
            for (variant in variants) {
                val binary = java.lang.Long.toString(request.bitmask, 2)
                val set = PrivilegedRunner.runShell(
                    "cmd phone set-allowed-network-types-for-users $variant$binary"
                )
                if (set.success && !shellReportedFailure(set.message)) {
                    val readBack = PrivilegedRunner.runShell(
                        "cmd phone get-allowed-network-types-for-users $variant"
                    ).message
                    if (NetworkModePolicy.coversReadBack(readBack, request)) {
                        return true to "$label via cmd phone"
                    }
                    notes += "$label via cmd phone (write ok, read-back unconfirmed)"
                } else {
                    notes += "$label via cmd phone (${set.message.trim().take(80)})"
                }
            }
        }

        // 3) Legacy ITelephony call (void return — only dispatch is observable).
        //    Dynamic profiles intentionally skip this path: PhoneConstants
        //    cannot represent every modern NR/TD-SCDMA/carrier combination.
        if (request.legacyCompatible) {
            val legacy = reflectTelephony(
                telephony,
                "setPreferredNetworkType",
                arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
                subId, request.legacyInt
            )
            if (legacy.dispatched) {
                val readBack = reflectTelephony(
                    telephony, "getPreferredNetworkType", arrayOf(Int::class.javaPrimitiveType!!), subId
                )
                val confirmed = readBack.dispatched && readBack.value is Int &&
                    readBack.value == request.legacyInt
                if (confirmed) {
                    return true to "$label via preferred-network-type"
                }
                notes += "$label via preferred-network-type (unconfirmed read-back)"
            }
        }

        // 4) Elevated shell: per-subscription key (multi-SIM) plus the plain
        //    default key, verified through Settings.Global. Last resort — the
        //    framework ignores this at runtime on Android 10+, but some OEM
        //    builds still honor it, and it costs nothing to try.
        if (request.legacyCompatible &&
            (PrivilegedRunner.isShizukuGranted() || PrivilegedRunner.isRootAvailable())
        ) {
            val subKey = "preferred_network_mode$subId"
            val shell = PrivilegedRunner.runShell(
                "settings put global $subKey ${request.legacyInt} && " +
                    "settings put global preferred_network_mode ${request.legacyInt}"
            )
            if (shell.success) {
                val stored = runCatching {
                    Settings.Global.getInt(context.contentResolver, subKey, -1)
                }.getOrDefault(-1)
                if (stored == request.legacyInt) {
                    return true to "$label via settings"
                }
                notes += "$label via settings (value not persisted)"
            } else {
                notes += "$label via settings (${shell.message.trim().take(80)})"
            }
        }

        return false to (notes.firstOrNull() ?: "$label rejected by the radio")
    }
    /**
     * The `-s` slot flag variants to try for the `cmd phone` commands. The
     * flag is understood on AOSP 14+ and most OEM builds; older or customized
     * builds may reject it or require it, so both forms are attempted and the
     * first confirming write wins.
     */
    private fun setCommandVariants(slot: Int): List<String> = when {
        slot >= 0 -> listOf("-s $slot ", "")
        else -> listOf("")
    }

    /** AOSP prints "... completed"/"... failed" and exits 0 in both cases. */
    private fun shellReportedFailure(message: String): Boolean {
        val m = message.uppercase()
        return m.contains("FAILED") || m.contains("ERROR") || m.contains("EXCEPTION") ||
            m.contains("NO VALID") || m.contains("UNKNOWN OPTION") || m.contains("NO SUCH")
    }

    /** Maps a subscription id back to its physical SIM slot (-1 when unknown). */
    private fun slotIndexFor(subId: Int): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        return runCatching { SubscriptionManager.getSlotIndex(subId) }
            .getOrDefault(-1)
    }

    private fun hasReadPhoneState(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    /** Active subscription ids, falling back to the primary slot id (or 0). */
    private fun activeSubscriptionIds(telephony: TelephonyManager): List<Int> {
        val ids = if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                context.getSystemService(SubscriptionManager::class.java)
                    ?.activeSubscriptionInfoList
                    .orEmpty()
                    .map { it.subscriptionId }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        if (ids.isNotEmpty()) return ids.distinct()
        // TelephonyManager#getSubscriptionId was only added in API 30.
        val subId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { telephony.subscriptionId }
                .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        } else {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
        val safe = if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) 0 else subId
        return listOf(safe)
    }

    private class ReflectCall(val dispatched: Boolean, val value: Any?)

    /**
     * Invokes a hidden TelephonyManager method with its exact AOSP primitive
     * signature. [ReflectCall.dispatched] is true only when the method was
     * found and invoked — a blocked hidden API or a missing overload both
     * surface as non-dispatched and move the ladder down. `getMethod` only
     * resolves public members; hidden APIs are policed at lookup time by the
     * runtime, not by Java accessibility, so no setAccessible is needed.
     */
    private fun reflectTelephony(
        instance: Any,
        method: String,
        argTypes: Array<Class<*>>,
        vararg args: Any?
    ): ReflectCall {
        return try {
            val m = instance.javaClass.getMethod(method, *argTypes)
            ReflectCall(true, m.invoke(instance, *args))
        } catch (_: Throwable) {
            ReflectCall(false, null)
        }
    }
}
