package com.nexaflow.core.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Settings section: a section label followed by one grouped surface of
 * [SettingRow]s. Emits two lazy items (header + group) so headers scroll with
 * their rows, exactly like a native settings screen — one reusable block
 * instead of every screen hand-rolling the pair.
 */
fun LazyListScope.settingsGroup(
    title: String,
    groupModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    item(key = "settings_header_$title") {
        SectionHeader(text = title)
        // 18dp above the label, 10dp below it (§7.1).
        Spacer(modifier = Modifier.height(10.dp))
    }
    item(key = "settings_group_$title") {
        PickerGroup(modifier = groupModifier, content = content)
    }
}
