package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.nexaflow.core.ui.theme.DarkNexaFlowCategoryColors
import com.nexaflow.core.ui.theme.DarkNexaFlowColors
import com.nexaflow.core.ui.theme.LightNexaFlowCategoryColors
import com.nexaflow.core.ui.theme.LightNexaFlowColors
import com.nexaflow.core.ui.theme.LocalNexaFlowCategoryColors
import com.nexaflow.core.ui.theme.LocalNexaFlowColors
import com.nexaflow.core.ui.theme.NexaFlowCategory
import com.nexaflow.core.ui.theme.NexaFlowTheme
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Previews for the Ink Glass component set (§9): every shared component is shown
 * light, dark, RTL (Arabic) and at 200% font scale so a token regression is
 * visible in the IDE instead of only on a device.
 *
 * The app theme lives in `:app`, so these previews provide the semantic Ink
 * locals directly — the same values `NexaFlowTheme` resolves at runtime.
 */
@Composable
internal fun InkPreviewSurface(
    dark: Boolean,
    content: @Composable () -> Unit
) {
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    val colors = if (dark) DarkNexaFlowColors else LightNexaFlowColors
    val categories = if (dark) DarkNexaFlowCategoryColors else LightNexaFlowCategoryColors
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalNexaFlowColors provides colors,
            LocalNexaFlowCategoryColors provides categories
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(Dimens.Space4),
                verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun TileShowcase() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
        IconTile(icon = Icons.Filled.Timer, category = NexaFlowCategory.TIME)
        IconTile(icon = Icons.Filled.LocationOn, category = NexaFlowCategory.LOCATION)
        IconTile(
            icon = Icons.Filled.Bolt,
            category = NexaFlowCategory.POWER,
            size = Dimens.PickerTile,
            radius = Dimens.PickerTileRadius,
            picker = true
        )
    }
}

@Composable
internal fun CardShowcase() {
    NexaFlowCard {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
            StatusPill(text = "Running", variant = StatusPillVariant.Running)
            StatusPill(text = "Needs permission", variant = StatusPillVariant.NeedsPermission)
            StatusPill(text = "Error", variant = StatusPillVariant.Error)
            FlowStepRow(
                label = "When",
                chips = listOf("At 07:30", "Weekdays")
            )
            FlowStepRow(
                label = "Then",
                chips = listOf("Set brightness 40%", "Enable sleep mode")
            )
        }
    }
    SearchPill(query = "", onQueryChange = {}, placeholder = "Search tasks")
    CategoryChipRow {
        CategoryChip(selected = true, onClick = {}, label = "Schedule")
        CategoryChip(selected = false, onClick = {}, label = "Device")
    }
}

@Composable
internal fun GroupShowcase() {
    PickerGroup {
        PickerRow(
            icon = Icons.Filled.Timer,
            title = "Time",
            category = NexaFlowCategory.TIME,
            checked = true,
            onCheckedChange = {},
            description = "Runs at a fixed time on the days you pick",
            showDivider = false
        )
        PickerRow(
            icon = Icons.Filled.Notifications,
            title = "Notification",
            category = NexaFlowCategory.NOTIFICATIONS,
            checked = false,
            onCheckedChange = {},
            description = "Runs when a matching notification arrives"
        )
    }
}

@Composable
internal fun ChromeShowcase() {
    ScreenHeader(
        title = "Routines",
        caption = "12 tasks · 9 active",
        onBack = {},
        backContentDescription = "Back",
        actions = {
            HeaderIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                onClick = {}
            )
        }
    )
    Dock(
        leading = {
            DockAskPill(label = "Ask NexaFlow…", onClick = {}, icon = Icons.Filled.Bolt)
        },
        action = {
            DockIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "New task",
                onClick = {}
            )
        }
    )
}

@Preview(name = "Tiles · light", showBackground = true)
@Composable
private fun TilesLightPreview() {
    InkPreviewSurface(dark = false) { TileShowcase() }
}

@Preview(name = "Tiles · dark", showBackground = true)
@Composable
private fun TilesDarkPreview() {
    InkPreviewSurface(dark = true) { TileShowcase() }
}

@Preview(name = "Cards · dark", showBackground = true)
@Composable
private fun CardsDarkPreview() {
    InkPreviewSurface(dark = true) { CardShowcase() }
}

@Preview(name = "Cards · RTL", locale = "ar", showBackground = true)
@Composable
private fun CardsRtlPreview() {
    InkPreviewSurface(dark = true) { CardShowcase() }
}

@Preview(name = "Cards · font scale 2", fontScale = 2f, showBackground = true)
@Composable
private fun CardsLargeTextPreview() {
    InkPreviewSurface(dark = true) { CardShowcase() }
}

@Preview(name = "Picker group · dark", showBackground = true)
@Composable
private fun GroupDarkPreview() {
    InkPreviewSurface(dark = true) { GroupShowcase() }
}

@Preview(name = "Picker group · RTL", locale = "ar", showBackground = true)
@Composable
private fun GroupRtlPreview() {
    InkPreviewSurface(dark = true) { GroupShowcase() }
}

@Preview(name = "Picker group · font scale 2", fontScale = 2f, showBackground = true)
@Composable
private fun GroupLargeTextPreview() {
    InkPreviewSurface(dark = true) { GroupShowcase() }
}

@Preview(name = "Chrome · dark", widthDp = 390, heightDp = 240, showBackground = true)
@Composable
private fun ChromeDarkPreview() {
    InkPreviewSurface(dark = true) { ChromeShowcase() }
}

@Preview(name = "Chrome · RTL", locale = "ar", widthDp = 390, heightDp = 240, showBackground = true)
@Composable
private fun ChromeRtlPreview() {
    InkPreviewSurface(dark = true) { ChromeShowcase() }
}

@Preview(name = "Chrome · tablet glass", widthDp = 840, heightDp = 240, showBackground = true)
@Composable
private fun ChromeTabletPreview() {
    InkPreviewSurface(dark = true) { ChromeShowcase() }
}

@PreviewLightDark
@Composable
private fun GlassSurfacePreview() {
    InkPreviewSurface(dark = true) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.DockRadius)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.Space4),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Glass",
                    style = NexaFlowTheme.type.cardTitle
                )
            }
        }
    }
}
