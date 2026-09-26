package com.nexaflow.core.automationcontrol.schedule

import com.nexaflow.core.automationcontrol.api.AgentActionDraftV1
import com.nexaflow.core.automationcontrol.api.AgentTaskDraftV1
import com.nexaflow.core.automationcontrol.api.AgentTriggerDraftV1
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSchedulePreviewServiceTest {

    private val utc = ZoneId.of("UTC")
    private val service = AgentSchedulePreviewService(deviceZone = { utc })

    private fun task(config: Map<String, String>) = AgentTaskDraftV1(
        name = "Scheduled task",
        triggers = listOf(
            AgentTriggerDraftV1(
                type = "TIME",
                config = config
            )
        ),
        actions = listOf(
            AgentActionDraftV1(
                type = "SYSTEM_SEND_NOTIFICATION",
                config = mapOf("title" to "hello")
            )
        )
    )

    @Test
    fun dailyPreviewReturnsOrderedNextOccurrences() {
        val from = Instant.parse("2026-09-26T07:00:00Z").toEpochMilli()

        val result = service.preview(
            AgentSchedulePreviewRequestV1(
                task = task(
                    mapOf(
                        "time" to "08:00",
                        "repeat" to "DAILY"
                    )
                ),
                fromEpochMillis = from,
                count = 3
            )
        ) as AgentSchedulePreviewResult.Success

        assertEquals("UTC", result.preview.zoneId)
        assertEquals(
            listOf(
                "2026-09-26T08:00:00Z",
                "2026-09-27T08:00:00Z",
                "2026-09-28T08:00:00Z"
            ),
            result.preview.triggers.single().occurrences.map {
                Instant.ofEpochMilli(it.fireAtEpochMillis).toString()
            }
        )
    }

    @Test
    fun rangePreviewIncludesExactWindowEnd() {
        val from = Instant.parse("2026-09-26T20:00:00Z").toEpochMilli()

        val result = service.preview(
            AgentSchedulePreviewRequestV1(
                task = task(
                    mapOf(
                        "timeMode" to "RANGE",
                        "rangeStart" to "22:00",
                        "rangeEnd" to "06:00",
                        "repeat" to "DAILY"
                    )
                ),
                fromEpochMillis = from,
                count = 1
            )
        ) as AgentSchedulePreviewResult.Success

        val occurrence = result.preview.triggers.single().occurrences.single()
        assertEquals(
            "2026-09-26T22:00:00Z",
            Instant.ofEpochMilli(occurrence.fireAtEpochMillis).toString()
        )
        assertEquals(
            "2026-09-27T06:00:00Z",
            Instant.ofEpochMilli(occurrence.windowEndEpochMillis!!).toString()
        )
    }

    @Test
    fun fixedIanaTimezoneUsesWallClockSemantics() {
        val from = Instant.parse("2026-09-26T06:30:00Z").toEpochMilli()

        val result = service.preview(
            AgentSchedulePreviewRequestV1(
                task = task(mapOf("time" to "09:00", "repeat" to "DAILY")),
                fromEpochMillis = from,
                count = 1,
                zonePolicy = AgentScheduleZonePolicyV1.FIXED_IANA,
                fixedZoneId = "Europe/Berlin"
            )
        ) as AgentSchedulePreviewResult.Success

        assertEquals("Europe/Berlin", result.preview.zoneId)
        assertEquals(
            "2026-09-26T07:00:00Z",
            Instant.ofEpochMilli(
                result.preview.triggers.single().occurrences.single().fireAtEpochMillis
            ).toString()
        )
    }

    @Test
    fun springDstGapUsesProductionCalculatorSemantics() {
        val from = Instant.parse("2026-03-28T20:00:00Z").toEpochMilli()

        val result = service.preview(
            AgentSchedulePreviewRequestV1(
                task = task(mapOf("time" to "02:30", "repeat" to "DAILY")),
                fromEpochMillis = from,
                count = 1,
                zonePolicy = AgentScheduleZonePolicyV1.FIXED_IANA,
                fixedZoneId = "Europe/Berlin"
            )
        ) as AgentSchedulePreviewResult.Success

        assertEquals(
            "2026-03-30T00:30:00Z",
            Instant.ofEpochMilli(
                result.preview.triggers.single().occurrences.single().fireAtEpochMillis
            ).toString()
        )
    }

    @Test
    fun oneShotScheduleStopsWhenRecurrenceIsExhausted() {
        val from = Instant.parse("2026-09-26T07:00:00Z").toEpochMilli()
        val result = service.preview(
            AgentSchedulePreviewRequestV1(
                task = task(
                    mapOf(
                        "time" to "08:00",
                        "repeat" to "SPECIFIC_DATE",
                        "date" to "2026-09-26"
                    )
                ),
                fromEpochMillis = from,
                count = 5
            )
        ) as AgentSchedulePreviewResult.Success

        assertEquals(1, result.preview.triggers.single().occurrences.size)
    }

    @Test
    fun nonTimeTaskIsRejected() {
        val request = AgentSchedulePreviewRequestV1(
            task = AgentTaskDraftV1(
                name = "No schedule",
                triggers = listOf(AgentTriggerDraftV1(type = "BATTERY")),
                actions = emptyList()
            ),
            fromEpochMillis = 1L
        )

        val result = service.preview(request)

        assertTrue(result is AgentSchedulePreviewResult.Rejected)
        assertEquals(
            "no_time_trigger",
            (result as AgentSchedulePreviewResult.Rejected).code
        )
    }

    @Test
    fun invalidTimezoneAndOversizedPreviewFailClosed() {
        val invalidZone = service.preview(
            AgentSchedulePreviewRequestV1(
                task = task(mapOf("time" to "08:00")),
                fromEpochMillis = 1L,
                zonePolicy = AgentScheduleZonePolicyV1.FIXED_IANA,
                fixedZoneId = "Mars/Olympus"
            )
        )
        assertEquals(
            "invalid_timezone",
            (invalidZone as AgentSchedulePreviewResult.Rejected).code
        )

        val tooMany = service.preview(
            AgentSchedulePreviewRequestV1(
                task = task(mapOf("time" to "08:00")),
                fromEpochMillis = 1L,
                count = 51
            )
        )
        assertEquals(
            "invalid_preview_count",
            (tooMany as AgentSchedulePreviewResult.Rejected).code
        )
    }

    @Test
    fun nonRangeOccurrenceHasNoWindowEnd() {
        val result = service.preview(
            AgentSchedulePreviewRequestV1(
                task = task(mapOf("time" to "08:00")),
                fromEpochMillis = Instant.parse("2026-09-26T07:00:00Z").toEpochMilli(),
                count = 1
            )
        ) as AgentSchedulePreviewResult.Success

        assertNull(result.preview.triggers.single().occurrences.single().windowEndEpochMillis)
    }
}
