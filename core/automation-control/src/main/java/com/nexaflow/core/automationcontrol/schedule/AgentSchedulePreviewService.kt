package com.nexaflow.core.automationcontrol.schedule

import com.nexaflow.core.automationcontrol.api.AgentTaskDraftV1
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.schedule.TimeTriggerCalculator
import java.time.ZoneId
import kotlinx.serialization.Serializable

@Serializable
enum class AgentScheduleZonePolicyV1 {
    /** Use the persisted TIME trigger zonePolicy/zoneId, defaulting to device local. */
    TASK_CONFIG,
    /** Explicit preview override that ignores a persisted fixed timezone. */
    DEVICE_LOCAL,
    /** Explicit preview override using [AgentSchedulePreviewRequestV1.fixedZoneId]. */
    FIXED_IANA
}

@Serializable
data class AgentSchedulePreviewRequestV1(
    val task: AgentTaskDraftV1,
    val fromEpochMillis: Long,
    val count: Int = DEFAULT_PREVIEW_COUNT,
    val zonePolicy: AgentScheduleZonePolicyV1 = AgentScheduleZonePolicyV1.TASK_CONFIG,
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

        val timeTriggers = request.task.triggers.withIndex()
            .filter { (_, trigger) -> trigger.type == TriggerType.TIME.name }

        if (timeTriggers.isEmpty()) {
            return AgentSchedulePreviewResult.Rejected(
                code = "no_time_trigger",
                path = "task.triggers",
                message = "task must contain at least one TIME trigger"
            )
        }

        val persistedConfig = timeTriggers.first().value.config
        val zone = resolveZone(request, persistedConfig)
            ?: return AgentSchedulePreviewResult.Rejected(
                code = "invalid_timezone",
                path = if (request.zonePolicy == AgentScheduleZonePolicyV1.FIXED_IANA) {
                    "fixedZoneId"
                } else {
                    "task.triggers[${timeTriggers.first().index}].config.zoneId"
                },
                message = "schedule timezone must be a valid IANA timezone"
            )

        val previews = timeTriggers.map { indexed ->
            val occurrences = ArrayList<AgentScheduleOccurrenceV1>(request.count)
            var cursor = request.fromEpochMillis
            while (occurrences.size < request.count) {
                val next = TimeTriggerCalculator.nextFireTime(
                    config = indexed.value.config,
                    fromMillis = cursor,
                    zone = zone
                ) ?: break
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

    private fun resolveZone(
        request: AgentSchedulePreviewRequestV1,
        persistedConfig: Map<String, String>
    ): ZoneId? = when (request.zonePolicy) {
        AgentScheduleZonePolicyV1.TASK_CONFIG ->
            TimeTriggerCalculator.resolveZone(persistedConfig, deviceZone())
        AgentScheduleZonePolicyV1.DEVICE_LOCAL -> deviceZone()
        AgentScheduleZonePolicyV1.FIXED_IANA ->
            TimeTriggerCalculator.resolveZone(
                config = mapOf(
                    TimeTriggerCalculator.ZONE_POLICY_KEY to
                        TimeTriggerCalculator.ZONE_POLICY_FIXED_IANA,
                    TimeTriggerCalculator.ZONE_ID_KEY to request.fixedZoneId.orEmpty()
                ),
                deviceZone = deviceZone()
            )
    }

    private companion object {
        const val MAX_PREVIEW_COUNT = 50
    }
}
