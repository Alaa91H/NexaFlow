package com.nexaflow.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.EmptyState
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.StatusPill
import com.nexaflow.domain.models.ExecutionRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val history by viewModel.history.collectAsStateWithLifecycle()

    Scaffold(topBar = { NexaFlowTopBar(title = stringResource(R.string.history_title), onBack = { navController.popBackStack() }) }) { padding ->
        if (history.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.History,
                title = stringResource(R.string.no_runs_title),
                subtitle = stringResource(R.string.no_runs_subtitle)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history) { entry ->
                    HistoryCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: ExecutionRecord) {
    val timeFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    NexaFlowCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(
                icon = Icons.Filled.Bolt,
                containerColor = if (entry.success) Color(0xFFE4F4E9) else Color(0xFFFBEAE7),
                contentColor = if (entry.success) Color(0xFF2FA84F) else Color(0xFFE5533D)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.automationName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
                Text(
                    text = timeFormat.format(Date(entry.executedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            StatusPill(
                text = if (entry.success) stringResource(R.string.status_success) else stringResource(R.string.status_failed),
                background = if (entry.success) Color(0xFFE4F4E9) else Color(0xFFFBEAE7),
                contentColor = if (entry.success) Color(0xFF2FA84F) else Color(0xFFE5533D)
            )
        }
    }
}
