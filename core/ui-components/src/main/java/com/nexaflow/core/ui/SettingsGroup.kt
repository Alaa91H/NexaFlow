package com.nexaflow.core.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Google 2026 settings-group pattern: a [SectionHeader] followed by one
 * [NexaFlowCard] containing [SettingRow]s. Emits two lazy items (header +
 * card) so headers scroll with their cards, exactly like Google's settings
 * screens — one reusable block instead of every screen hand-rolling the
 * header/card pair with its own spacing.
 *
 * Screens that need a special card (dialogs, accordions) can keep the plain
 * `item { … }` form; this covers the 90% case.
 */
fun LazyListScope.settingsGroup(
    title: String,
    cardModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    item(key = "settings_header_$title") {
        SectionHeader(text = title)
        // M3 spacing: 8dp between a section header and its list. The lazy
        // content itself has no inter-item spacing, so the header-to-card
        // gap is explicit here.
        Spacer(modifier = Modifier.height(Dimens.Space2))
    }
    item(key = "settings_card_$title") {
        NexaFlowCard(modifier = cardModifier, content = content)
    }
}
