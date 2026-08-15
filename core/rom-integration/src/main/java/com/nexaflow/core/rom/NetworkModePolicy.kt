package com.nexaflow.core.rom

/**
 * Pure mapping for the cellular network-generation action
 * (`SYSTEM_NETWORK_MODE`). No `android.*` imports so the table and the
 * read-back verification are atomically testable on the JVM.
 *
 * Two value spaces exist because the two write paths disagree:
 *
 *  - [Request.legacyInt] — the `preferred_network_mode` /
 *    `setPreferredNetworkType` PhoneConstants table (1 = GSM only,
 *    2 = WCDMA only, 11 = LTE only, 22 = NR + LTE + legacy, 10 = classic
 *    full-auto). These integers were appended per Android release, so the
 *    NR entries are **not stable across versions** — the bitmask path below
 *    is the reliable one.
 *  - [Request.bitmask] — the `NetworkTypeBitMask` values consumed by
 *    `setAllowedNetworkTypesForReason`, stable since Android 11.
 */
object NetworkModePolicy {

    /** GSM | GPRS | EDGE — mirrors `TelephonyManager.NETWORK_TYPE_BITMASK_*`. */
    const val BITMASK_2G: Long = (1L shl 16) or (1L shl 1) or (1L shl 2)

    /** UMTS | HSDPA | HSUPA | HSPA | HSPAP. */
    const val BITMASK_3G: Long = (1L shl 3) or (1L shl 8) or (1L shl 9) or (1L shl 10) or (1L shl 15)

    /** LTE | LTE_CA. */
    const val BITMASK_4G: Long = (1L shl 13) or (1L shl 19)

    /** NR. */
    const val BITMASK_5G: Long = 1L shl 20

    /** The pre-Android-14 full set: 2G/3G/4G/5G families + TD-SCDMA + IWLAN. */
    const val BITMASK_AUTO: Long =
        BITMASK_2G or BITMASK_3G or BITMASK_4G or BITMASK_5G or (1L shl 17) or (1L shl 18)

    data class Request(val label: String, val legacyInt: Int, val bitmask: Long)

    /**
     * Resolves a mode label to its write values. Unknown or blank labels
     * fall back to AUTO so a stale config never locks the radio to a wrong
     * generation.
     *
     * @param nrSupported whether the platform exposes NR modes (Android 11+);
     *                    controls the legacy full-auto fallback value.
     */
    fun request(mode: String, nrSupported: Boolean = true): Request = when (mode) {
        "2G" -> Request("2G", legacyInt = 1, BITMASK_2G)
        "3G" -> Request("3G", legacyInt = 2, BITMASK_3G)
        "4G" -> Request("4G", legacyInt = 11, BITMASK_4G)
        "5G" -> Request("5G", legacyInt = 22, BITMASK_5G)
        else -> Request("AUTO", legacyAuto(nrSupported), BITMASK_AUTO)
    }

    /**
     * Full-auto legacy int: on Android 11+ the NR-aware value (22) is valid;
     * earlier releases cap at the classic full-legacy 10.
     */
    fun legacyAuto(nrSupported: Boolean): Int = if (nrSupported) 22 else 10

    /**
     * Read-back check for `getAllowedNetworkTypesForReason`: true when
     * [actual] still permits every generation [requested] demands. Extra bits
     * the radio cannot serve are tolerated, so a carrier that silently drops
     * one band still counts the mode as applied.
     */
    fun covers(requested: Long, actual: Long): Boolean = (actual and requested) == requested

    /**
     * Read-back check for `cmd phone get-allowed-network-types-for-users`,
     * which prints the allowed set as band names (e.g. "LTE" or "LTE|NR").
     * True when the printed set matches the requested generation's defining
     * band family.
     */
    fun coversByName(actual: String, label: String): Boolean {
        val a = actual.uppercase()
        return when (label) {
            "2G" -> a.contains("GSM") || a.contains("GPRS") || a.contains("EDGE")
            "3G" -> a.contains("UMTS") || a.contains("HSDPA") || a.contains("HSPA")
            "4G" -> a.contains("LTE") && !a.contains("NR")
            "5G" -> a.contains("NR")
            else -> a.isNotBlank() && !a.contains("UNKNOWN")
        }
    }
}
