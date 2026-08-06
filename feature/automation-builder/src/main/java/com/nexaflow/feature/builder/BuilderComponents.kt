package com.nexaflow.feature.builder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.domain.models.ActionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerAlert(
    initialTime: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialTime.substringBefore(":").toIntOrNull() ?: 8,
        initialMinute = initialTime.substringAfter(":").toIntOrNull() ?: 0
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm("%02d:%02d".format(pickerState.hour, pickerState.minute)) }) {
                Text(text = stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        text = { TimePicker(state = pickerState) }
    )
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

/**
 * Samsung-style selection chip with a clearly highlighted selected state
 * (filled primary tint + check icon + bolder label).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = {
            when {
                selected -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                leadingIcon != null -> Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OptionChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    labels: Map<String, String>? = null
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            SelectChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = labels?.get(option) ?: option
            )
        }
    }
}

@Composable
fun PermissionHint(
    text: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        TextButton(onClick = onClick) {
            Text(text = buttonLabel)
        }
    }
}

@Composable
fun ItemHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun ActionOptionRow(
    option: ActionOption,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconBadge(
            icon = option.icon,
            containerColor = option.color.copy(alpha = 0.15f),
            contentColor = option.color
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
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
fun PermissionHintForAction(
    actionType: ActionType,
    context: Context,
    onRequestPermission: (Array<String>) -> Unit = {},
    // Default keeps the pre-explain behavior (open settings directly) so a call
    // site that forgets to wire the explain screen never gets a dead button.
    onExplainSpecial: (SpecialPermission) -> Unit = { PermissionShortcuts.openSpecial(context, it) }
) {
    // Runtime-requestable permissions are requested through the system dialog
    // directly; special settings (write settings, DND, notification access,
    // accessibility, Shizuku/root) still open their dedicated settings screen.
    val runtimePermissions: List<String> = when (actionType) {
        ActionType.SYSTEM_SEND_SMS -> listOf(android.Manifest.permission.SEND_SMS)
        ActionType.SYSTEM_FLASHLIGHT -> listOf(android.Manifest.permission.CAMERA)
        ActionType.SYSTEM_SEND_NOTIFICATION,
        ActionType.SYSTEM_SEND_REMINDER,
        ActionType.BATTERY_ALERTS,
        ActionType.BATTERY_CHARGING_NOTIFICATIONS -> listOf(android.Manifest.permission.POST_NOTIFICATIONS)
        ActionType.SYSTEM_LOCATION -> listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        else -> emptyList()
    }
    if (runtimePermissions.isNotEmpty()) {
        PermissionHint(
            text = stringResource(
                when (actionType) {
                    ActionType.SYSTEM_SEND_SMS -> R.string.sms_permission_hint
                    ActionType.SYSTEM_FLASHLIGHT -> R.string.flashlight_hint
                    ActionType.SYSTEM_SEND_NOTIFICATION,
                    ActionType.SYSTEM_SEND_REMINDER,
                    ActionType.BATTERY_ALERTS,
                    ActionType.BATTERY_CHARGING_NOTIFICATIONS -> R.string.notification_permission_hint
                    else -> R.string.location_hint
                }
            ),
            buttonLabel = stringResource(R.string.grant),
            onClick = { onRequestPermission(runtimePermissions.toTypedArray()) }
        )
        return
    }

    val hint: Triple<Int, Int, SpecialPermission>? = when (actionType) {
        ActionType.SYSTEM_BRIGHTNESS,
        ActionType.SYSTEM_SCREEN_ROTATION,
        ActionType.SYSTEM_SCREEN_TIMEOUT,
        ActionType.SYSTEM_STAY_AWAKE,
        ActionType.SYSTEM_AUTO_BRIGHTNESS,
        ActionType.SYSTEM_DARK_MODE,
        ActionType.SYSTEM_ANIMATIONS -> Triple(
            R.string.write_settings_hint,
            R.string.grant,
            SpecialPermission.WRITE_SETTINGS
        )
        ActionType.SYSTEM_DND,
        ActionType.SYSTEM_RINGER_MODE -> Triple(
            R.string.dnd_hint,
            R.string.grant,
            SpecialPermission.DND_ACCESS
        )
        ActionType.ADVANCED_SHIZUKU -> Triple(
            R.string.shizuku_hint,
            R.string.enable,
            SpecialPermission.SHIZUKU
        )
        ActionType.ADVANCED_ROOT -> Triple(
            R.string.root_hint,
            R.string.grant,
            SpecialPermission.ROOT
        )
        ActionType.APPLICATION_CLOSE_APP,
        ActionType.SYSTEM_MOBILE_DATA,
        ActionType.SYSTEM_HOTSPOT,
        ActionType.SYSTEM_NFC,
        ActionType.SYSTEM_POWER_SAVER,
        ActionType.SYSTEM_LOCK_SCREEN,
        ActionType.SYSTEM_OPEN_RECENTS,
        ActionType.SYSTEM_GO_HOME -> Triple(
            R.string.elevated_hint,
            R.string.info,
            SpecialPermission.ELEVATED
        )
        ActionType.SYSTEM_BLOCK_NOTIFICATION,
        ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS -> Triple(
            R.string.notification_access_hint,
            R.string.enable,
            SpecialPermission.NOTIFICATION_ACCESS
        )
        else -> null
    }
    hint?.let { (textRes, buttonRes, special) ->
        PermissionHint(
            text = stringResource(textRes),
            buttonLabel = stringResource(buttonRes),
            // Explain why the permission is needed BEFORE opening its settings screen.
            onClick = { onExplainSpecial(special) }
        )
    }
}

object PermissionShortcuts {
    /** Opens the dedicated system screen for a special permission. */
    fun openSpecial(context: Context, type: SpecialPermission) {
        when (type) {
            SpecialPermission.WRITE_SETTINGS -> openWriteSettings(context)
            SpecialPermission.DND_ACCESS -> openNotificationPolicy(context)
            SpecialPermission.NOTIFICATION_ACCESS -> openNotificationAccessSettings(context)
            SpecialPermission.ACCESSIBILITY -> openAccessibilitySettings(context)
            SpecialPermission.SHIZUKU -> openShizukuManager(context)
            SpecialPermission.ROOT,
            SpecialPermission.ELEVATED -> openAppSettings(context)
            SpecialPermission.BLUETOOTH -> openBluetoothSettings(context)
        }
    }

    fun openWriteSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
        } catch (_: Throwable) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun openNotificationPolicy(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } catch (_: Throwable) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Throwable) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun openAppSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
        } catch (_: Throwable) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun openShizukuManager(context: Context) {
        try {
            context.startActivity(context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api"))
        } catch (_: Throwable) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun openBluetoothSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (_: Throwable) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun openNotificationAccessSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Throwable) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
