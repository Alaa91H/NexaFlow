package com.nexaflow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexaflow.core.compat.ChannelStatus
import com.nexaflow.core.compat.ChannelTier
import com.nexaflow.core.compat.ExecutionProviderType
import com.nexaflow.core.ui.SettingRow
import com.nexaflow.core.ui.StatusPill

/**
 * Live status row (Phase 7): shows the execution channel the engine selected
 * automatically for this device — Root / Shizuku / System app / … — with a
 * tier-colored badge and a manual refresh button.
 *
 * [status] is null while the first detection is in flight (or on failure),
 * rendered as a neutral "None" pill so the row never flashes.
 */
@Composable
fun ChannelStatusRow(
    status: ChannelStatus?,
    onRefresh: () -> Unit
) {
    // null = detection still in flight (first frame); a real status with a
    // NONE tier means detection ran but no channel is usable.
    val label = when {
        status == null -> stringResource(R.string.channel_detecting)
        status.provider == ExecutionProviderType.ROOT -> stringResource(R.string.channel_root)
        status.provider == ExecutionProviderType.SHIZUKU -> stringResource(R.string.channel_shizuku)
        status.provider == ExecutionProviderType.SYSTEM_APP -> stringResource(R.string.channel_system_app)
        status.provider == ExecutionProviderType.ADB -> stringResource(R.string.channel_adb)
        status.provider == ExecutionProviderType.ANDROID -> stringResource(R.string.channel_android)
        status.provider == ExecutionProviderType.ACCESSIBILITY -> stringResource(R.string.channel_accessibility)
        else -> stringResource(R.string.channel_none)
    }
    val (background, content) = when (status?.tier) {
        ChannelTier.ELEVATED -> Color(0xFFE4F4E9) to Color(0xFF006D3C)
        ChannelTier.STANDARD -> Color(0xFFE3F0FA) to Color(0xFF1E6FD9)
        ChannelTier.ACCESSIBILITY -> Color(0xFFFFF2E0) to Color(0xFFB26A00)
        ChannelTier.NONE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.secondary
        null -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.outline
    }
    SettingRow(
        icon = Icons.Filled.Bolt,
        title = stringResource(R.string.execution_channel),
        subtitle = stringResource(R.string.execution_channel_sub),
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusPill(
                    text = label,
                    background = background,
                    contentColor = content,
                    // Live region: screen readers announce channel changes as
                    // the detection refreshes instead of silently switching.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    )
}
