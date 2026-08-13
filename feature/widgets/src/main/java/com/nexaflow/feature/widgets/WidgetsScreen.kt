package com.nexaflow.feature.widgets

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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.CheckableRow
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.iconVector

private data class TileModel(
    val slot: Int,
    val labelRes: Int,
    val descriptionRes: Int,
    val icon: ImageVector,
    val color: Color,
    val serviceClass: Class<out TaskTileService>
)

private val quickTiles = listOf(
    TileModel(1, R.string.tile_1_label, R.string.tile_1_desc, Icons.Filled.Bolt, Color(0xFF0B57D0), TaskTile1Service::class.java),
    TileModel(2, R.string.tile_2_label, R.string.tile_2_desc, Icons.Filled.PlayArrow, Color(0xFF006D3C), TaskTile2Service::class.java),
    TileModel(3, R.string.tile_3_label, R.string.tile_3_desc, Icons.Filled.Pause, Color(0xFF9C6ADE), TaskTile3Service::class.java),
    TileModel(4, R.string.tile_4_label, R.string.tile_4_desc, Icons.Filled.CheckCircle, Color(0xFFE8833A), TaskTile4Service::class.java),
    TileModel(5, R.string.tile_5_label, R.string.tile_5_desc, Icons.Filled.Star, Color(0xFF8F4C00), TaskTile5Service::class.java),
    TileModel(6, R.string.tile_6_label, R.string.tile_6_desc, Icons.Filled.Home, Color(0xFF006A6C), TaskTile6Service::class.java),
    TileModel(7, R.string.tile_7_label, R.string.tile_7_desc, Icons.Filled.BatteryChargingFull, Color(0xFF006D3C), TaskTile7Service::class.java),
    TileModel(8, R.string.tile_8_label, R.string.tile_8_desc, Icons.Filled.MusicNote, Color(0xFF6750A4), TaskTile8Service::class.java)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsScreen(navController: NavController, viewModel: WidgetsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val automations by viewModel.automations.collectAsStateWithLifecycle()
    val bindings by viewModel.bindings.collectAsStateWithLifecycle()
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
                val boundId = bindings[tile.slot]
                val boundTask = automations.firstOrNull { it.id == boundId }
                val boundName = boundTask?.name
                NexaFlowCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // The tile shows the bound task's own icon (the same
                            // one chosen in the task editor), falling back to the
                            // slot's default icon when unbound.
                            IconBadge(
                                icon = boundTask?.let { iconVector(it.icon) } ?: tile.icon,
                                containerColor = boundTask?.let { Color(it.iconColor) } ?: tile.color
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(tile.labelRes),
                                    style = MaterialTheme.typography.titleSmall
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
        }
    }

    bindingSlot?.let { slot ->
        TileBindingDialog(
            automations = automations,
            currentBinding = bindings[slot],
            onSelect = { automationId ->
                viewModel.setBinding(slot, automationId)
                bindingSlot = null
            },
            onDismiss = { bindingSlot = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TileBindingDialog(
    automations: List<com.nexaflow.domain.models.Automation>,
    currentBinding: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    // Google 2026: selection tasks open as a full-height modal bottom sheet.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Text(
            text = stringResource(R.string.tile_choose_task),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Google-style single-choice rows: the selected option carries
            // a trailing checkmark instead of a radio button.
            CheckableRow(
                selected = currentBinding == null,
                onClick = { onSelect(null) }
            ) {
                Text(text = stringResource(R.string.tile_automatic))
            }
            automations.forEach { automation ->
                CheckableRow(
                    selected = currentBinding == automation.id,
                    onClick = { onSelect(automation.id) }
                ) {
                    IconBadge(
                        icon = iconVector(automation.icon),
                        containerColor = Color(automation.iconColor),
                        size = 32
                    )
                    Text(
                        text = automation.name,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }
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
