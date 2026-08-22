package com.nexaflow.core.rom

/**
 * Pure cellular-radio policy shared by the UI capability reader and the
 * privileged writer. Values are Android [TelephonyManager.NetworkTypeBitMask]
 * compatible and are deliberately JVM-testable.
 */
object NetworkModePolicy {

    /** GSM | GPRS | EDGE. */
    const val BITMASK_2G: Long = (1L shl 16) or (1L shl 1) or (1L shl 2)

    /** UMTS | HSDPA | HSUPA | HSPA | HSPAP. */
    const val BITMASK_3G: Long =
        (1L shl 3) or (1L shl 8) or (1L shl 9) or (1L shl 10) or (1L shl 15)

    /** CDMA | 1xRTT. */
    const val BITMASK_CDMA: Long = (1L shl 4) or (1L shl 7)

    /** EVDO-0 | EVDO-A | EVDO-B | eHRPD. */
    const val BITMASK_EVDO: Long = (1L shl 5) or (1L shl 6) or (1L shl 12) or (1L shl 14)

    /** TD-SCDMA. */
    const val BITMASK_TD_SCDMA: Long = 1L shl 17

    /** LTE | LTE-CA. */
    const val BITMASK_4G: Long = (1L shl 13) or (1L shl 19)

    /** NR (5G). */
    const val BITMASK_5G: Long = 1L shl 20

    /** Cellular RATs that make sense as a user-selectable preferred mode. */
    const val BITMASK_SELECTABLE_CELLULAR: Long =
        BITMASK_2G or BITMASK_3G or BITMASK_CDMA or BITMASK_EVDO or BITMASK_TD_SCDMA or
            BITMASK_4G or BITMASK_5G

    /** The full AOSP set, retained for legacy AUTO tasks only. */
    const val BITMASK_AUTO: Long = BITMASK_SELECTABLE_CELLULAR or (1L shl 18)

    data class Request(
        val label: String,
        val legacyInt: Int,
        val bitmask: Long,
        /** Legacy PhoneConstants cannot represent this exact profile safely. */
        val legacyCompatible: Boolean = true
    )

    /** A candidate profile that is filtered against a confirmed device mask. */
    data class Option(
        val id: String,
        val allowedNetworkTypes: Long,
        val label: String,
        val isAutomatic: Boolean = false
    )

    /** Versioned, per-subscription capture used by restore-original behavior. */
    private const val SNAPSHOT_PREFIX = "network-mask-v1:"

    fun encodeSnapshot(masksBySubscription: Map<Int, Long>): String? {
        if (masksBySubscription.isEmpty()) return null
        return SNAPSHOT_PREFIX + masksBySubscription
            .toSortedMap()
            .entries
            .joinToString(",") { (subId, mask) -> "$subId=$mask" }
    }

    fun decodeSnapshot(value: String): Map<Int, Long>? {
        if (!value.startsWith(SNAPSHOT_PREFIX)) return null
        val entries = value.removePrefix(SNAPSHOT_PREFIX)
            .split(',')
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val subId = entry.substringBefore('=').toIntOrNull()
                val mask = entry.substringAfter('=', missingDelimiterValue = "").toLongOrNull()
                if (subId != null && mask != null && mask > 0L) subId to mask else null
            }
            .toMap()
        return entries.takeIf { it.isNotEmpty() }
    }

    /**
     * Resolves legacy task values. Unknown values deliberately return AUTO so a
     * saved task cannot silently lock a radio to a guessed technology.
     */
    fun request(mode: String, nrSupported: Boolean = true): Request = when (mode) {
        "2G" -> Request("2G", legacyInt = 1, BITMASK_2G)
        "3G" -> Request("3G", legacyInt = 2, BITMASK_3G)
        "4G" -> Request("4G", legacyInt = 11, BITMASK_4G)
        "5G" -> Request("5G", legacyInt = 22, BITMASK_5G)
        else -> Request("AUTO", legacyAuto(nrSupported), BITMASK_AUTO)
    }

    /**
     * Builds an exact request from a mode read from this device. The legacy
     * PhoneConstants fallback is explicitly disabled because its integer table
     * cannot faithfully encode modern NR/TD-SCDMA/carrier combinations.
     */
    fun requestForMask(mask: Long): Request {
        val cellular = mask and BITMASK_SELECTABLE_CELLULAR
        return Request(
            label = describe(cellular),
            legacyInt = legacyAuto(nrSupported = (cellular and BITMASK_5G) != 0L),
            bitmask = cellular,
            legacyCompatible = false
        )
    }

    /**
     * Generates only profiles that are complete subsets of [supportedMask].
     * This preserves real carrier/hardware limits instead of displaying a
     * hard-coded list of 2G/3G/4G/5G choices on every phone.
     */
    fun optionsFor(supportedMask: Long): List<Option> {
        val supported = supportedMask and BITMASK_SELECTABLE_CELLULAR
        if (supported == 0L) return emptyList()
        val options = mutableListOf(
            Option(
                id = "AUTO",
                allowedNetworkTypes = supported,
                label = describe(supported),
                isAutomatic = true
            )
        )
        standardProfiles.forEach { option ->
            if (option.allowedNetworkTypes != supported &&
                (supported and option.allowedNetworkTypes) == option.allowedNetworkTypes
            ) {
                options += option
            }
        }
        return options.distinctBy { it.allowedNetworkTypes }
    }

    /** Name every supported radio family without assuming the carrier supports it. */
    fun describe(mask: Long): String = buildList {
        if ((mask and BITMASK_5G) != 0L) add("NR")
        if ((mask and BITMASK_4G) != 0L) add("LTE")
        if ((mask and BITMASK_TD_SCDMA) != 0L) add("TD-SCDMA")
        if ((mask and BITMASK_CDMA) != 0L) add("CDMA")
        if ((mask and BITMASK_EVDO) != 0L) add("EvDo")
        if ((mask and BITMASK_3G) != 0L) add("WCDMA")
        if ((mask and BITMASK_2G) != 0L) add("GSM")
    }.joinToString(" / ").ifBlank { "Unknown" }

    fun legacyAuto(nrSupported: Boolean): Int = if (nrSupported) 22 else 10

    /** True when [actual] retains the entire requested profile. */
    fun covers(requested: Long, actual: Long): Boolean = (actual and requested) == requested

    /**
     * Preferred-network mode is a restriction, not a minimum capability. A
     * read-back containing NR after the user chose LTE-only is therefore not a
     * success. Ignore IWLAN/unknown bits but require exact cellular equality.
     */
    fun matches(requested: Long, actual: Long): Boolean =
        (requested and BITMASK_SELECTABLE_CELLULAR) == (actual and BITMASK_SELECTABLE_CELLULAR)

    /**
     * Parses AOSP/OEM `cmd phone` read-back output into a selectable cellular
     * mask. Binary must be considered before decimal: a short binary mask such
     * as `1000000000000000` is also syntactically a decimal number but means a
     * completely different radio family when interpreted that way.
     */
    fun parseReadBackMask(actual: String): Long? {
        val trimmed = actual.trim()
        val upper = trimmed.uppercase()
        if (trimmed.isEmpty() || trimmed.equals("-1", ignoreCase = true) ||
            upper.contains("FAILED") || upper.contains("ERROR") ||
            upper.contains("EXCEPTION") || upper.contains("UNKNOWN") ||
            upper.contains("NO SUCH") || upper.contains("UNSUPPORTED")
        ) return null

        // OEMs may prefix a valid number with a label or a slot id. Prefer the
        // final numeric token, which is the actual mask for all known formats.
        val binaryToken = Regex("(?<![01])[01]{11,}(?![01])")
            .findAll(trimmed)
            .lastOrNull()
            ?.value
        val decimalToken = Regex("(?<![0-9])-?[0-9]+(?![0-9])")
            .findAll(trimmed)
            .lastOrNull()
            ?.value
        val rawMask = binaryToken?.toLongOrNull(2)
            ?: decimalToken?.toLongOrNull()
            ?: maskFromNames(upper)
        return rawMask
            .and(BITMASK_SELECTABLE_CELLULAR)
            .takeIf { it > 0L }
    }

    /** Parses both AOSP text output and numeric OEM shell output exactly. */
    fun coversReadBack(actual: String, request: Request): Boolean =
        parseReadBackMask(actual)?.let { matches(request.bitmask, it) } ?: false

    private fun maskFromNames(names: String): Long {
        var mask = 0L
        if (names.contains("NR")) mask = mask or BITMASK_5G
        if (names.contains("LTE")) mask = mask or BITMASK_4G
        if (names.contains("TD")) mask = mask or BITMASK_TD_SCDMA
        if (names.contains("CDMA")) mask = mask or BITMASK_CDMA
        if (names.contains("EVDO") || names.contains("EHRPD")) mask = mask or BITMASK_EVDO
        if (names.contains("UMTS") || names.contains("WCDMA") || names.contains("HSPA")) {
            mask = mask or BITMASK_3G
        }
        if (names.contains("GSM") || names.contains("GPRS") || names.contains("EDGE")) {
            mask = mask or BITMASK_2G
        }
        return mask
    }

    private val standardProfiles = listOf(
        profile("NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA", BITMASK_5G or BITMASK_4G or BITMASK_TD_SCDMA or BITMASK_CDMA or BITMASK_EVDO or BITMASK_2G or BITMASK_3G),
        profile("NR_LTE_TDSCDMA_GSM_WCDMA", BITMASK_5G or BITMASK_4G or BITMASK_TD_SCDMA or BITMASK_2G or BITMASK_3G),
        profile("NR_LTE_GSM_WCDMA", BITMASK_5G or BITMASK_4G or BITMASK_2G or BITMASK_3G),
        profile("NR_LTE_WCDMA", BITMASK_5G or BITMASK_4G or BITMASK_3G),
        profile("NR_LTE", BITMASK_5G or BITMASK_4G),
        profile("NR", BITMASK_5G),
        profile("LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA", BITMASK_4G or BITMASK_TD_SCDMA or BITMASK_CDMA or BITMASK_EVDO or BITMASK_2G or BITMASK_3G),
        profile("LTE_TDSCDMA_GSM_WCDMA", BITMASK_4G or BITMASK_TD_SCDMA or BITMASK_2G or BITMASK_3G),
        profile("LTE_GSM_WCDMA", BITMASK_4G or BITMASK_2G or BITMASK_3G),
        profile("LTE_WCDMA", BITMASK_4G or BITMASK_3G),
        profile("LTE", BITMASK_4G),
        profile("TDSCDMA_WCDMA", BITMASK_TD_SCDMA or BITMASK_3G),
        profile("TDSCDMA_GSM", BITMASK_TD_SCDMA or BITMASK_2G),
        profile("TDSCDMA", BITMASK_TD_SCDMA),
        profile("CDMA_EVDO", BITMASK_CDMA or BITMASK_EVDO),
        profile("CDMA", BITMASK_CDMA),
        profile("EVDO", BITMASK_EVDO),
        profile("GSM_WCDMA", BITMASK_2G or BITMASK_3G),
        profile("WCDMA", BITMASK_3G),
        profile("GSM", BITMASK_2G)
    )

    private fun profile(id: String, mask: Long): Option =
        Option(id = id, allowedNetworkTypes = mask, label = describe(mask))
}
