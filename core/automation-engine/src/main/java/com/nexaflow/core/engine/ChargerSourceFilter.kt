package com.nexaflow.core.engine

import java.util.Locale

/**
 * The charger trigger's optional source filter.
 *
 * "Any" keeps the original behaviour (fire on every plug event). A specific
 * source only fires for that plug type, so "charging from a computer" and
 * "charging from the wall" are separate automations.
 */
internal object ChargerSourceFilter {
    const val ANY = "ANY"

    /** True when [configured] accepts [plugType] (AC / USB / WIRELESS). */
    fun matches(configured: String?, plugType: String): Boolean {
        val filter = configured?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (filter.isEmpty() || filter == ANY) return true
        return filter == plugType.trim().uppercase(Locale.ROOT)
    }
}
