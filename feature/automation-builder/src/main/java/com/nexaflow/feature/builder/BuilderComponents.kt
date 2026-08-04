package com.nexaflow.feature.builder

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
fun itemHeader(text: String) {
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
fun PermissionHintForAction(actionType: ActionType, context: Context) {
    val hint: Triple<Int, Int, (Context) -> Unit>? = when (actionType) {
        ActionType.SYSTEM_BRIGHTNESS,
        ActionType.SYSTEM_SCREEN_ROTATION,
        ActionType.SYSTEM_SCREEN_TIMEOUT,
        ActionType.SYSTEM_STAY_AWAKE,
        ActionType.SYSTEM_AUTO_BRIGHTNESS,
        ActionType.SYSTEM_DARK_MODE,
        ActionType.SYSTEM_ANIMATIONS -> Triple(
            R.string.write_settings_hint,
            R.string.grant,
            PermissionShortcuts::openWriteSettings
        )
        ActionType.SYSTEM_DND,
        ActionType.SYSTEM_RINGER_MODE -> Triple(
            R.string.dnd_hint,
            R.string.grant,
            PermissionShortcuts::openNotificationPolicy
        )
        ActionType.SYSTEM_FLASHLIGHT -> Triple(
            R.string.flashlight_hint,
            R.string.grant,
            PermissionShortcuts::openAppSettings
        )
        ActionType.ADVANCED_SHIZUKU -> Triple(
            R.string.shizuku_hint,
            R.string.enable,
            PermissionShortcuts::openShizukuManager
        )
        ActionType.ADVANCED_ROOT -> Triple(
            R.string.root_hint,
            R.string.grant,
            PermissionShortcuts::openAppSettings
        )
        ActionType.APPLICATION_CLOSE_APP,
        ActionType.SYSTEM_MOBILE_DATA,
        ActionType.SYSTEM_HOTSPOT,
        ActionType.SYSTEM_NFC,
        ActionType.SYSTEM_POWER_SAVER,
        ActionType.SYSTEM_LOCK_SCREEN,
        ActionType.SYSTEM_OPEN_RECENTS,
        ActionType.SYSTEM_GO_HOME,
        ActionType.SYSTEM_LOCATION -> Triple(
            R.string.elevated_hint,
            R.string.info,
            PermissionShortcuts::openAppSettings
        )
        ActionType.SYSTEM_SEND_SMS -> Triple(
            R.string.sms_permission_hint,
            R.string.grant,
            PermissionShortcuts::openAppSettings
        )
        ActionType.SYSTEM_SEND_REMINDER -> Triple(
            R.string.notification_permission_hint,
            R.string.grant,
            PermissionShortcuts::openAppSettings
        )
        else -> null
    }
    hint?.let { (textRes, buttonRes, onClick) ->
        PermissionHint(
            text = stringResource(textRes),
            buttonLabel = stringResource(buttonRes),
            onClick = { onClick(context) }
        )
    }
}

private const val ACTION_INDEX_MIME = "text/action-index"

/**
 * Lists the selected actions in execution order and lets the user reorder them
 * with long-press drag & drop and up/down arrow buttons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionOrderSection(
    actions: List<ActionOption>,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (ActionOption) -> Unit
) {
    if (actions.isEmpty()) return
    var dragOverIndex by remember { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.forEachIndexed { index, option ->
            val isDragOver = dragOverIndex == index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isDragOver) {
                            Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        } else {
                            Modifier
                        }
                    )
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { event ->
                            event.mimeTypes().contains(ACTION_INDEX_MIME)
                        },
                        target = object : DragAndDropTarget {
                            override fun onEntered(event: DragAndDropEvent) {
                                dragOverIndex = index
                            }

                            override fun onExited(event: DragAndDropEvent) {
                                if (dragOverIndex == index) dragOverIndex = null
                            }

                            override fun onEnded(event: DragAndDropEvent) {
                                dragOverIndex = null
                            }

                            override fun onDrop(event: DragAndDropEvent): Boolean {
                                val from = event.toAndroidDragEvent().clipData
                                    ?.getItemAt(0)?.text?.toString()?.toIntOrNull()
                                if (from == null || from == index) {
                                    dragOverIndex = null
                                    return false
                                }
                                onMove(from, index)
                                dragOverIndex = null
                                return true
                            }
                        }
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .dragAndDropSource(
                            transferData = { _ ->
                                DragAndDropTransferData(
                                    clipData = ClipData.newPlainText(
                                        ACTION_INDEX_MIME,
                                        index.toString()
                                    ),
                                    localState = null,
                                    flags = 0
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = stringResource(R.string.drag_to_reorder),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(20.dp)
                )
                IconBadge(
                    icon = option.icon,
                    containerColor = option.color.copy(alpha = 0.15f),
                    contentColor = option.color
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(option.titleRes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(option.subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { onMove(index, index - 1) },
                    enabled = index > 0
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.move_up)
                    )
                }
                IconButton(
                    onClick = { onMove(index, index + 1) },
                    enabled = index < actions.lastIndex
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.move_down)
                    )
                }
                IconButton(onClick = { onRemove(option) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.remove_action),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
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
