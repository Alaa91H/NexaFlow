package com.nexaflow.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.NexaFlowFloatingActionButton

@Composable
internal fun DashboardFabActions(
    askLabel: String = stringResource(R.string.ask_nexaflow),
    newTaskLabel: String = stringResource(R.string.new_routine),
    onAsk: () -> Unit,
    onNewTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NexaFlowFloatingActionButton(
            onClick = onAsk,
            icon = Icons.Filled.Bolt,
            label = askLabel,
            modifier = Modifier.testTag("ask_nexaflow_fab")
        )
        NexaFlowFloatingActionButton(
            onClick = onNewTask,
            icon = Icons.Filled.Add,
            label = newTaskLabel,
            modifier = Modifier.testTag("new_task_fab")
        )
    }
}
