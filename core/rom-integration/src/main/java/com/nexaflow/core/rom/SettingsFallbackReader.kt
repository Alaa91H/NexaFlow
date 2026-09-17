package com.nexaflow.core.rom

import android.content.Context
import android.provider.Settings

/**
 * Fallback reader for network mode via Settings.Global — works without
 * READ_PHONE_STATE and without root/Shizuku on most AOSP-based ROMs
 * (including Evolution X). This is the layer that makes network-mode
 * picker usable even in the worst case: no permission, no elevated runtime.
 */
object SettingsFallbackReader {

    /**
     * Tries to read preferred network mode from Settings.Global via
     * ContentResolver (no permission needed for reads on AOSP). Tries
     * multiple key variants for slot/subId compatibility.
     *
     * Returns a bitmask (already filtered to BITMASK_SELECTABLE_CELLULAR) or null.
     */
    fun readViaContentResolver(context: Context, slotIndex: Int, subscriptionId: Int?): Long? {
        val candidates = buildList {
            subscriptionId?.takeIf { it >= 0 }?.let {
                add("preferred_network_mode$it")
                add("preferred_network_mode_$it")
            }
            add("preferred_network_mode$slotIndex")
            add("preferred_network_mode_$slotIndex")
            add("preferred_network_mode")
            // Legacy per-slot keys
            add("preferred_network_mode1")
            add("preferred_network_mode0")
        }.distinct()

        for (key in candidates) {
            val raw = runCatching { Settings.Global.getString(context.contentResolver, key) }.getOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: continue
            // Try as long bitmask first (Android 11+)
            raw.toLongOrNull()?.let { asLong ->
                // If it's a reasonable bitmask (>100), treat as mask
                if (asLong > 100L) {
                    val filtered = asLong and NetworkModePolicy.BITMASK_SELECTABLE_CELLULAR
                    if (filtered > 0L) return filtered
                }
                // Otherwise treat as RIL mode int (e.g., 9, 10 for whyred)
                NetworkModePolicy.defaultNetworkModeMask(asLong.toInt())?.let { return it }
            }
        }
        return null
    }

    /**
     * Same but via privileged shell "settings get global <key>" — works even when
     * ContentResolver is blocked by OEM, if Shizuku/Root is available.
     */
    fun readViaShell(slotIndex: Int, subscriptionId: Int?): Long? {
        if (!PrivilegedRunner.isShizukuGranted() && !PrivilegedRunner.isRootAvailable()) return null
        val candidates = buildList {
            subscriptionId?.takeIf { it >= 0 }?.let {
                add("preferred_network_mode$it")
                add("preferred_network_mode_$it")
            }
            add("preferred_network_mode$slotIndex")
            add("preferred_network_mode_$slotIndex")
            add("preferred_network_mode")
        }.distinct()

        for (key in candidates) {
            val result = PrivilegedRunner.runShell("settings get global $key")
            if (!result.success) continue
            val raw = result.message.trim().takeIf { it.isNotBlank() && it != "null" } ?: continue
            raw.toLongOrNull()?.let { asLong ->
                if (asLong > 100L) {
                    val filtered = asLong and NetworkModePolicy.BITMASK_SELECTABLE_CELLULAR
                    if (filtered > 0L) return filtered
                }
                NetworkModePolicy.defaultNetworkModeMask(asLong.toInt())?.let { return it }
            }
        }
        return null
    }
}
