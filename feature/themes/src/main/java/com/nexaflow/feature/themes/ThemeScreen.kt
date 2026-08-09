package com.nexaflow.feature.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.datastore.ThemeMode
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader

private data class AccentOption(val key: String, val color: Color, val labelRes: Int)

private val accentOptions = listOf(
    AccentOption("blue", Color(0xFF1B62B7), R.string.accent_blue),
    AccentOption("green", Color(0xFF2FA84F), R.string.accent_green),
    AccentOption("red", Color(0xFFE5533D), R.string.accent_red),
    AccentOption("purple", Color(0xFF7A5BD1), R.string.accent_purple),
    AccentOption("amber", Color(0xFFE8A33D), R.string.accent_amber),
    AccentOption("teal", Color(0xFF13A5A8), R.string.accent_teal)
)

private data class ModeOption(
    val mode: ThemeMode,
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector
)

private val modeOptions = listOf(
    ModeOption(ThemeMode.SYSTEM, R.string.mode_system, R.string.mode_system_sub, Icons.Filled.Contrast),
    ModeOption(ThemeMode.LIGHT, R.string.mode_light, R.string.mode_light_sub, Icons.Filled.LightMode),
    ModeOption(ThemeMode.DARK, R.string.mode_dark, R.string.mode_dark_sub, Icons.Filled.DarkMode)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(navController: NavController) {
    val viewModel: ThemeViewModel = hiltViewModel()
    val theme by viewModel.theme.collectAsStateWithLifecycle()

    Scaffold(topBar = { NexaFlowTopBar(title = stringResource(R.string.themes_title), onBack = { navController.popBackStack() }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(text = stringResource(R.string.section_appearance))
            }
            item {
                NexaFlowCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        modeOptions.forEach { option ->
                            val selected = theme.mode == option.mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setThemeMode(option.mode) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconBadge(
                                    icon = option.icon,
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(option.titleRes),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(option.subtitleRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (theme.mode == ThemeMode.SYSTEM) {
                                stringResource(R.string.theme_follows_system)
                            } else {
                                stringResource(R.string.theme_manual)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        // Material You (P2-7): wallpaper colors on Android 12+.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconBadge(
                                    icon = Icons.Filled.Palette,
                                    containerColor = if (theme.dynamicColor) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.dynamic_color),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.dynamic_color_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Switch(
                                    checked = theme.dynamicColor,
                                    onCheckedChange = { viewModel.setDynamicColor(it) }
                                )
                            }
                        }
                    }
                }
            }
            item {
                SectionHeader(text = stringResource(R.string.section_accent))
            }
            item {
                NexaFlowCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        accentOptions.forEach { option ->
                            val selected = theme.accent == option.key
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(color = option.color, shape = CircleShape)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.setAccent(option.key) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.accent_selected,
                            stringResource(accentOptions.firstOrNull { it.key == theme.accent }?.labelRes ?: R.string.accent_blue)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            item {
                SectionHeader(text = stringResource(R.string.section_preview))
            }
            item {
                NexaFlowCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconBadge(
                                icon = Icons.Filled.Palette,
                                containerColor = accentOptions.firstOrNull { it.key == theme.accent }?.color ?: Color(0xFF1B62B7)
                            )
                            Column {
                                Text(text = stringResource(R.string.theme_name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = when (theme.mode) {
                                        ThemeMode.SYSTEM -> stringResource(R.string.preview_system)
                                        ThemeMode.DARK -> stringResource(R.string.preview_dark)
                                        ThemeMode.LIGHT -> stringResource(R.string.preview_light)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = accentOptions.firstOrNull { it.key == theme.accent }?.color ?: Color(0xFF1B62B7),
                                    shape = MaterialTheme.shapes.medium
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.primary_accent),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
