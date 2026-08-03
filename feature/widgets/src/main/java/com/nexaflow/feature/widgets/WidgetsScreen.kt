package com.nexaflow.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
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
    val componentName: String
)

private val realWidgets = listOf(
    WidgetModel(
        "1",
        "Quick Toggle",
        "Enable or disable all automations from your home screen with one tap.",
        Icons.Filled.ToggleOn,
        Color(0xFF1B62B7),
        "com.nexaflow.app.NexaFlowToggleWidgetProvider"
    ),
    WidgetModel(
        "2",
        "Status Card",
        "Live view of active automations and the last run time.",
        Icons.Filled.CheckCircle,
        Color(0xFF2FA84F),
        "com.nexaflow.app.NexaFlowStatusWidgetProvider"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsScreen(navController: NavController) {
    val context = LocalContext.current
    val installed = remember(context) { installedWidgets(context) }

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
            items(realWidgets) { widget ->
                val isInstalled = widget.componentName in installed
                NexaFlowCard {
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
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Icon(
                            imageVector = if (isInstalled) Icons.Filled.CheckCircle else Icons.Filled.Add,
                            contentDescription = null,
                            tint = if (isInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    if (!isInstalled) {
                        Text(
                            text = "Not added to your home screen yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            item {
                SectionHeader(text = "HOW TO ADD")
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
                        Column {
                            Text(
                                text = "Long-press an empty spot on your home screen, tap Widgets, then find NexaFlow.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Drag the Quick Toggle or Status Card widget onto your screen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
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
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Widgets update automatically whenever an automation runs or its state changes.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

private fun installedWidgets(context: Context): Set<String> {
    return try {
        AppWidgetManager.getInstance(context)
            .installedProviders
            .map { it.provider.className }
            .toSet()
    } catch (_: Throwable) {
        emptySet()
    }
}
