package com.nexaflow.feature.builder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexaflow.core.execution.NotificationActionButton
import com.nexaflow.core.rom.DnsProviderCatalog
import com.nexaflow.core.rom.NetworkModeCapabilities
import com.nexaflow.core.rom.NetworkModePolicy
import com.nexaflow.core.rom.NetworkModeSnapshot
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.RootPermissionGranter
import com.nexaflow.core.rom.ShizukuShellBridge
import com.nexaflow.core.ui.SelectChip
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.variables.VariableResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Simple toggle actions that use "turn_on" as their label. */
private val TURN_ON_TOGGLE_ACTIONS = setOf(
    ActionType.SYSTEM_LOCATION,
    ActionType.SYSTEM_DND,
    ActionType.SYSTEM_WIFI,
    ActionType.SYSTEM_BLUETOOTH,
    ActionType.SYSTEM_FLASHLIGHT,
    ActionType.SYSTEM_AIRPLANE_MODE,
    ActionType.SYSTEM_STAY_AWAKE,
    ActionType.SYSTEM_AUTO_BRIGHTNESS,
    ActionType.SYSTEM_MOBILE_DATA,
    ActionType.SYSTEM_HOTSPOT,
    ActionType.SYSTEM_NFC,
    ActionType.SYSTEM_POWER_SAVER,
    ActionType.SYSTEM_ANIMATIONS,
    ActionType.SYSTEM_DARK_MODE,
    ActionType.SYSTEM_DATA_ROAMING,
    ActionType.SYSTEM_CALL_VIBRATION,
    ActionType.SYSTEM_STATUS_BAR_TOGGLE
)

/**
 * Network-mode editor backed by a live per-subscription telephony snapshot.
 * It never promotes a generic generation list to a device capability: when
 * Android/OEM policy blocks the read, the card says so and preserves existing
 * task data rather than inventing choices that the device may reject.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VariableInsertChips(
    availableVariables: List<String>,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    if (availableVariables.isEmpty()) return
    // Variables already referenced in the field's text are shown as selected
    // (live feedback that the insertion is in effect).
    val used = remember(currentValue) {
        VariableResolver.referencedPlaceholders(currentValue).map { it.lowercase() }.toSet()
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.insert_variable_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            availableVariables.forEach { name ->
                SelectChip(
                    selected = name.lowercase() in used,
                    onClick = {
                        val placeholder = "%$name"
                        onValueChange(
                            if (currentValue.isBlank()) placeholder
                            else "$currentValue $placeholder"
                        )
                    },
                    label = "%$name"
                )
            }
        }
    }
}

/**
 * Step-5 insert row: tapping the chip appends a `%CTX.$` reference placeholder
 * to the field so the user can feed the output of an earlier node (published
 * via its `outputPath`) into this one. The JSONPath is typed after the `$`.
 */
@Composable
internal fun ContextPathInsertChips(
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.insert_context_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SelectChip(
                selected = currentValue.contains("%CTX.", ignoreCase = true),
                onClick = {
                    val placeholder = "%CTX.$"
                    onValueChange(
                        if (currentValue.isBlank()) placeholder
                        else "$currentValue $placeholder"
                    )
                },
                label = "%CTX.$"
            )
        }
    }
}

@Composable
internal fun ToggleConfigRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * Editor for notification action buttons: attach up to three
 * tasks that run straight from the notification when it is shown. Tapping a
 * row opens a picker of the other saved tasks; each picked task becomes a
 * button labelled with the task name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationButtonsEditor(
    buttons: List<NotificationActionButton>,
    automations: List<Automation>,
    onButtonsChange: (List<NotificationActionButton>) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var replyEditorButton by remember { mutableStateOf<NotificationActionButton?>(null) }
    var replyEditorVariable by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.notification_buttons_title),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(R.string.notification_buttons_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        if (buttons.isEmpty()) {
            Text(
                text = stringResource(R.string.notification_buttons_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            buttons.forEach { button ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = button.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            // Subtitle: the reply variable the button writes to
                            // (P2-1), so the user sees the target at a glance.
                            if (!button.replyVariable.isNullOrBlank()) {
                                Text(
                                    text = stringResource(
                                        R.string.notification_button_reply_sub,
                                        "%" + button.replyVariable
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        // Reply toggle: turns this button into a text-input
                        // action that stores the typed reply into a %variable.
                        IconButton(onClick = {
                            replyEditorVariable = button.replyVariable.orEmpty()
                            replyEditorButton = button
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = stringResource(R.string.notification_button_reply),
                                tint = if (button.replyVariable.isNullOrBlank()) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        IconButton(onClick = {
                            onButtonsChange(buttons.filterNot { it.automationId == button.automationId })
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.notification_button_remove),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
        // Android caps a notification at three action buttons.
        if (buttons.size < 3) {
            TextButton(
                onClick = { showPicker = true },
                enabled = automations.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(text = stringResource(R.string.notification_buttons_add))
            }
        }
    }

    if (showPicker) {
        // Google 2026: selection tasks open as a full-height modal bottom sheet.
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            )
        ) {
            Text(
                text = stringResource(R.string.notification_buttons_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                if (automations.isEmpty()) {
                    Text(
                        text = stringResource(R.string.notification_buttons_picker_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    // Never offer a task twice — already-attached buttons are
                    // hidden from the picker so duplicate entries can't stack.
                    val selectedIds = buttons.map { it.automationId }.toSet()
                    automations.filterNot { it.id in selectedIds }.forEach { automation ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onButtonsChange(buttons + NotificationActionButton(automation.name, automation.id))
                                    showPicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = automation.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showPicker = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        }
    }

    // Reply-variable editor: a small dialog setting the %variable that receives
    // the text typed into this button's RemoteInput field. Blank clears it.
    replyEditorButton?.let { button ->
        AlertDialog(
            onDismissRequest = {
                replyEditorButton = null
                replyEditorVariable = ""
            },
            title = { Text(text = stringResource(R.string.notification_button_reply_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.notification_button_reply_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    OutlinedTextField(
                        value = replyEditorVariable,
                        onValueChange = { replyEditorVariable = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = stringResource(R.string.notification_button_reply_label)) },
                        placeholder = { Text(text = "MyReply") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val updated = buttons.map {
                        if (it.automationId == button.automationId) {
                            it.copy(replyVariable = replyEditorVariable.takeIf { v -> v.isNotBlank() })
                        } else it
                    }
                    onButtonsChange(updated)
                    replyEditorButton = null
                    replyEditorVariable = ""
                }) {
                    Text(text = stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    replyEditorButton = null
                    replyEditorVariable = ""
                }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}
