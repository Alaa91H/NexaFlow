package com.nexaflow.feature.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nexaflow.core.ui.NexaFlowIcons
import com.nexaflow.core.ui.NexaFlowTopBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IconPickerScreen(navController: NavController) {
    var selectedIndex by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }

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
                        navController.previousBackStackEntry?.savedStateHandle?.set("selected_icon", index)
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.done))
                }
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(4) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = stringResource(R.string.search_icons)) },
                    singleLine = true
                )
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(4) }) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = category == null,
                        onClick = { category = null },
                        leadingIcon = {
                            if (category == null) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.category_all),
                                fontWeight = if (category == null) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    NexaFlowIcons.categories.forEach { cat ->
                        val isSelected = category == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { category = cat },
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = stringResource(categoryLabelRes(cat)),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
            items(filtered) { entry ->
                val isSelected = entry.name == selected?.name
                Icon(
                    imageVector = entry.icon,
                    contentDescription = entry.name,
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .border(
                            width = if (isSelected) 0.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { selectedIndex = filtered.indexOfFirst { it.name == entry.name } },
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary
                )
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
