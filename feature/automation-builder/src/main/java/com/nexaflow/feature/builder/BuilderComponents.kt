package com.nexaflow.feature.builder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(text = option) }
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

object PermissionShortcuts {
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
}
