package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexaflow.core.rom.RebootMode
import com.nexaflow.core.ui.SelectChip

/**
 * Editor for the reboot action's declared `mode` field.
 *
 * The values come from [RebootMode], the same closed contract the runtime maps
 * to the elevated `reboot` arguments, so the builder cannot offer a target the
 * executor would refuse or silently normalise.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RebootTargetEditor(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.reboot_mode),
            style = MaterialTheme.typography.titleSmall
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RebootMode.selectable.forEach { mode ->
                SelectChip(
                    selected = (config["mode"] ?: RebootMode.NORMAL) == mode,
                    onClick = { onConfigChange(config + ("mode" to mode)) },
                    label = mode
                )
            }
        }
    }
}
