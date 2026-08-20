package com.nexaflow.domain.models

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.security.MessageDigest

/**
 * Derives a bounded, deterministic occurrence identity for recurring
 * maintenance. The date belongs to the start of an overnight window, so an
 * occurrence at 02:00 is the same logical run that began the previous evening.
 */
object MaintenanceExecutionIdentity {

    fun occurrenceKey(automation: Automation, now: ZonedDateTime): String? {
        val profile = automation.maintenanceProfile ?: return null
        val window = profile.window
        val occurrenceDate = occurrenceDate(window, now)
        val source = listOf(
            automation.id,
            profile.kind.name,
            occurrenceDate.toString(),
            window?.startTime.orEmpty(),
            window?.endTime.orEmpty()
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "maintenance:$digest"
    }

    private fun occurrenceDate(window: MaintenanceWindow?, now: ZonedDateTime): LocalDate {
        val startValue = window?.startTime ?: return now.toLocalDate()
        val endValue = window.endTime ?: return now.toLocalDate()
        val start = LocalTime.parse(startValue)
        val end = LocalTime.parse(endValue)
        return if (end <= start && now.toLocalTime() < end) {
            now.minusDays(1).toLocalDate()
        } else {
            now.toLocalDate()
        }
    }
}
