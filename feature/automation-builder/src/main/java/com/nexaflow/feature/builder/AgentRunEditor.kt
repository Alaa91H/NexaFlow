package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexaflow.core.agentruntime.EnabledAgent
import com.nexaflow.core.ui.SelectChip

/**
 * Editor for [com.nexaflow.domain.models.ActionType.AGENT_RUN].
 *
 * It writes exactly the keys the execution schema declares — `agentId`,
 * `prompt`, `maxDurationSeconds`, `onApprovalRequired` and `resultVariable` —
 * and only offers agents that are enabled, because a disabled agent fails the
 * run by design. Approval policy defaults to `FAIL`: a task has no dialog, so
 * waiting is never an option.
 */
@Composable
internal fun AgentRunEditor(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
    // Supplied by the screen in tests; null resolves the saved agents here.
    agents: List<EnabledAgent>? = null
) {
    if (agents != null) {
        AgentRunEditorContent(config = config, onConfigChange = onConfigChange, agents = agents)
        return
    }
    val viewModel: AgentRunChoiceViewModel = hiltViewModel()
    val saved by viewModel.agents.collectAsStateWithLifecycle()
    AgentRunEditorContent(config = config, onConfigChange = onConfigChange, agents = saved)
}

/** Stateless body; every key it writes is declared by the canonical action schema. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgentRunEditorContent(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
    agents: List<EnabledAgent>
) {
    val selectedId = config[KEY_AGENT_ID].orEmpty()
    val selected = agents.firstOrNull { it.id == selectedId }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.agent_run_agent_label),
            style = MaterialTheme.typography.titleSmall
        )
        if (agents.isEmpty()) {
            Text(
                text = stringResource(R.string.agent_run_no_agents),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                agents.forEach { agent ->
                    SelectChip(
                        selected = agent.id == selectedId,
                        onClick = { onConfigChange(config + (KEY_AGENT_ID to agent.id)) },
                        label = agent.name
                    )
                }
            }
        }
        if (selected != null) {
            val detail = selected.description.ifBlank { selected.modelId.orEmpty() }
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        OutlinedTextField(
            value = config[KEY_PROMPT].orEmpty(),
            onValueChange = { onConfigChange(config + (KEY_PROMPT to it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.agent_run_prompt_label)) },
            placeholder = { Text(text = stringResource(R.string.agent_run_prompt_hint)) },
            minLines = 2,
            maxLines = 6
        )

        OutlinedTextField(
            value = config[KEY_MAX_DURATION].orEmpty(),
            onValueChange = { raw ->
                onConfigChange(config + (KEY_MAX_DURATION to raw.filter(Char::isDigit).take(4)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.agent_run_duration_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            )
        )

        Text(
            text = stringResource(R.string.agent_run_approval_label),
            style = MaterialTheme.typography.titleSmall
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val policy = config[KEY_APPROVAL_POLICY]?.uppercase() ?: APPROVAL_FAIL
            SelectChip(
                selected = policy == APPROVAL_FAIL,
                onClick = { onConfigChange(config + (KEY_APPROVAL_POLICY to APPROVAL_FAIL)) },
                label = stringResource(R.string.agent_run_approval_fail)
            )
            SelectChip(
                selected = policy == APPROVAL_SKIP,
                onClick = { onConfigChange(config + (KEY_APPROVAL_POLICY to APPROVAL_SKIP)) },
                label = stringResource(R.string.agent_run_approval_skip)
            )
        }

        OutlinedTextField(
            value = config[KEY_RESULT_VARIABLE].orEmpty(),
            onValueChange = { raw ->
                onConfigChange(config + (KEY_RESULT_VARIABLE to raw.filter { it.isLetterOrDigit() || it == '_' }.take(64)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.agent_run_result_label)) },
            singleLine = true
        )
    }
}

private const val KEY_AGENT_ID = "agentId"
private const val KEY_PROMPT = "prompt"
private const val KEY_MAX_DURATION = "maxDurationSeconds"
private const val KEY_APPROVAL_POLICY = "onApprovalRequired"
private const val KEY_RESULT_VARIABLE = "resultVariable"
private const val APPROVAL_FAIL = "FAIL"
private const val APPROVAL_SKIP = "SKIP"
