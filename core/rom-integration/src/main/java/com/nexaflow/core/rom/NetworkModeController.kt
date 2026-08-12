package com.nexaflow.core.rom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.nexaflow.core.rom.model.SystemControlResult

/**
 * Forces the preferred cellular network generation (2G/3G/4G/5G) on every
 * active SIM. A bare `settings put global preferred_network_mode` write is
 * ignored on Android 10+ (the framework reads it once as a default only) and
 * is per-subscription on multi-SIM devices — so this uses the modern
 * `setAllowedNetworkTypesForReason` bitmask API (stable since Android 11, the
 * exact path the system network-mode UI uses) via reflection when the app
 * holds MODIFY_PHONE_STATE / runs as a system app, falls back to the legacy
 * per-sub `setPreferredNetworkType` ITelephony call, then to the
 * per-subscription global setting through the elevated shell. Each step is
 * verified by reading the resulting state back.
 */
class NetworkModeController(
    private val context: Context
) {

    fun setNetworkMode(mode: String): SystemControlResult {
        val request = NetworkModePolicy.request(
            mode,
            nrSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )
        return try {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return SystemControlResult.fail("Telephony service unavailable")
            val subIds = activeSubscriptionIds(telephony)
            if (subIds.isEmpty()) {
                return SystemControlResult.fail("No active SIM subscription found")
            }
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
     * Applies [request] to one subscription through a three-step ladder, each
     * verified by a read-back where the platform exposes one. Returns
     * (applied, diagnostic note).
     */
    private fun setModeForSubscription(
        telephony: TelephonyManager,
        subId: Int,
        request: NetworkModePolicy.Request
    ): Pair<Boolean, String> {
        val label = "sub$subId"
        // 1) Modern per-subscription bitmask API (Android 11+). Reflection is
        //    exempt for system/privileged installs; a blocked hidden API
        //    simply reports not-dispatched and falls through.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val call = reflectTelephony(
                telephony,
                "setAllowedNetworkTypesForReason",
                arrayOf(
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                    Long::class.javaPrimitiveType!!
                ),
                subId, TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER, request.bitmask
            )
            if (call.dispatched && call.value == true) {
                val readBack = reflectTelephony(
                    telephony,
                    "getAllowedNetworkTypesForReason",
                    arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
                    subId, TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
                )
                val confirmed = readBack.dispatched && readBack.value is Long &&
                    NetworkModePolicy.covers(request.bitmask, readBack.value)
                return confirmed to "$label via allowed-network-types"
            }
        }
        // 2) Legacy ITelephony call (void return — only dispatch is observable).
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
            return confirmed to "$label via preferred-network-type"
        }
        // 3) Elevated shell: per-subscription key (multi-SIM) plus the plain
        //    default key, verified through Settings.Global.
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
        }
        return false to "$label rejected by the radio (requires MODIFY_PHONE_STATE or a system install)"
    }

    /** Active subscription ids, falling back to the primary slot id (or 0). */
    @Suppress("DEPRECATION")
    private fun activeSubscriptionIds(telephony: TelephonyManager): List<Int> {
        val granted = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val ids = if (granted) {
            runCatching {
                SubscriptionManager.from(context).activeSubscriptionInfoList
                    ?.map { it.subscriptionId }.orEmpty()
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
