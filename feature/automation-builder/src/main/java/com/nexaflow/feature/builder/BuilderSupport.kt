package com.nexaflow.feature.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nexaflow.core.execution.TriggerMatchPolicy
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.RoutineTemplateCatalog
import com.nexaflow.domain.models.Trigger

internal fun starterRoutineTitleRes(templateId: String): Int = when (templateId) {
    RoutineTemplateCatalog.SLEEP -> R.string.starter_template_sleep
    RoutineTemplateCatalog.LOW_BATTERY -> R.string.starter_template_low_battery
    RoutineTemplateCatalog.CHARGING -> R.string.starter_template_charging
    RoutineTemplateCatalog.DAILY_APP_MAINTENANCE -> R.string.starter_template_daily_app_maintenance
    RoutineTemplateCatalog.WEEKLY_STORAGE_CLEANUP -> R.string.starter_template_weekly_storage_cleanup
    RoutineTemplateCatalog.NIGHTLY_AUTOMATION_SYNC -> R.string.starter_template_nightly_automation_sync
    RoutineTemplateCatalog.SCHEDULED_SMS -> R.string.starter_template_scheduled_sms
    RoutineTemplateCatalog.NIGHTLY_CALL_SILENCE -> R.string.starter_template_nightly_call_silence
    else -> R.string.builder_title
}

/**
 * Packages referenced by other saved tasks (triggers and actions), most
 * recently saved first. Feeds the app picker's recents section; the task
 * being edited is excluded so the section shows cross-task history only.
 */
internal fun packagesUsedByOtherTasks(
    viewModel: com.nexaflow.feature.builder.AutomationBuilderViewModel,
    excludeAutomationId: String?
): List<String> {
    return viewModel.automations.value
        .filter { it.id != excludeAutomationId }
        .flatMap { automation ->
            automation.triggers.mapNotNull { it.config["packages"] ?: it.config["package"] } +
                automation.actions.mapNotNull { it.config["packages"] ?: it.config["package"] }
        }
        .flatMap { it.split(',') }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

/**
 * ANY/ALL selector shown in the Conditions step once the task has two or
 * more triggers. ANY preserves the historical fire-on-any-event semantics;
 * ALL requires every configured trigger to be verifiably true at fire time
 * (the firing monitor merely starts the evaluation).
 */
/**
 * Draft-level event-only check for the ALL-mode advisory. Delegates to
 * TriggerMatchPolicy.isEventOnly — the single source of truth the engine and
 * manual gate also use — so the builder's explanation cannot drift from
 * runtime semantics. Momentary triggers are proven by the current occurrence;
 * the advisory explains that every state-readable sibling must be true at that
 * same moment.
 */
internal fun triggerMatchBuiltWarning(
    triggers: List<TriggerDraft>
): TriggerMatchPolicy.AllModeEventSemantics {
    if (triggers.size < 2) return TriggerMatchPolicy.AllModeEventSemantics.NONE
    return TriggerMatchPolicy.allModeEventSemantics(
        triggers.map { draft -> Trigger(draft.type, draft.config) }
    )
}

@Composable
internal fun TriggerMatchSelector(
    selected: TriggerMatchMode,
    onSelect: (TriggerMatchMode) -> Unit,
    allModeSemantics: TriggerMatchPolicy.AllModeEventSemantics =
        TriggerMatchPolicy.AllModeEventSemantics.NONE,
    legacyReviewRequired: Boolean = false,
) {
    Column {
        Text(
            text = stringResource(R.string.trigger_match_title),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TriggerMatchMode.entries.forEach { mode ->
                val isSelected = mode == selected
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelect(mode) }
                    )
                    Column {
                        Text(
                            text = stringResource(
                                if (mode == TriggerMatchMode.ANY) R.string.trigger_match_any
                                else R.string.trigger_match_all
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(
                                if (mode == TriggerMatchMode.ANY) R.string.trigger_match_any_hint
                                else R.string.trigger_match_all_hint
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (legacyReviewRequired) {
            Text(
                text = stringResource(R.string.trigger_match_legacy_review_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (
            selected == TriggerMatchMode.ALL &&
            allModeSemantics != TriggerMatchPolicy.AllModeEventSemantics.NONE
        ) {
            Text(
                text = stringResource(
                    if (
                        allModeSemantics ==
                        TriggerMatchPolicy.AllModeEventSemantics.SAME_OCCURRENCE_REQUIRED
                    ) {
                        R.string.trigger_match_all_multi_event_warning
                    } else {
                        R.string.trigger_match_all_event_warning
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

