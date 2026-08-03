package com.nexaflow.feature.history

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nexaflow.core.ui.EmptyState
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.StatusPill

private data class HistoryEntry(
    val id: String,
    val automationName: String,
    val time: String,
    val status: String,
    val success: Boolean
)

private val demoHistory = listOf(
    HistoryEntry("1", "Morning Routine", "Today, 06:30", "Success", true),
    HistoryEntry("2", "Low Battery Saver", "Today, 05:12", "Success", true),
    HistoryEntry("3", "Silent at Work", "Yesterday, 21:00", "Failed", false),
    HistoryEntry("4", "Morning Routine", "Yesterday, 06:30", "Success", true),
    HistoryEntry("5", "Wi-Fi Off", "Jul 2, 22:15", "Success", true)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    Scaffold(topBar = { NexaFlowTopBar(title = "History", onBack = { navController.popBackStack() }) }) { padding ->
        if (demoHistory.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.History,
                title = "No runs yet",
                subtitle = "Your automation executions will appear here."
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(demoHistory) { entry ->
                    NexaFlowCard(modifier = Modifier.clickable(onClick = { })) {
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
                                Text(text = entry.automationName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = entry.time,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            StatusPill(
                                text = entry.status,
                                background = if (entry.success) Color(0xFFE4F4E9) else Color(0xFFFBEAE7),
                                contentColor = if (entry.success) Color(0xFF2FA84F) else Color(0xFFE5533D)
                            )
                        }
                    }
                }
            }
        }
    }
}
