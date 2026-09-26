package com.nexaflow.core.automationcontrol.schedule

import com.nexaflow.core.automationcontrol.api.AgentTaskDraftV1
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.schedule.TimeTriggerCalculator
import java.time.DateTimeException
import java.time.ZoneId
import kotlinx.serialization.Serializable

@Serializable
enum class AgentScheduleZonePolicyV1 {
    DEVICE_LOCAL,
    FIXED_IANA
}

@Serializable
data class AgentSchedulePreviewRequestV1(
    val task: AgentTaskDraftV1,
    val fromEpochMillis: Long,
    val count: Int = DEFAULT_PREVIEW_COUNT,
    val zonePolicy: AgentScheduleZonePolicyV1 = AgentScheduleZonePolicyV1.DEVICE_LOCAL,
    val fixedZoneId: String? = null
) {
    companion object {
        const val DEFAULT_PREVIEW_COUNT = 5
    }
}

@Serializable
data class AgentScheduleOccurrenceV1(
    val triggerIndex: Int,
    val fireAtEpochMillis: Long,
    val windowEndEpochMillis: Long? = null
)

@Serializable
data class AgentScheduleTriggerPreviewV1(
    val triggerIndex: Int,
    val occurrences: List<AgentScheduleOccurrenceV1>
)

@Serializable
data class AgentSchedulePreviewV1(
    val zoneId: String,
    val triggers: List<AgentScheduleTriggerPreviewV1>,
    val truncated: Boolean = false
)

sealed interface AgentSchedulePreviewResult {
    data class Success(val preview: AgentSchedulePreviewV1) : AgentSchedulePreviewResult
    data class Rejected(val code: String, val path: String, val message: String) :
        AgentSchedulePreviewResult
}

/**
 * Side-effect-free schedule preview for agent/API callers.
 *
 * The service delegates every recurrence/DST decision to TimeTriggerCalculator,
 * exactly like the production scheduler. It never schedules alarms and never
 * mutates the task.
 */
class AgentSchedulePreviewService(
    private val deviceZone: () -> ZoneId = ZoneId::systemDefault
) {

    fun preview(request: AgentSchedulePreviewRequestV1): AgentSchedulePreviewResult {
        if (request.count !in 1..MAX_PREVIEW_COUNT) {
            return AgentSchedulePreviewResult.Rejected(
                code = "invalid_preview_count",
                path = "count",
                message = "count must be between 1 and $MAX_PREVIEW_COUNT"
            )
        }
        if (request.fromEpochMillis < 0L) {
            return AgentSchedulePreviewResult.Rejected(
                code = "invalid_start_time",
                path = "fromEpochMillis",
                message = "fromEpochMillis must not be negative"
            )
        }

        val zone = resolveZone(request) ?: return AgentSchedulePreviewResult.Rejected(
            code = "invalid_timezone",
            path = "fixedZoneId",
            message = "fixedZoneId must be a valid IANA timezone when FIXED_IANA is selected"
        )

        val timeTriggers = request.task.triggers.withIndex()
            .filter { (_, trigger) -> trigger.type == TriggerType.TIME.name }

        if (timeTriggers.isEmpty()) {
            return AgentSchedulePreviewResult.Rejected(
                code = "no_time_trigger",
                path = "task.triggers",
                message = "task must contain at least one TIME trigger"
            )
        }

        val previews = timeTriggers.map { indexed ->
            val occurrences = ArrayList<AgentScheduleOccurrenceV1>(request.count)
            var cursor = request.fromEpochMillis
            repeat(request.count) {
                val next = TimeTriggerCalculator.nextFireTime(
                    config = indexed.value.config,
                    fromMillis = cursor,
                    zone = zone
                ) ?: return@repeat
                occurrences += AgentScheduleOccurrenceV1(
                    triggerIndex = indexed.index,
                    fireAtEpochMillis = next,
                    windowEndEpochMillis = TimeTriggerCalculator.windowEndMillis(
                        config = indexed.value.config,
                        windowStartMillis = next,
                        zone = zone
                    )
                )
                cursor = next
            }
            AgentScheduleTriggerPreviewV1(
                triggerIndex = indexed.index,
                occurrences = occurrences
            )
        }

        return AgentSchedulePreviewResult.Success(
            AgentSchedulePreviewV1(
                zoneId = zone.id,
                triggers = previews
            )
        )
    }

    private fun resolveZone(request: AgentSchedulePreviewRequestV1): ZoneId? =
        when (request.zonePolicy) {
            AgentScheduleZonePolicyV1.DEVICE_LOCAL -> deviceZone()
            AgentScheduleZonePolicyV1.FIXED_IANA -> {
                val id = request.fixedZoneId?.trim().orEmpty()
                if (id.isEmpty()) null else try {
                    ZoneId.of(id)
                } catch (_: DateTimeException) {
                    null
                }
            }
        }

    private companion object {
        const val MAX_PREVIEW_COUNT = 50
    }
}
