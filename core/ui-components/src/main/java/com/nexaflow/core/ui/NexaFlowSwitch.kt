package com.nexaflow.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.nexaflow.core.ui.theme.NexaFlowTheme

/**
 * Ink Glass switch (§6). One tuned colour set for every toggle in the app:
 * primary track with a white thumb when on, `surfaceContainerHighest` track
 * with the dedicated [com.nexaflow.core.ui.theme.NexaFlowColors.switchThumbOff]
 * neutral when off. Material's default off thumb is `outline`, which measures
 * 2.2:1 on the dark off track, so the shared component is also the contrast fix.
 *
 * The track is 52dp wide and sits inside a row whose touch target is ≥ 48dp.
 */
@Composable
fun nexaFlowSwitchColors(): SwitchColors {
    val colors = MaterialTheme.colorScheme
    val nexaFlow = NexaFlowTheme.colors
    return SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = colors.primary,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = nexaFlow.switchThumbOff,
        uncheckedTrackColor = colors.surfaceContainerHighest,
        uncheckedBorderColor = Color.Transparent
    )
}

/**
 * Material 3 [Switch] on the Ink colour set. [stateDescription] lets a caller
 * announce the stream of truth the toggle controls (“Routine enabled”) without
 * changing the switch's own on/off semantics.
 */
@Composable
fun NexaFlowSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateDescription: String? = null
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = if (stateDescription == null) {
            modifier
        } else {
            modifier.semantics { this.stateDescription = stateDescription }
        },
        enabled = enabled,
        colors = nexaFlowSwitchColors()
    )
}
