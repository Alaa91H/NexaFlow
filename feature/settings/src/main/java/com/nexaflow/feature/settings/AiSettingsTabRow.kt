package com.nexaflow.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

enum class AiSettingsTab { AGENTS, LOGS, ADD_AGENT }

@Composable
internal fun AiSettingsTabRow(
    selected: AiSettingsTab,
    onSelect: (AiSettingsTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AiSettingsTab.entries.forEach { tab ->
            FilterChip(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                label = {
                    Text(
                        stringResource(
                            when (tab) {
                                AiSettingsTab.AGENTS -> R.string.ai_tab_agents
                                AiSettingsTab.LOGS -> R.string.ai_tab_logs
                                AiSettingsTab.ADD_AGENT -> R.string.ai_tab_add_agent
                            }
                        )
                    )
                }
            )
        }
    }
}
