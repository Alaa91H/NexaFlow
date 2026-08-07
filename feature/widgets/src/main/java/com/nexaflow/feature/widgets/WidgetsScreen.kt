package com.nexaflow.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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

private data class TileModel(
    val slot: Int,
    val labelRes: Int,
    val descriptionRes: Int,
    val icon: ImageVector,
    val color: Color,
    val serviceClass: Class<out TaskTileService>
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

private val quickTiles = listOf(
    TileModel(1, R.string.tile_1_label, R.string.tile_1_desc, Icons.Filled.Bolt, Color(0xFF1B62B7), TaskTile1Service::class.java),
    TileModel(2, R.string.tile_2_label, R.string.tile_2_desc, Icons.Filled.PlayArrow, Color(0xFF2FA84F), TaskTile2Service::class.java),
    TileModel(3, R.string.tile_3_label, R.string.tile_3_desc, Icons.Filled.Pause, Color(0xFF9C6ADE), TaskTile3Service::class.java),
    TileModel(4, R.string.tile_4_label, R.string.tile_4_desc, Icons.Filled.CheckCircle, Color(0xFFE8833A), TaskTile4Service::class.java)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsScreen(navController: NavController, viewModel: WidgetsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val installed = remember(context) { installedWidgets(context) }
    val automations by viewModel.automations.collectAsState()
    var bindingSlot by remember { mutableStateOf<Int?>(null) }

    Scaffold(topBar = { NexaFlowTopBar(title = stringResource(R.string.widgets_title), onBack = { navController.popBackStack() }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(text = stringResource(R.string.section_tiles))
            }
            items(quickTiles) { tile ->
                val boundId = viewModel.bindingFor(tile.slot)
                val boundName = automations.firstOrNull { it.id == boundId }?.name
                NexaFlowCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconBadge(icon = tile.icon, containerColor = tile.color)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(tile.labelRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(tile.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.tile_controls),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = boundName ?: stringResource(R.string.tile_automatic),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            TextButton(onClick = { bindingSlot = tile.slot }) {
                                Text(text = stringResource(R.string.tile_choose_task))
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Button(
                                onClick = {
                                    requestAddTile(context, ComponentName(context, tile.serviceClass))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.tile_add),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.tile_requires_android_13),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
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

    bindingSlot?.let { slot ->
        TileBindingDialog(
            automations = automations,
            currentBinding = viewModel.bindingFor(slot),
            onSelect = { automationId ->
                viewModel.setBinding(slot, automationId)
                bindingSlot = null
            },
            onDismiss = { bindingSlot = null }
        )
    }
}

@Composable
private fun TileBindingDialog(
    automations: List<com.nexaflow.domain.models.Automation>,
    currentBinding: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.tile_choose_task)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = currentBinding == null, onClick = { onSelect(null) })
                    Text(text = stringResource(R.string.tile_automatic))
                }
                automations.forEach { automation ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(automation.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentBinding == automation.id, onClick = { onSelect(automation.id) })
                        Text(
                            text = automation.name,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.tile_cancel))
            }
        }
    )
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

/**
 * Opens the system "Add tile" dialog (API 33+). The compile-time stub jar for
 * the SDK used by this project does not expose
 * [TileService.requestAddTileService], so it is invoked reflectively — the
 * method exists on every device running Android 13+.
 */
private fun requestAddTile(context: Context, component: ComponentName) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    try {
        val method = TileService::class.java.getMethod(
            "requestAddTileService",
            Context::class.java,
            ComponentName::class.java
        )
        method.invoke(null, context, component)
    } catch (_: Throwable) {
        // Best effort; tiles can also be added from the system edit panel.
    }
}
