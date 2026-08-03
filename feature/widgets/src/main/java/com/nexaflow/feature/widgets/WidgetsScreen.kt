package com.nexaflow.feature.widgets

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nexaflow.core.ui.EmptyState
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader

private data class WidgetModel(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val installed: Boolean
)

private val demoWidgets = listOf(
    WidgetModel("1", "Quick Toggle", "Enable or disable automation from the home screen", Icons.Filled.ToggleOn, Color(0xFF1B62B7), true),
    WidgetModel("2", "Status Card", "Live view of active automations", Icons.Filled.CheckCircle, Color(0xFF2FA84F), true),
    WidgetModel("3", "Power Action", "One-tap run any automation", Icons.Filled.Bolt, Color(0xFFE5533D), false)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsScreen(navController: NavController) {
    Scaffold(topBar = { NexaFlowTopBar(title = "Widgets", onBack = { navController.popBackStack() }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(text = "AVAILABLE WIDGETS")
            }
            if (demoWidgets.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Widgets,
                        title = "No widgets",
                        subtitle = "Add widgets to control your automations from the home screen."
                    )
                }
            }
            items(demoWidgets) { widget ->
                NexaFlowCard(modifier = Modifier.clickable(onClick = { })) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconBadge(icon = widget.icon, containerColor = widget.color)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = widget.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = widget.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 2
                            )
                        }
                        Icon(
                            imageVector = if (widget.installed) Icons.Filled.CheckCircle else Icons.Filled.Add,
                            contentDescription = null,
                            tint = if (widget.installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            item {
                SectionHeader(text = "TIP")
            }
            item {
                NexaFlowCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Hold the widget on your home screen to resize or edit it.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
