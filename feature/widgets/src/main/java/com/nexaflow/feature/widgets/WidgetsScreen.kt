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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader

private data class WidgetModel(
    val id: String,
    val nameRes: Int,
    val descriptionRes: Int,
    val icon: ImageVector,
    val color: Color,
    val componentName: String
)

private val realWidgets = listOf(
    WidgetModel(
        "1",
        R.string.widget_quick_toggle,
        R.string.widget_quick_toggle_desc,
        Icons.Filled.ToggleOn,
        Color(0xFF1B62B7),
        "com.nexaflow.app.NexaFlowToggleWidgetProvider"
    ),
    WidgetModel(
        "2",
        R.string.widget_status_card,
        R.string.widget_status_card_desc,
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

    Scaffold(topBar = { NexaFlowTopBar(title = stringResource(R.string.widgets_title), onBack = { navController.popBackStack() }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(text = stringResource(R.string.section_available))
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
                            Text(text = stringResource(widget.nameRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = stringResource(widget.descriptionRes),
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
                            text = stringResource(R.string.widget_not_added),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            item {
                SectionHeader(text = stringResource(R.string.section_how_to))
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
                                text = stringResource(R.string.how_to_text),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.how_to_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            item {
                SectionHeader(text = stringResource(R.string.section_tip))
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
                            text = stringResource(R.string.tip_text),
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
