package com.nexaflow.feature.dashboard

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexaflow.core.execution.ActionCurrentState
import com.nexaflow.core.execution.ActionStateReader
import com.nexaflow.core.execution.ActionStateValueKind
import com.nexaflow.core.execution.AutomationExitReason
import com.nexaflow.core.execution.AutomationLifecycleDisplayState
import com.nexaflow.core.execution.AutomationLifecycleSnapshot
import com.nexaflow.core.execution.AutomationLifecycleStateReader
import com.nexaflow.core.execution.TriggerStateEvaluator
import com.nexaflow.core.execution.constraints.ConstraintStateReader
import com.nexaflow.domain.constraints.ConstraintEvaluator
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.AutomationHealthReport
import com.nexaflow.domain.models.AutomationHealthStatus
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.requiresTimeRangeForEndBehavior
import com.nexaflow.feature.automations.actionPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Expanded dashboard presentation for one automation.
 *
 * The execution engine remains the source of truth. This layer only translates
 * persisted configuration + live readable condition state + the latest durable
 * execution into a scannable card. Event-only triggers are shown as waiting
 * rather than incorrectly treated as failed.
 */
@Composable
internal fun RoutineFlowOverview(
    automation: Automation,
    summary: String
) {
    DetailSection(title = stringResource(R.string.task_details_summary)) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        var step = 1
        if (automation.triggers.isNotEmpty()) {
            RoutineDetailItem(
                leading = (step++).toString(),
                title = stringResource(R.string.task_details_triggers, automation.triggers.size),
                subtitle = triggerStageSummary(automation)
            )
        }

        if (automation.constraints.isNotEmpty()) {
            RoutineDetailItem(
                leading = (step++).toString(),
                title = stringResource(
                    R.string.task_details_constraints,
                    automation.constraints.size
                ),
                subtitle = constraintStageSummary(automation)
            )
        }

        if (automation.actions.isNotEmpty()) {
            RoutineDetailItem(
                leading = (step++).toString(),
                title = stringResource(R.string.task_details_actions, automation.actions.size),
                subtitle = actionStageSummary(automation)
            )
        }

        RoutineDetailItem(
            leading = step.toString(),
            title = stringResource(
                R.string.task_details_exit_actions,
                automation.exitBehaviorItemCount()
            ),
            subtitle = endPlanSummary(automation)
        )
    }
}

@Composable
private fun triggerStageSummary(automation: Automation): String {
    val labels = mutableListOf<String>()
    val visible = automation.triggers.take(FLOW_OVERVIEW_ITEM_LIMIT)
    for (trigger in visible) {
        labels += stringResource(triggerLabel(trigger.type))
    }
    return appendRemainingCount(labels.joinToString(" · "), automation.triggers.size - visible.size)
}

@Composable
private fun constraintStageSummary(automation: Automation): String {
    val labels = mutableListOf<String>()
    val visible = automation.constraints.take(FLOW_OVERVIEW_ITEM_LIMIT)
    for (constraint in visible) {
        labels += stringResource(constraintTitleRes(constraint.type))
    }
    return appendRemainingCount(labels.joinToString(" · "), automation.constraints.size - visible.size)
}

@Composable
private fun actionStageSummary(automation: Automation): String {
    val labels = mutableListOf<String>()
    val visible = automation.actions.take(FLOW_OVERVIEW_ITEM_LIMIT)
    for (action in visible) {
        val (titleRes, _, _) = actionPresentation(action.type)
        labels += stringResource(titleRes)
    }
    return appendRemainingCount(labels.joinToString(" · "), automation.actions.size - visible.size)
}

private fun appendRemainingCount(value: String, remaining: Int): String =
    if (remaining > 0) "$value · +$remaining" else value

@Composable
internal fun DetailedHealthSection(report: AutomationHealthReport) {
    val status = when (report.status) {
        AutomationHealthStatus.NO_EXECUTIONS -> DetailStatus(
            text = stringResource(R.string.task_health_no_runs),
            tone = DetailStatusTone.Neutral
        )
        AutomationHealthStatus.HEALTHY -> DetailStatus(
            text = stringResource(R.string.task_health_healthy),
            tone = DetailStatusTone.Success
        )
        AutomationHealthStatus.NEEDS_ATTENTION -> DetailStatus(
            text = stringResource(R.string.task_health_attention),
            tone = DetailStatusTone.Error
        )
    }

    DetailSection(
        title = stringResource(R.string.task_health_title),
        subtitle = stringResource(R.string.task_health_subtitle)
    ) {
        RoutineDetailItem(
            leading = "✓",
            title = stringResource(
                when (report.status) {
                    AutomationHealthStatus.NO_EXECUTIONS -> R.string.task_health_no_runs_detail
                    AutomationHealthStatus.HEALTHY -> R.string.task_health_healthy_detail
                    AutomationHealthStatus.NEEDS_ATTENTION -> R.string.task_health_attention_detail
                }
            ),
            status = status
        )

        FactRow(
            label = stringResource(R.string.task_health_completed),
            value = report.completedRuns.toString()
        )
        FactRow(
            label = stringResource(R.string.task_health_skipped),
            value = report.skippedRuns.toString()
        )
        FactRow(
            label = stringResource(R.string.task_health_failed),
            value = report.failedRuns.toString()
        )
        if (report.consecutiveFailures > 0) {
            FactRow(
                label = stringResource(R.string.task_health_consecutive_failures),
                value = report.consecutiveFailures.toString()
            )
        }
        if (report.recoveryReviewPending) {
            RoutineDetailItem(
                leading = "!",
                title = stringResource(R.string.task_health_recovery_pending),
                subtitle = stringResource(R.string.task_health_recovery_pending_detail),
                status = DetailStatus(
                    text = stringResource(R.string.task_health_attention),
                    tone = DetailStatusTone.Warning
                )
            )
        }
        report.latestFailureMessage
            ?.takeIf { it.isNotBlank() }
            ?.let { failure ->
                FactRow(
                    label = stringResource(R.string.task_health_last_issue),
                    value = failure.take(HEALTH_FAILURE_PREVIEW_LIMIT)
                )
            }
    }
}

@Composable
internal fun DetailedLifecycleSection(automation: Automation) {
    val context = LocalContext.current
    val lifecycle by produceState<AutomationLifecycleSnapshot?>(
        initialValue = null,
        automation.id,
        automation.enabled
    ) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    AutomationLifecycleStateReader.read(
                        context.applicationContext,
                        automation.id
                    )
                }.getOrNull()
            }
            delay(DETAIL_STATE_REFRESH_MS)
        }
    }

    val status = when (lifecycle?.state) {
        AutomationLifecycleDisplayState.ACTIVE -> DetailStatus(
            text = stringResource(R.string.task_detail_lifecycle_active),
            tone = DetailStatusTone.Success
        )
        AutomationLifecycleDisplayState.EXITING -> DetailStatus(
            text = stringResource(R.string.task_detail_lifecycle_ending),
            tone = DetailStatusTone.Warning
        )
        AutomationLifecycleDisplayState.EXIT_FAILED -> DetailStatus(
            text = stringResource(R.string.task_detail_lifecycle_exit_failed),
            tone = DetailStatusTone.Error
        )
        null -> DetailStatus(
            text = stringResource(
                if (automation.enabled) {
                    R.string.task_detail_lifecycle_waiting
                } else {
                    R.string.task_detail_lifecycle_disabled
                }
            ),
            tone = DetailStatusTone.Neutral
        )
    }

    DetailSection(
        title = stringResource(R.string.task_detail_lifecycle_title),
        subtitle = stringResource(R.string.task_detail_lifecycle_subtitle)
    ) {
        RoutineDetailItem(
            leading = "●",
            title = lifecycleTitle(automation, lifecycle),
            status = status
        )

        lifecycle?.let { snapshot ->
            FactRow(
                label = stringResource(R.string.task_detail_lifecycle_activated_at),
                value = formatLifecycleTimestamp(context, snapshot.activatedAt)
            )
            snapshot.expectedEndAt?.let { expectedEndAt ->
                FactRow(
                    label = stringResource(R.string.task_detail_lifecycle_expected_end),
                    value = formatLifecycleTimestamp(context, expectedEndAt)
                )
            }
            snapshot.exitStartedAt?.let { exitStartedAt ->
                FactRow(
                    label = stringResource(R.string.task_detail_lifecycle_exit_started),
                    value = formatLifecycleTimestamp(context, exitStartedAt)
                )
            }
            snapshot.exitReason?.let { reason ->
                FactRow(
                    label = stringResource(R.string.task_detail_lifecycle_exit_reason),
                    value = lifecycleExitReasonText(reason)
                )
            }
            if (snapshot.exitAttempt > 0) {
                FactRow(
                    label = stringResource(R.string.task_detail_lifecycle_exit_attempt),
                    value = snapshot.exitAttempt.toString()
                )
            }
        }

        FactRow(
            label = stringResource(R.string.task_detail_lifecycle_end_plan),
            value = endPlanSummary(automation)
        )
    }
}

@Composable
private fun lifecycleTitle(
    automation: Automation,
    lifecycle: AutomationLifecycleSnapshot?
): String = when (lifecycle?.state) {
    AutomationLifecycleDisplayState.ACTIVE ->
        stringResource(R.string.task_detail_lifecycle_active_detail)
    AutomationLifecycleDisplayState.EXITING ->
        stringResource(R.string.task_detail_lifecycle_ending_detail)
    AutomationLifecycleDisplayState.EXIT_FAILED ->
        stringResource(R.string.task_detail_lifecycle_exit_failed_detail)
    null -> stringResource(
        if (automation.enabled) {
            R.string.task_detail_lifecycle_waiting_detail
        } else {
            R.string.task_detail_lifecycle_disabled_detail
        }
    )
}

@Composable
private fun lifecycleExitReasonText(reason: AutomationExitReason): String = stringResource(
    when (reason) {
        AutomationExitReason.TIME_WINDOW_ENDED -> R.string.task_detail_exit_reason_time_window
        AutomationExitReason.TRIGGER_FALSE -> R.string.task_detail_exit_reason_trigger_false
        AutomationExitReason.AUTOMATION_DISABLED -> R.string.task_detail_exit_reason_disabled
        AutomationExitReason.MANUAL_STOP -> R.string.task_detail_exit_reason_manual
        AutomationExitReason.PROCESS_RECOVERY -> R.string.task_detail_exit_reason_process_recovery
        AutomationExitReason.BOOT_RECOVERY -> R.string.task_detail_exit_reason_boot_recovery
        AutomationExitReason.SCHEDULE_RECONCILIATION ->
            R.string.task_detail_exit_reason_schedule_reconcile
        AutomationExitReason.SYSTEM_STATE_CHANGED ->
            R.string.task_detail_exit_reason_system_state
        AutomationExitReason.UNKNOWN -> R.string.task_detail_exit_reason_unknown
    }
)

@Composable
private fun endPlanSummary(automation: Automation): String {
    if (automation.revertOnExit) {
        return stringResource(R.string.task_detail_end_plan_revert)
    }
    val perAction = automation.actions.count {
        it.endBehavior?.mode?.let { mode -> mode != EndMode.LEAVE } == true
    }
    val total = perAction + automation.exitActions.size
    return if (total == 0) {
        stringResource(R.string.task_detail_end_plan_none)
    } else {
        stringResource(R.string.task_detail_end_plan_steps, total)
    }
}

private fun formatLifecycleTimestamp(context: Context, timestamp: Long): String {
    val date = Date(timestamp)
    val dateText = DateFormat.getMediumDateFormat(context).format(date)
    val timeText = DateFormat.getTimeFormat(context).format(date)
    return "$dateText · $timeText"
}

internal data class RoutineConditionSnapshot(
    val triggerStates: List<ConditionResult?>,
    val constraintStates: List<Boolean?>
)

internal enum class RoutineReadinessState {
    READY,
    WAITING,
    UNKNOWN,
    DISABLED,
    CONFIGURATION_ERROR
}

@Composable
internal fun rememberRoutineConditionSnapshot(
    automation: Automation
): RoutineConditionSnapshot {
    val context = LocalContext.current
    val snapshot by produceState(
        initialValue = RoutineConditionSnapshot(
            triggerStates = List(automation.triggers.size) { null },
            constraintStates = List(automation.constraints.size) { null }
        ),
        automation.triggers,
        automation.constraints
    ) {
        val hasLocalConstraints = automation.constraints.any {
            it.type != ConstraintType.PLUGIN
        }
        while (true) {
            value = withContext(Dispatchers.IO) {
                val triggerStates = automation.triggers.map { trigger ->
                    runCatching {
                        TriggerStateEvaluator.evaluateTriggerState(
                            context.applicationContext,
                            trigger
                        )
                    }.getOrElse { throwable ->
                        ConditionResult.Error(
                            throwable.message
                                ?.take(CONDITION_ERROR_PREVIEW_LIMIT)
                                ?.ifBlank { CONDITION_EVALUATION_FAILED }
                                ?: CONDITION_EVALUATION_FAILED
                        )
                    }
                }
                val localConstraintSnapshot = if (hasLocalConstraints) {
                    runCatching {
                        ConstraintStateReader.capture(context.applicationContext)
                    }.getOrNull()
                } else {
                    null
                }
                val constraintStates = automation.constraints.map { constraint ->
                    when {
                        constraint.type == ConstraintType.PLUGIN -> null
                        localConstraintSnapshot == null -> null
                        else -> ConstraintEvaluator.isSatisfied(
                            constraint,
                            localConstraintSnapshot
                        )
                    }
                }
                RoutineConditionSnapshot(
                    triggerStates = triggerStates,
                    constraintStates = constraintStates
                )
            }
            delay(DETAIL_STATE_REFRESH_MS)
        }
    }
    return snapshot
}

internal fun routineReadinessState(
    automation: Automation,
    snapshot: RoutineConditionSnapshot
): RoutineReadinessState {
    if (!automation.enabled) return RoutineReadinessState.DISABLED
    if (automation.requiresTimeRangeForEndBehavior) {
        return RoutineReadinessState.CONFIGURATION_ERROR
    }

    val triggerState = aggregateTriggerState(
        states = snapshot.triggerStates,
        mode = automation.triggerMatch,
        hasTriggers = automation.triggers.isNotEmpty()
    )
    val constraintState = aggregateConstraintState(
        snapshot.constraintStates,
        automation.constraints.isNotEmpty()
    )

    return when {
        triggerState == GateState.BLOCKED || constraintState == GateState.BLOCKED ->
            RoutineReadinessState.WAITING
        triggerState == GateState.UNKNOWN || constraintState == GateState.UNKNOWN ->
            RoutineReadinessState.UNKNOWN
        else -> RoutineReadinessState.READY
    }
}

private enum class GateState { READY, BLOCKED, UNKNOWN }

private fun aggregateTriggerState(
    states: List<ConditionResult?>,
    mode: TriggerMatchMode,
    hasTriggers: Boolean
): GateState {
    if (!hasTriggers) return GateState.READY
    val hasSatisfied = states.any { it == ConditionResult.Satisfied }
    val hasBlocked = states.any { it == ConditionResult.Unsatisfied }
    val hasUnknown = states.any {
        it == null ||
            it == ConditionResult.Unknown ||
            it == ConditionResult.Unavailable ||
            it is ConditionResult.Error
    }
    return when (mode) {
        TriggerMatchMode.ALL -> when {
            hasBlocked -> GateState.BLOCKED
            hasUnknown -> GateState.UNKNOWN
            else -> GateState.READY
        }
        TriggerMatchMode.ANY -> when {
            hasSatisfied -> GateState.READY
            hasUnknown -> GateState.UNKNOWN
            else -> GateState.BLOCKED
        }
    }
}

private fun aggregateConstraintState(
    states: List<Boolean?>,
    hasConstraints: Boolean
): GateState {
    if (!hasConstraints) return GateState.READY
    return when {
        states.any { it == false } -> GateState.BLOCKED
        states.any { it == null } -> GateState.UNKNOWN
        else -> GateState.READY
    }
}

@Composable
internal fun DetailedReadinessSection(
    automation: Automation,
    snapshot: RoutineConditionSnapshot
) {
    val readiness = routineReadinessState(automation, snapshot)
    val status = when (readiness) {
        RoutineReadinessState.READY -> DetailStatus(
            text = stringResource(R.string.task_readiness_ready),
            tone = DetailStatusTone.Success
        )
        RoutineReadinessState.WAITING -> DetailStatus(
            text = stringResource(R.string.task_readiness_waiting),
            tone = DetailStatusTone.Warning
        )
        RoutineReadinessState.UNKNOWN -> DetailStatus(
            text = stringResource(R.string.task_readiness_unknown),
            tone = DetailStatusTone.Neutral
        )
        RoutineReadinessState.DISABLED -> DetailStatus(
            text = stringResource(R.string.task_readiness_disabled),
            tone = DetailStatusTone.Neutral
        )
        RoutineReadinessState.CONFIGURATION_ERROR -> DetailStatus(
            text = stringResource(R.string.task_readiness_configuration_error),
            tone = DetailStatusTone.Error
        )
    }

    DetailSection(
        title = stringResource(R.string.task_readiness_title),
        subtitle = stringResource(R.string.task_readiness_subtitle)
    ) {
        RoutineDetailItem(
            leading = "?",
            title = stringResource(
                when (readiness) {
                    RoutineReadinessState.READY -> R.string.task_readiness_ready_detail
                    RoutineReadinessState.WAITING -> R.string.task_readiness_waiting_detail
                    RoutineReadinessState.UNKNOWN -> R.string.task_readiness_unknown_detail
                    RoutineReadinessState.DISABLED -> R.string.task_readiness_disabled_detail
                    RoutineReadinessState.CONFIGURATION_ERROR ->
                        R.string.task_readiness_configuration_error_detail
                }
            ),
            status = status
        )

        if (automation.triggers.isNotEmpty()) {
            FactRow(
                label = stringResource(R.string.task_readiness_triggers),
                value = stringResource(
                    R.string.task_readiness_fraction,
                    snapshot.triggerStates.count { it == ConditionResult.Satisfied },
                    automation.triggers.size
                )
            )
        }
        if (automation.constraints.isNotEmpty()) {
            FactRow(
                label = stringResource(R.string.task_readiness_constraints),
                value = stringResource(
                    R.string.task_readiness_fraction,
                    snapshot.constraintStates.count { it == true },
                    automation.constraints.size
                )
            )
        }
    }
}

@Composable
internal fun DetailedTriggerSection(
    automation: Automation,
    states: List<ConditionResult?>
) {
    if (automation.triggers.isEmpty()) return

    DetailSection(
        title = stringResource(R.string.task_details_triggers, automation.triggers.size),
        subtitle = stringResource(
            if (automation.triggerMatch == TriggerMatchMode.ALL) {
                R.string.task_details_trigger_mode_all
            } else {
                R.string.task_details_trigger_mode_any
            }
        )
    ) {
        automation.triggers.forEachIndexed { index, trigger ->
            TriggerDetailItem(
                index = index,
                trigger = trigger,
                state = states.getOrNull(index)
            )
            TriggerApps(trigger = trigger)
        }

        val satisfied = states.count { it == ConditionResult.Satisfied }
        val unresolved = states.count {
            it == null ||
                it == ConditionResult.Unknown ||
                it == ConditionResult.Unavailable ||
                it is ConditionResult.Error
        }
        Text(
            text = when {
                unresolved > 0 -> stringResource(
                    R.string.task_details_trigger_progress_waiting,
                    satisfied,
                    automation.triggers.size
                )
                else -> stringResource(
                    R.string.task_details_trigger_progress,
                    satisfied,
                    automation.triggers.size
                )
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TriggerDetailItem(
    index: Int,
    trigger: Trigger,
    state: ConditionResult?
) {
    val isEventOnly = TriggerStateEvaluator.isEventOnly(trigger.type)
    RoutineDetailItem(
        leading = (index + 1).toString(),
        title = stringResource(triggerLabel(trigger.type)),
        facts = routineConfigFacts(trigger.config),
        status = when {
            state == null -> DetailStatus(
                text = stringResource(R.string.task_detail_status_checking),
                tone = DetailStatusTone.Neutral
            )
            isEventOnly && state == ConditionResult.Unknown -> DetailStatus(
                text = stringResource(R.string.task_detail_status_waiting_event),
                tone = DetailStatusTone.Neutral
            )
            state == ConditionResult.Satisfied -> DetailStatus(
                text = stringResource(R.string.task_detail_status_satisfied),
                tone = DetailStatusTone.Success
            )
            state == ConditionResult.Unsatisfied -> DetailStatus(
                text = stringResource(R.string.task_detail_status_unsatisfied),
                tone = DetailStatusTone.Error
            )
            state == ConditionResult.Unknown -> DetailStatus(
                text = stringResource(R.string.task_detail_status_unknown),
                tone = DetailStatusTone.Warning
            )
            state == ConditionResult.Unavailable -> DetailStatus(
                text = stringResource(R.string.task_detail_status_unavailable),
                tone = DetailStatusTone.Warning
            )
            state is ConditionResult.Error -> DetailStatus(
                text = stringResource(R.string.task_detail_status_error),
                tone = DetailStatusTone.Error
            )
            else -> null
        }
    )
}

@Composable
internal fun DetailedConstraintSection(
    automation: Automation,
    states: List<Boolean?>
) {
    if (automation.constraints.isEmpty()) return

    DetailSection(
        title = stringResource(R.string.task_details_constraints, automation.constraints.size),
        subtitle = stringResource(R.string.task_details_constraints_all_required)
    ) {
        automation.constraints.forEachIndexed { index, constraint ->
            val satisfied = states.getOrNull(index)
            RoutineDetailItem(
                leading = (index + 1).toString(),
                title = stringResource(constraintTitleRes(constraint.type)),
                facts = routineConfigFacts(constraint.config),
                status = when (satisfied) {
                    true -> DetailStatus(
                        stringResource(R.string.task_detail_status_satisfied),
                        DetailStatusTone.Success
                    )
                    false -> DetailStatus(
                        stringResource(R.string.task_detail_status_unsatisfied),
                        DetailStatusTone.Error
                    )
                    null -> DetailStatus(
                        stringResource(
                            if (constraint.type == ConstraintType.PLUGIN) {
                                R.string.task_detail_status_provider_managed
                            } else {
                                R.string.task_detail_status_checking
                            }
                        ),
                        DetailStatusTone.Neutral
                    )
                }
            )
        }
    }
}

@Composable
internal fun DetailedActionSection(
    automation: Automation,
    latestExecution: ExecutionRecord?
) {
    if (automation.actions.isEmpty()) return

    val context = LocalContext.current
    val currentStates by produceState<List<ActionCurrentState?>>(
        initialValue = List(automation.actions.size) { null },
        automation.actions
    ) {
        if (automation.actions.none { ActionStateReader.supports(it.type) }) {
            return@produceState
        }
        while (true) {
            value = withContext(Dispatchers.IO) {
                automation.actions.map { action ->
                    runCatching {
                        ActionStateReader.read(context.applicationContext, action)
                    }.getOrNull()
                }
            }
            delay(DETAIL_STATE_REFRESH_MS)
        }
    }

    DetailSection(
        title = stringResource(R.string.task_details_actions, automation.actions.size),
        subtitle = stringResource(R.string.task_details_actions_ordered)
    ) {
        automation.actions.forEachIndexed { index, action ->
            val (titleRes, subtitleRes, _) = actionPresentation(action.type)
            val result = latestExecution.actionResultAt(index, action.type.name)
            RoutineDetailItem(
                leading = (index + 1).toString(),
                title = stringResource(titleRes),
                subtitle = stringResource(subtitleRes),
                facts = routineConfigFacts(action.config),
                status = actionResultStatus(result),
                currentState = currentStates.getOrNull(index),
                currentStateSupported = ActionStateReader.supports(action.type),
                footer = action.endBehavior
                    ?.takeIf { it.mode != EndMode.LEAVE }
                    ?.let { taskEndBehaviorDetail(action) }
                    ?.takeIf { it.isNotBlank() }
            )
        }
    }
}

@Composable
private fun actionResultStatus(result: ActionExecutionResult?): DetailStatus? {
    result ?: return DetailStatus(
        text = stringResource(R.string.task_detail_status_not_run_yet),
        tone = DetailStatusTone.Neutral
    )
    return DetailStatus(
        text = stringResource(
            if (result.success) R.string.task_detail_last_action_success
            else R.string.task_detail_last_action_failed,
            result.durationMs
        ),
        tone = if (result.success) DetailStatusTone.Success else DetailStatusTone.Error
    )
}

@Composable
internal fun DetailedEndBehaviorSection(automation: Automation) {
    val configuredPerAction = automation.actions.filter {
        it.endBehavior?.mode?.let { mode -> mode != EndMode.LEAVE } == true
    }
    if (!automation.revertOnExit && configuredPerAction.isEmpty() && automation.exitActions.isEmpty()) return

    DetailSection(
        title = stringResource(R.string.task_details_exit_actions, automation.exitBehaviorItemCount()),
        subtitle = stringResource(R.string.task_details_exit_subtitle)
    ) {
        if (automation.revertOnExit) {
            RoutineDetailItem(
                leading = "↶",
                title = stringResource(R.string.task_details_revert_on_exit),
                status = DetailStatus(
                    stringResource(R.string.task_detail_status_configured),
                    DetailStatusTone.Neutral
                )
            )
        } else {
            configuredPerAction.forEach { action ->
                val (titleRes, _, _) = actionPresentation(action.type)
                RoutineDetailItem(
                    leading = "↶",
                    title = stringResource(titleRes),
                    subtitle = taskEndBehaviorDetail(action),
                    facts = action.endBehavior?.let { routineConfigFacts(it.config) }.orEmpty(),
                    status = DetailStatus(
                        stringResource(R.string.task_detail_status_configured),
                        DetailStatusTone.Neutral
                    )
                )
            }

            automation.exitActions.forEach { action ->
                val (titleRes, subtitleRes, _) = actionPresentation(action.type)
                RoutineDetailItem(
                    leading = "→",
                    title = stringResource(titleRes),
                    subtitle = stringResource(subtitleRes),
                    facts = routineConfigFacts(action.config),
                    status = DetailStatus(
                        stringResource(R.string.task_details_runs_at_end),
                        DetailStatusTone.Neutral
                    )
                )
            }
        }
    }
}

@Composable
internal fun RoutineCustomizationSection(automation: Automation) {
    DetailSection(
        title = stringResource(R.string.task_details_customizations)
    ) {
        FactRow(
            label = stringResource(R.string.task_details_trigger_logic),
            value = stringResource(
                if (automation.triggerMatch == TriggerMatchMode.ALL) {
                    R.string.task_details_trigger_logic_all
                } else {
                    R.string.task_details_trigger_logic_any
                }
            )
        )
        FactRow(
            label = stringResource(R.string.task_details_cooldown),
            value = stringResource(R.string.task_details_seconds, automation.cooldownSeconds)
        )
        FactRow(
            label = stringResource(R.string.toast_on_toggle_title),
            value = stringResource(
                if (automation.showToastOnToggle) {
                    R.string.task_details_yes
                } else {
                    R.string.task_details_no
                }
            )
        )
    }
}

private enum class DetailStatusTone { Success, Error, Warning, Neutral }

private data class DetailStatus(
    val text: String,
    val tone: DetailStatusTone
)

@Composable
private fun DetailSection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun RoutineDetailItem(
    leading: String,
    title: String,
    subtitle: String? = null,
    facts: List<RoutineConfigFact> = emptyList(),
    status: DetailStatus? = null,
    currentState: ActionCurrentState? = null,
    currentStateSupported: Boolean = false,
    footer: String? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.56f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = leading,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    status?.let { StatusPill(it) }
                }
            }

            facts.forEach { fact ->
                FactRow(
                    label = stringResource(configFactLabelRes(fact.key)),
                    value = localizedConfigValue(fact.value)
                )
            }

            if (currentStateSupported) {
                ActionCurrentStateRow(currentState)
            }

            footer?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ActionCurrentStateRow(state: ActionCurrentState?) {
    val rawValue = state?.rawValue
    val value = when {
        state == null -> stringResource(R.string.task_detail_status_checking)
        rawValue == null -> stringResource(R.string.task_detail_status_unavailable)
        state.kind == ActionStateValueKind.BOOLEAN -> {
            if (rawValue == "true") {
                stringResource(R.string.task_details_value_on)
            } else {
                stringResource(R.string.task_details_value_off)
            }
        }
        state.kind == ActionStateValueKind.DURATION_SECONDS ->
            stringResource(R.string.task_details_seconds, rawValue.toIntOrNull() ?: 0)
        state.kind == ActionStateValueKind.RINGER_MODE -> when (rawValue) {
            "SILENT" -> stringResource(R.string.task_detail_ringer_silent)
            "VIBRATE" -> stringResource(R.string.task_detail_ringer_vibrate)
            else -> stringResource(R.string.task_detail_ringer_normal)
        }
        else -> rawValue
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.task_detail_current_state),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        when (state?.matchesTarget) {
            true -> StatusPill(
                DetailStatus(
                    text = stringResource(R.string.task_detail_target_applied),
                    tone = DetailStatusTone.Success
                )
            )
            false -> StatusPill(
                DetailStatus(
                    text = stringResource(R.string.task_detail_target_differs),
                    tone = DetailStatusTone.Warning
                )
            )
            null -> Unit
        }
    }
}

@Composable
private fun StatusPill(status: DetailStatus) {
    val container = when (status.tone) {
        DetailStatusTone.Success -> MaterialTheme.colorScheme.primaryContainer
        DetailStatusTone.Error -> MaterialTheme.colorScheme.errorContainer
        DetailStatusTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer
        DetailStatusTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when (status.tone) {
        DetailStatusTone.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        DetailStatusTone.Error -> MaterialTheme.colorScheme.onErrorContainer
        DetailStatusTone.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
        DetailStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(999.dp), color = container) {
        Text(
            text = status.text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1
        )
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal data class RoutineConfigFact(
    val key: String,
    val value: String
)

private val FACT_PRIORITY = listOf(
    "state",
    "enabled",
    "autoRotate",
    "event",
    "network",
    "mode",
    "timeMode",
    "time",
    "start",
    "end",
    "days",
    "ssid",
    "deviceName",
    "stream",
    "direction",
    "level",
    "threshold",
    "seconds",
    "minutes",
    "percent",
    "scale",
    "density",
    "dpi",
    "speed",
    "value",
    "namespace",
    "key",
    "zone",
    "from",
    "contains",
    "package",
    "packages"
)

private val SENSITIVE_CONFIG_KEYS = setOf(
    "password",
    "token",
    "secret",
    "authorization",
    "pluginApproval",
    "bundleJson",
    "body",
    "headers"
)

/**
 * Returns a stable, compact and privacy-conscious subset of config for card
 * presentation. Credentials and opaque plug-in/network payloads are never
 * rendered on the dashboard.
 */
internal fun routineConfigFacts(config: Map<String, String>): List<RoutineConfigFact> {
    if (config.isEmpty()) return emptyList()
    val blocked = SENSITIVE_CONFIG_KEYS.map { it.lowercase() }.toSet()
    return FACT_PRIORITY.asSequence()
        .mapNotNull { key ->
            val value = config[key]?.trim().orEmpty()
            if (value.isBlank() || key.lowercase() in blocked) null
            else RoutineConfigFact(key, value)
        }
        .distinctBy { it.key }
        .take(4)
        .toList()
}

@Composable
private fun localizedConfigValue(value: String): String = when (value.uppercase()) {
    "TRUE", "ON", "ENABLED" -> stringResource(R.string.task_details_value_on)
    "FALSE", "OFF", "DISABLED" -> stringResource(R.string.task_details_value_off)
    "CONNECTED" -> stringResource(R.string.task_details_value_connected)
    "DISCONNECTED" -> stringResource(R.string.task_details_value_disconnected)
    "ABOVE" -> stringResource(R.string.task_details_value_above)
    "BELOW" -> stringResource(R.string.task_details_value_below)
    else -> value
}

private fun configFactLabelRes(key: String): Int = when (key) {
    "state" -> R.string.task_detail_fact_state
    "enabled", "autoRotate" -> R.string.task_detail_fact_target_state
    "event" -> R.string.task_detail_fact_event
    "network" -> R.string.task_detail_fact_network
    "mode", "timeMode" -> R.string.task_detail_fact_mode
    "time" -> R.string.task_detail_fact_time
    "start" -> R.string.task_detail_fact_start
    "end" -> R.string.task_detail_fact_end
    "days" -> R.string.task_detail_fact_days
    "ssid" -> R.string.task_detail_fact_wifi_network
    "deviceName" -> R.string.task_detail_fact_device
    "stream" -> R.string.task_detail_fact_audio_stream
    "direction" -> R.string.task_detail_fact_condition
    "level", "threshold" -> R.string.task_detail_fact_threshold
    "seconds", "minutes" -> R.string.task_detail_fact_duration
    "percent" -> R.string.task_detail_fact_percent
    "scale" -> R.string.task_detail_fact_scale
    "density", "dpi" -> R.string.task_detail_fact_density
    "speed" -> R.string.task_detail_fact_speed
    "value" -> R.string.task_detail_fact_value
    "namespace" -> R.string.task_detail_fact_namespace
    "key" -> R.string.task_detail_fact_setting
    "zone" -> R.string.task_detail_fact_zone
    "from" -> R.string.task_detail_fact_from
    "contains" -> R.string.task_detail_fact_contains
    "package", "packages" -> R.string.task_detail_fact_apps
    else -> R.string.task_detail_fact_value
}

private fun constraintTitleRes(type: ConstraintType): Int = when (type) {
    ConstraintType.WIFI -> R.string.task_constraint_wifi
    ConstraintType.BATTERY -> R.string.task_constraint_battery
    ConstraintType.SCREEN_LOCKED -> R.string.task_constraint_screen_locked
    ConstraintType.HEADSET -> R.string.task_constraint_headset
    ConstraintType.BLUETOOTH -> R.string.task_constraint_bluetooth
    ConstraintType.DND -> R.string.task_constraint_dnd
    ConstraintType.AIRPLANE -> R.string.task_constraint_airplane
    ConstraintType.CHARGING -> R.string.task_constraint_charging
    ConstraintType.LOCATION -> R.string.task_constraint_location
    ConstraintType.PLUGIN -> R.string.task_constraint_plugin
    ConstraintType.SCHEDULE -> R.string.task_constraint_schedule
}


/**
 * Maps the latest durable per-action outcome by execution order, not by
 * ActionType. This remains correct when a task intentionally contains two
 * actions of the same type.
 */
internal fun ExecutionRecord?.actionResultAt(
    index: Int,
    expectedActionType: String
): ActionExecutionResult? =
    this?.actionResults
        ?.getOrNull(index)
        ?.takeIf { it.actionType == expectedActionType }

private const val DETAIL_STATE_REFRESH_MS = 3_000L

private const val FLOW_OVERVIEW_ITEM_LIMIT = 3

private const val HEALTH_FAILURE_PREVIEW_LIMIT = 240

private const val CONDITION_ERROR_PREVIEW_LIMIT = 1_024
private const val CONDITION_EVALUATION_FAILED = "Condition evaluation failed"
