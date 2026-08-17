package com.nexaflow.feature.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nexaflow.core.ui.NexaFlowIcons
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SelectChip

/**
 * Ready-made accent palette (Google 2026 tones) shown as a fixed strip at the
 * top of the picker. The chosen color is returned to the builder alongside the
 * icon and tinted the selected icon cell.
 */
val IconPickerPalette: List<Color> = listOf(
    Color(0xFF0B57D0), // blue
    Color(0xFFD93025), // red
    Color(0xFFE37400), // orange
    Color(0xFFF9AB00), // yellow
    Color(0xFF188038), // green
    Color(0xFF007B83), // teal
    Color(0xFF12B5CB), // cyan
    Color(0xFF3949AB), // indigo
    Color(0xFF9334E6), // purple
    Color(0xFFD01884)  // pink
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IconPickerScreen(navController: NavController) {
    var selectedIndex by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }

    // Preseed the palette with the task's current color when the builder set
    // it on its own handle before navigating here (see the builder's card).
    val preselectedColor = remember {
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.get<Long>("selected_color")
            ?.let { Color(it) }
    }
    var selectedColor by remember {
        mutableStateOf(preselectedColor ?: IconPickerPalette.first())
    }

    // Resolve the persisted selection back to the canonical list index so the
    // "Done" button keeps returning the same name the builder persists.
    val filtered = remember(query, category) {
        NexaFlowIcons.search(query, category)
    }
    val selected = remember(filtered, selectedIndex) {
        // Keep the selection stable when filtering changes: fall back to the
        // first visible icon instead of pointing at a hidden one.
        filtered.getOrNull(selectedIndex) ?: filtered.firstOrNull()
    }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.choose_icon_title),
                onBack = { navController.popBackStack() }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        val name = selected?.name
                            ?: NexaFlowIcons.all.first().first
                        val index = NexaFlowIcons.all.indexOfFirst { it.first == name }
                        val handle = navController.previousBackStackEntry?.savedStateHandle
                        handle?.set("selected_icon", index)
                        handle?.set("selected_color", selectedColor.toArgb().toLong())
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.done))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Fixed header (never scrolls away): search, color palette, categories ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = stringResource(R.string.search_icons)) },
                    singleLine = true
                )
                Text(
                    text = stringResource(R.string.icon_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconPickerPalette.forEach { color ->
                        val isSelected = color == selectedColor
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SelectChip(
                        selected = category == null,
                        onClick = { category = null },
                        label = stringResource(R.string.category_all)
                    )
                    NexaFlowIcons.categories.forEach { cat ->
                        val isSelected = category == cat
                        SelectChip(
                            selected = isSelected,
                            onClick = { category = cat },
                            label = stringResource(categoryLabelRes(cat))
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(56.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.name }) { entry ->
                    val isSelected = entry.name == selected?.name
                    // Google-style tonal circle: fully filled when selected,
                    // soft tinted when idle — the chosen color personalizes it.
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) selectedColor
                                else selectedColor.copy(alpha = 0.12f)
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = selectedColor,
                                shape = CircleShape
                            )
                            .clickable { selectedIndex = filtered.indexOfFirst { it.name == entry.name } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = entry.name,
                            tint = if (isSelected) Color.White else selectedColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun categoryLabelRes(category: String): Int = when (category) {
    NexaFlowIcons.CATEGORY_CONNECTIVITY -> R.string.category_connectivity
    NexaFlowIcons.CATEGORY_SOUND -> R.string.category_sound
    NexaFlowIcons.CATEGORY_DISPLAY -> R.string.category_display
    NexaFlowIcons.CATEGORY_MEDIA -> R.string.category_media
    NexaFlowIcons.CATEGORY_SYSTEM -> R.string.category_system
    NexaFlowIcons.CATEGORY_BATTERY -> R.string.category_battery
    NexaFlowIcons.CATEGORY_TIME -> R.string.category_time
    NexaFlowIcons.CATEGORY_LOCATION -> R.string.category_location
    else -> R.string.category_general
}
