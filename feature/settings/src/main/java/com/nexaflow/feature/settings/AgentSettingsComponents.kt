package com.nexaflow.feature.settings

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.nexaflow.core.agentapi.AgentApiAuditEventV1
import com.nexaflow.core.agentsecurity.AgentGrantRecord
import com.nexaflow.core.agentsecurity.AgentGrantMode
import com.nexaflow.core.database.AgentAutomationApprovalEntity
import com.nexaflow.core.ui.NexaFlowCard
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AgentApprovalSettings(state: AgentSettingsUiState, viewModel: AgentSettingsViewModel) {
    var approvalUnderReview by remember { mutableStateOf<AgentAutomationApprovalEntity?>(null) }
    NexaFlowCard {
        Text(stringResource(R.string.agent_automation_approval_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.agent_automation_approval_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.pendingAutomationApprovals.isEmpty()) Text(stringResource(R.string.agent_automation_approval_none))
        state.pendingAutomationApprovals.forEach { approval ->
            androidx.compose.material3.HorizontalDivider()
            Text(stringResource(R.string.agent_automation_approval_pending, approval.riskLevel, approval.agentId, DateFormat.getDateTimeInstance().format(Date(approval.expiresAt))))
            TextButton(onClick = { approvalUnderReview = approval }) { Text(stringResource(R.string.agent_automation_approval_review)) }
        }
        state.latestAutomationApprovalId?.let { Text(stringResource(R.string.agent_automation_approval_token, it)) }
    }
    approvalUnderReview?.let { approval ->
        AlertDialog(
            onDismissRequest = { approvalUnderReview = null },
            title = {
                Text(stringResource(R.string.agent_automation_approval_review_title, approval.riskLevel))
            },
            text = {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.agent_automation_approval_agent_id, approval.agentId))
                        Text(approval.definitionSummary.ifBlank {
                            stringResource(R.string.agent_automation_approval_summary_unavailable)
                        })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.approveAutomation(
                        approval.id,
                        approval.agentId,
                        approval.contentHash,
                        approval.riskLevel
                    )
                    approvalUnderReview = null
                }) {
                    Text(stringResource(R.string.agent_automation_approval_confirm))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.rejectAutomation(approval)
                        approvalUnderReview = null
                    }) {
                        Text(stringResource(R.string.agent_automation_approval_reject))
                    }
                    TextButton(onClick = { approvalUnderReview = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }
}

@Composable
internal fun TrustedAgentCard(
    agent: AgentGrantRecord,
    dateFormat: DateFormat,
    onReducePermissions: () -> Unit,
    onRevoke: () -> Unit
) {
    NexaFlowCard {
        Text(
            text = agent.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = agent.agentId,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                when (agent.mode) {
                    AgentGrantMode.READ_ONLY -> R.string.agent_grant_mode_read_only
                    AgentGrantMode.STANDARD -> R.string.agent_grant_mode_standard
                    AgentGrantMode.TIMED_FULL_ACCESS -> R.string.agent_grant_mode_timed_full_access
                    AgentGrantMode.PERMANENT_FULL_ACCESS,
                    AgentGrantMode.UNKNOWN -> R.string.agent_full_access
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        agent.lastUsedAt?.let {
            Text(
                text = dateFormat.format(Date(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (agent.mode == AgentGrantMode.PERMANENT_FULL_ACCESS ||
            agent.mode == AgentGrantMode.UNKNOWN
        ) {
            Text(
                stringResource(R.string.agent_permanent_access_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onReducePermissions) {
                Text(stringResource(R.string.agent_reduce_permissions))
            }
        }
        TextButton(onClick = onRevoke) {
            Text(stringResource(R.string.agent_revoke))
        }
    }
}

@Composable
internal fun AgentActivityRow(
    event: AgentApiAuditEventV1,
    dateFormat: DateFormat
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = event.eventType,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = event.outcome,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        val subject = listOfNotNull(event.agentId, event.automationId)
            .joinToString(" · ")
        if (subject.isNotBlank()) {
            Text(
                text = subject,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = dateFormat.format(Date(event.createdAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun AgentActivitySection(
    events: List<AgentApiAuditEventV1>,
    dateFormat: DateFormat,
    onClear: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(stringResource(R.string.agent_activity_title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onClear) { Text(stringResource(R.string.agent_activity_clear)) }
    }
    if (events.isEmpty()) {
        NexaFlowCard { Text(stringResource(R.string.agent_activity_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        NexaFlowCard {
            events.forEachIndexed { index, event ->
                AgentActivityRow(event, dateFormat)
                if (index != events.lastIndex) androidx.compose.material3.HorizontalDivider(modifier = androidx.compose.ui.Modifier.padding(vertical = 8.dp))
            }
        }
    }
}
