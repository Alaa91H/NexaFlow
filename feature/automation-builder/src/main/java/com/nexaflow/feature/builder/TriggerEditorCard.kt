package com.nexaflow.feature.builder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.domain.models.TriggerType

val triggerTypeOptions = listOf(
    TriggerType.TIME,
    TriggerType.APPLICATION,
    TriggerType.DEVICE,
    TriggerType.CONNECTIVITY,
    TriggerType.LOCATION,
    TriggerType.SMS
)

private val repeatOptions = listOf(
    "ONCE" to R.string.repeat_once,
    "DAILY" to R.string.repeat_daily,
    "WEEKDAYS" to R.string.repeat_weekdays,
    "WEEKENDS" to R.string.repeat_weekends,
    "SPECIFIC_DAYS" to R.string.repeat_specific_days,
    "MONTHLY" to R.string.repeat_monthly,
    "DATE_RANGE" to R.string.repeat_date_range
)

private val weekdayOptions = listOf(
    1 to R.string.day_mon,
    2 to R.string.day_tue,
    3 to R.string.day_wed,
    4 to R.string.day_thu,
    5 to R.string.day_fri,
    6 to R.string.day_sat,
    7 to R.string.day_sun
)

private fun TriggerType.labelRes(): Int = when (this) {
    TriggerType.TIME -> R.string.trigger_type_time
    TriggerType.APPLICATION -> R.string.trigger_type_app
    TriggerType.DEVICE -> R.string.trigger_type_device
    TriggerType.CONNECTIVITY -> R.string.trigger_type_connectivity
    TriggerType.LOCATION -> R.string.trigger_type_location
    TriggerType.SMS -> R.string.trigger_type_sms
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TriggerEditorCard(
    draft: TriggerDraft,
    index: Int,
    onConfigChange: (TriggerDraft) -> Unit,
    onRemove: () -> Unit,
    onPickApp: () -> Unit,
    onPickFromMap: () -> Unit = {}
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    NexaFlowCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.trigger_n, index + 1),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.remove_trigger),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                triggerTypeOptions.forEach { option ->
                    FilterChip(
                        selected = draft.type == option,
                        onClick = {
                            onConfigChange(
                                TriggerDraft(
                                    type = option,
                                    config = when (option) {
                                        TriggerType.TIME -> mapOf("time" to (draft.config["time"] ?: "08:00"))
                                        TriggerType.APPLICATION -> mapOf("package" to (draft.config["package"] ?: ""))
                                        TriggerType.DEVICE -> mapOf("event" to (draft.config["event"] ?: "SCREEN_ON"))
                                        TriggerType.CONNECTIVITY -> mapOf(
                                            "network" to (draft.config["network"] ?: "WIFI"),
                                            "state" to (draft.config["state"] ?: "CONNECTED")
                                        )
                                        TriggerType.LOCATION -> mapOf(
                                            "lat" to (draft.config["lat"] ?: ""),
                                            "lng" to (draft.config["lng"] ?: ""),
                                            "radius" to (draft.config["radius"] ?: "100"),
                                            "event" to (draft.config["event"] ?: "ENTER")
                                        )
                                        TriggerType.SMS -> mapOf(
                                            "from" to (draft.config["from"] ?: ""),
                                            "contains" to (draft.config["contains"] ?: ""),
                                            "reply" to (draft.config["reply"] ?: "")
                                        )
                                    }
                                )
                            )
                        },
                        label = { Text(text = stringResource(option.labelRes())) }
                    )
                }
            }
            when (draft.type) {
                TriggerType.TIME -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTimePicker = true }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trigger_time),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.repeat_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            TextButton(onClick = { showTimePicker = true }) {
                                Text(text = draft.config["time"] ?: "08:00")
                            }
                        }
                        Text(text = stringResource(R.string.repeat_label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeatOptions.forEach { (value, labelRes) ->
                                FilterChip(
                                    selected = (draft.config["repeat"] ?: "DAILY") == value,
                                    onClick = {
                                        onConfigChange(draft.copy(config = draft.config + ("repeat" to value)))
                                    },
                                    label = { Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }
                        when (draft.config["repeat"] ?: "DAILY") {
                            "SPECIFIC_DAYS" -> {
                                Text(text = stringResource(R.string.select_days), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    weekdayOptions.forEach { (day, labelRes) ->
                                        val selectedDays = draft.config["days"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
                                        FilterChip(
                                            selected = day in selectedDays,
                                            onClick = {
                                                val updated = if (day in selectedDays) selectedDays - day else selectedDays + day
                                                onConfigChange(draft.copy(config = draft.config + ("days" to updated.sorted().joinToString(","))))
                                            },
                                            label = { Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelMedium) }
                                        )
                                    }
                                }
                            }
                            "MONTHLY" -> {
                                OutlinedTextField(
                                    value = draft.config["monthDay"] ?: "1",
                                    onValueChange = { onConfigChange(draft.copy(config = draft.config + ("monthDay" to it))) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(text = stringResource(R.string.month_day)) },
                                    singleLine = true
                                )
                            }
                            "DATE_RANGE" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = draft.config["startDate"] ?: "",
                                        onValueChange = { onConfigChange(draft.copy(config = draft.config + ("startDate" to it))) },
                                        modifier = Modifier.weight(1f),
                                        label = { Text(text = stringResource(R.string.start_date)) },
                                        placeholder = { Text(text = "2026-01-01") },
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = draft.config["endDate"] ?: "",
                                        onValueChange = { onConfigChange(draft.copy(config = draft.config + ("endDate" to it))) },
                                        modifier = Modifier.weight(1f),
                                        label = { Text(text = stringResource(R.string.end_date)) },
                                        placeholder = { Text(text = "2026-12-31") },
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                }
                TriggerType.APPLICATION -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.config["package"] ?: "",
                            onValueChange = {
                                onConfigChange(draft.copy(config = draft.config + ("package" to it)))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.package_name)) },
                            placeholder = { Text(text = stringResource(R.string.package_hint)) },
                            singleLine = true
                        )
                        TextButton(onClick = onPickApp) {
                            Text(text = stringResource(R.string.choose_from_installed))
                        }
                        PermissionHint(
                            text = stringResource(R.string.app_detection_hint),
                            buttonLabel = stringResource(R.string.enable),
                            onClick = { PermissionShortcuts.openAccessibilitySettings(context) }
                        )
                    }
                }
                TriggerType.DEVICE -> {
                    OptionChips(
                        options = listOf(
                            "SCREEN_ON",
                            "SCREEN_OFF",
                            "POWER_CONNECTED",
                            "POWER_DISCONNECTED",
                            "HEADSET_CONNECTED",
                            "HEADSET_DISCONNECTED"
                        ),
                        selected = draft.config["event"] ?: "SCREEN_ON",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                    )
                }
                TriggerType.CONNECTIVITY -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.network), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OptionChips(
                            options = listOf("WIFI", "MOBILE"),
                            selected = draft.config["network"] ?: "WIFI",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("network" to it))) }
                        )
                        Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OptionChips(
                            options = listOf("CONNECTED", "DISCONNECTED"),
                            selected = draft.config["state"] ?: "CONNECTED",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                        )
                    }
                }
                TriggerType.LOCATION -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onPickFromMap) {
                            Icon(imageVector = Icons.Filled.Map, contentDescription = null)
                            Text(text = stringResource(R.string.pick_on_map), modifier = Modifier.padding(start = 4.dp))
                        }
                        OutlinedTextField(
                            value = draft.config["lat"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("lat" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.latitude)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = draft.config["lng"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("lng" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.longitude)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = draft.config["radius"] ?: "100",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("radius" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.radius_meters)) },
                            singleLine = true
                        )
                        Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OptionChips(
                            options = listOf("ENTER", "EXIT"),
                            selected = draft.config["event"] ?: "ENTER",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                        )
                        PermissionHint(
                            text = stringResource(R.string.location_hint),
                            buttonLabel = stringResource(R.string.grant),
                            onClick = { PermissionShortcuts.openAppSettings(context) }
                        )
                    }
                }
                TriggerType.SMS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.config["from"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("from" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.sms_from)) },
                            placeholder = { Text(text = stringResource(R.string.sms_from_hint)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = draft.config["contains"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("contains" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.sms_contains)) },
                            placeholder = { Text(text = stringResource(R.string.sms_contains_hint)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = draft.config["reply"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("reply" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.sms_reply)) },
                            placeholder = { Text(text = stringResource(R.string.sms_reply_hint)) },
                            singleLine = true
                        )
                        PermissionHint(
                            text = stringResource(R.string.sms_permission_hint),
                            buttonLabel = stringResource(R.string.grant),
                            onClick = { PermissionShortcuts.openAppSettings(context) }
                        )
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerAlert(
            initialTime = draft.config["time"] ?: "08:00",
            onConfirm = {
                onConfigChange(draft.copy(config = draft.config + ("time" to it)))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}
