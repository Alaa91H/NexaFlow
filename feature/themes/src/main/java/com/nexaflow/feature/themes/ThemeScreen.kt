package com.nexaflow.feature.themes

import android.os.Build
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.datastore.ThemeMode
import com.nexaflow.core.datastore.ThemeSettings
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowGradientIcon
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SettingRow
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.theme.NexaFlowAccentSeeds
import com.nexaflow.core.ui.theme.NexaFlowBackground
import com.nexaflow.core.ui.theme.NexaFlowGradients

private data class AccentOption(val key: String, val labelRes: Int)

/**
 * The accents the appearance screen offers.
 *
 * The swatches are read from the shared palette, so a swatch shows exactly
 * the color the scheme is built from.
 */
private val accentOptions = listOf(
    AccentOption("indigo", R.string.accent_indigo),
    AccentOption("teal", R.string.accent_teal),
    AccentOption("violet", R.string.accent_violet),
    AccentOption("emerald", R.string.accent_emerald),
    AccentOption("amber", R.string.accent_amber),
    AccentOption("rose", R.string.accent_rose)
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

private data class BackgroundOption(
    val background: NexaFlowBackground,
    val titleRes: Int,
    val gradient: List<Color>
)

private val backgroundOptions = listOf(
    BackgroundOption(NexaFlowBackground.AMBIENT, R.string.background_ambient, NexaFlowGradients.brand),
    BackgroundOption(NexaFlowBackground.EMBER, R.string.background_ember, NexaFlowGradients.items[4]),
    BackgroundOption(NexaFlowBackground.QUIET, R.string.background_quiet, NexaFlowGradients.items[1]),
    BackgroundOption(NexaFlowBackground.NONE, R.string.background_none, emptyList())
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(navController: NavController) {
    val viewModel: ThemeViewModel = hiltViewModel()
    val theme by viewModel.theme.collectAsStateWithLifecycle()

    ThemeScreenContent(
        theme = theme,
        onModeChange = viewModel::setThemeMode,
        onAccentChange = viewModel::setAccent,
        onDynamicColorChange = viewModel::setDynamicColor,
        onMonochromeChange = viewModel::setMonochrome,
        onColorAccentsChange = viewModel::setColorAccents,
        onOutlinesChange = viewModel::setOutlines,
        onBlurChange = viewModel::setBlur,
        onAmbientChange = viewModel::setAmbientBackground,
        onDensityChange = viewModel::setDensity,
        onBackgroundChange = viewModel::setBackground,
        onPureBlackChange = viewModel::setPureBlack,
        onBack = { navController.popBackStack() }
    )
}

/**
 * The appearance screen.
 *
 * The sections compose rather than sit next to each other: monochrome
 * flattens the accent swatches, Dynamic color overrides the accent, the
 * ambient switch empties the background preview. Each section says what it
 * does, and the preview at the bottom shows the result of the combination
 * rather than of the last thing that changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreenContent(
    theme: ThemeSettings,
    onModeChange: (ThemeMode) -> Unit,
    onAccentChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onMonochromeChange: (Boolean) -> Unit,
    onColorAccentsChange: (Boolean) -> Unit,
    onOutlinesChange: (Boolean) -> Unit,
    onBlurChange: (Boolean) -> Unit,
    onAmbientChange: (Boolean) -> Unit,
    onDensityChange: (String) -> Unit,
    onBackgroundChange: (String) -> Unit,
    onPureBlackChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(topBar = { NexaFlowTopBar(title = stringResource(R.string.themes_title), onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Theme -----------------------------------------------------
            item {
                SectionHeader(
                    text = stringResource(R.string.section_appearance),
                    icon = Icons.Filled.DarkMode
                )
            }
            item {
                NexaFlowCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        modeOptions.forEach { option ->
                            val selected = theme.mode == option.mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onModeChange(option.mode) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconBadge(
                                    icon = option.icon,
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(option.titleRes),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = stringResource(option.subtitleRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        if (theme.mode != ThemeMode.LIGHT) {
                            SettingRow(
                                icon = Icons.Filled.Contrast,
                                title = stringResource(R.string.pure_black),
                                subtitle = stringResource(R.string.pure_black_sub),
                                trailing = {
                                    Switch(
                                        checked = theme.pureBlack,
                                        onCheckedChange = onPureBlackChange
                                    )
                                }
                            )
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SettingRow(
                                icon = Icons.Filled.Palette,
                                title = stringResource(R.string.dynamic_color),
                                subtitle = stringResource(R.string.dynamic_color_sub),
                                trailing = {
                                    Switch(
                                        checked = theme.dynamicColor,
                                        onCheckedChange = onDynamicColorChange
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // --- Accent -----------------------------------------------------
            item {
                SectionHeader(
                    text = stringResource(R.string.section_accent),
                    icon = Icons.Filled.Palette
                )
            }
            item {
                NexaFlowCard {
                    val accentsEnabled = !theme.dynamicColor && !theme.monochrome
                    val accentAlpha = if (accentsEnabled) 1f else 0.38f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(accentAlpha),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        accentOptions.forEach { option ->
                            val selected = theme.accent == option.key
                            val seed = NexaFlowAccentSeeds[option.key]
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        color = seed?.lightPrimary ?: MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = CircleShape
                                    )
                                    .then(
                                        if (accentsEnabled) {
                                            Modifier.clickable { onAccentChange(option.key) }
                                        } else {
                                            Modifier
                                        }
                                    ),
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
                        text = if (accentsEnabled) {
                            stringResource(
                                R.string.accent_selected,
                                stringResource(
                                    accentOptions.firstOrNull { it.key == theme.accent }?.labelRes
                                        ?: R.string.accent_indigo
                                )
                            )
                        } else if (theme.monochrome) {
                            stringResource(R.string.accent_disabled_mono)
                        } else {
                            stringResource(R.string.accent_disabled_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            // --- Background -------------------------------------------------
            item {
                SectionHeader(
                    text = stringResource(R.string.section_background),
                    icon = Icons.Filled.Gradient
                )
            }
            item {
                NexaFlowCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        backgroundOptions.forEach { option ->
                            val selected = theme.background == option.background.storedId
                            val enabled = theme.ambientBackground
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (enabled) 1f else 0.4f)
                                    .clickable(enabled = enabled) {
                                        onBackgroundChange(option.background.storedId)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            brush = Brush.linearGradient(
                                                if (option.gradient.isEmpty()) {
                                                    listOf(
                                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                                    )
                                                } else {
                                                    option.gradient
                                                }
                                            ),
                                            shape = CircleShape
                                        )
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                            shape = CircleShape
                                        ),
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
                                Text(
                                    text = stringResource(option.titleRes),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }
                }
            }

            // --- Style ------------------------------------------------------
            item {
                SectionHeader(
                    text = stringResource(R.string.section_traits),
                    icon = Icons.Filled.Tune
                )
            }
            item {
                NexaFlowCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingRow(
                            icon = Icons.Filled.Contrast,
                            title = stringResource(R.string.trait_monochrome),
                            subtitle = stringResource(R.string.trait_monochrome_sub),
                            trailing = {
                                Switch(
                                    checked = theme.monochrome,
                                    onCheckedChange = onMonochromeChange
                                )
                            }
                        )
                        SettingRow(
                            icon = Icons.Filled.Palette,
                            title = stringResource(R.string.trait_item_colors),
                            subtitle = stringResource(R.string.trait_item_colors_sub),
                            trailing = {
                                Switch(
                                    checked = theme.colorAccents,
                                    onCheckedChange = onColorAccentsChange
                                )
                            }
                        )
                        SettingRow(
                            icon = Icons.Filled.Tune,
                            title = stringResource(R.string.trait_outlines),
                            subtitle = stringResource(R.string.trait_outlines_sub),
                            trailing = {
                                Switch(
                                    checked = theme.outlines,
                                    onCheckedChange = onOutlinesChange
                                )
                            }
                        )
                        SettingRow(
                            icon = Icons.Filled.Gradient,
                            title = stringResource(R.string.trait_translucent),
                            subtitle = stringResource(R.string.trait_translucent_sub),
                            trailing = {
                                Switch(
                                    checked = theme.blur,
                                    onCheckedChange = onBlurChange
                                )
                            }
                        )
                        SettingRow(
                            icon = Icons.Filled.Gradient,
                            title = stringResource(R.string.trait_ambient),
                            subtitle = stringResource(R.string.trait_ambient_sub),
                            trailing = {
                                Switch(
                                    checked = theme.ambientBackground,
                                    onCheckedChange = onAmbientChange
                                )
                            }
                        )
                    }
                }
            }

            // --- Density ----------------------------------------------------
            item {
                SectionHeader(
                    text = stringResource(R.string.section_density),
                    icon = Icons.Filled.Tune
                )
            }
            item {
                NexaFlowCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DensityOption(
                            title = stringResource(R.string.density_comfortable),
                            selected = theme.density != "compact",
                            modifier = Modifier.weight(1f),
                            onClick = { onDensityChange("comfortable") }
                        )
                        DensityOption(
                            title = stringResource(R.string.density_compact),
                            selected = theme.density == "compact",
                            modifier = Modifier.weight(1f),
                            onClick = { onDensityChange("compact") }
                        )
                    }
                }
            }

            // --- Preview ----------------------------------------------------
            item {
                SectionHeader(
                    text = stringResource(R.string.section_preview),
                    icon = Icons.Filled.Palette
                )
            }
            item {
                NexaFlowCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NexaFlowGradientIcon(
                                icon = Icons.Filled.Palette,
                                gradient = NexaFlowGradients.brand
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.theme_name),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = when (theme.mode) {
                                        ThemeMode.SYSTEM -> stringResource(R.string.preview_system)
                                        ThemeMode.DARK -> stringResource(R.string.preview_dark)
                                        ThemeMode.LIGHT -> stringResource(R.string.preview_light)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        NexaFlowAccentSeeds[theme.accent]?.let { seed ->
                                            listOf(seed.lightPrimary, seed.lightSecondary)
                                        } ?: listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    ),
                                    shape = MaterialTheme.shapes.medium
                                )
                                .padding(12.dp),
                            contentAlignment = Alignment.CenterStart
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

@Composable
private fun DensityOption(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                shape = shape
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Preview(name = "Appearance — Light", showBackground = true, widthDp = 400, heightDp = 900)
@Composable
private fun ThemeScreenPreviewLight() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ThemeScreenContent(ThemeSettings(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}

@Preview(name = "Appearance — Dark", showBackground = true, backgroundColor = 0xFF0A0B10, widthDp = 400, heightDp = 900)
@Composable
private fun ThemeScreenPreviewDark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ThemeScreenContent(
                ThemeSettings(mode = ThemeMode.DARK, accent = "violet", monochrome = true),
                {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
            )
        }
    }
}
