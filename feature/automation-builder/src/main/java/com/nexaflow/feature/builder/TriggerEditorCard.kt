package com.nexaflow.feature.builder

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import com.nexaflow.core.engine.currentCellularGeneration
import com.nexaflow.core.rom.CustomSettingsBridge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.SelectChip
import com.nexaflow.domain.catalog.AutomationNodeCatalog
import com.nexaflow.domain.catalog.AutomationNodeFamily
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TriggerEditorCard(
    draft: TriggerDraft,
    index: Int,
    total: Int,
    onConfigChange: (TriggerDraft) -> Unit,
    onRemove: () -> Unit,
    /** Controlled by the builder so this card is the only expanded trigger. */
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    // Shared builder-row structure: ✕ remove, ↕️ reorder handle (long-press
    // drag + tap arrows), then the name. Wired by the reorderable list.
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onPickApp: () -> Unit,
    onPickFromMap: () -> Unit = {},
    onUseCurrentLocation: () -> Unit = {},
    onPickBluetooth: () -> Unit = {},
    onPickCalendar: () -> Unit = {},
    onRequestPermission: (Array<String>) -> Unit = {},
    onExplainSpecial: (SpecialPermission) -> Unit = {},
    // Re-probes the live permission badges when the screen resumes (e.g. after
    // returning from the accessibility or notification-access settings screen).
    refreshKey: Int = 0
) {
    val context = LocalContext.current
    val headerLayoutDirection = LocalLayoutDirection.current
    var showTimePicker by remember { mutableStateOf(false) }
    var timePickerTarget by remember { mutableStateOf("time") } // "time" | "rangeStart" | "rangeEnd"
    var datePickerTarget by remember { mutableStateOf<String?>(null) } // "date" | "startDate" | "endDate"
    // Fixed header row; the builder owns expansion so the selected card is
    // the sole open card and a new card can close the previous one.
    val accent = builderCardAccent(index)
    NexaFlowCard(
        modifier = modifier,
        containerColor = builderCardContainerColor(index),
        contentColor = builderCardContentColor
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // X + reorder handle pinned to the LEFT, row number to the
                    // RIGHT, regardless of the locale direction.
                    // يفتح الصف الإعداد عند الحاجة ويطويه بعد الضبط، فيبقى
                    // محرر المهمة منظماً حتى عند تعدد المحفزات والشروط.
                    .clickable { onExpandedChange(!expanded) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.remove_trigger),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                TaskRowHandle(
                    index = index,
                    total = total,
                    isDragging = isDragging,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onDragStart = onDragStart,
                    onDragDelta = onDragDelta,
                    onDragEnd = onDragEnd
                )
                // Keep the controls physically pinned LTR, but render the
                // trigger identity using the app locale. The type name is the
                // primary label and the configured value is secondary, so two
                // triggers remain easy to distinguish even when both are
                // collapsed.
                val triggerName = stringResource(draft.type.labelRes())
                val triggerValue = triggerSummary(draft)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides headerLayoutDirection) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = draft.type.icon(),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = triggerName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (!triggerValue.equals(triggerName, ignoreCase = true)) {
                            Text(
                                text = triggerValue,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                TaskNumberBadge(
                    number = index + 1,
                    containerColor = accent,
                    contentColor = Color.White
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(if (expanded) R.string.collapse_options else R.string.expand_options),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            }
            if (expanded) {
                NodeConfiguratorSheet(
                    title = triggerSummary(draft),
                    confirmLabel = stringResource(R.string.save),
                    confirmEnabled = true,
                    onConfirm = { onExpandedChange(false) },
                    onDismiss = { onExpandedChange(false) }
                ) {
                    // A task card configures its already-selected trigger only.
                    // Simple contracts render directly from the canonicalized
                    // catalog schema. Platform pickers and advanced contracts
                    // keep their specialized renderer as a compatibility seam.
                    val canonicalBinding =
                        CanonicalBuilderSchemaBridge.editingBindingForTrigger(draft.type)
                    if (canonicalBinding != null) {
                        CanonicalSchemaFieldEditor(
                            binding = canonicalBinding,
                            config = draft.config,
                            onConfigChange = { updated ->
                                onConfigChange(draft.copy(config = updated))
                            },
                        )
                    } else {
                        when (draft.type) {
                TriggerType.TIME -> {
                    val rangeMode = draft.config["timeMode"] == "RANGE"
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SelectChip(
                                selected = !rangeMode,
                                onClick = {
                                    onConfigChange(
                                        draft.copy(
                                            config = draft.config + ("timeMode" to "SINGLE")
                                        )
                                    )
                                },
                                label = stringResource(R.string.time_single)
                            )
                            SelectChip(
                                selected = rangeMode,
                                onClick = {
                                    onConfigChange(
                                        draft.copy(
                                            config = draft.config +
                                                ("timeMode" to "RANGE") +
                                                ("rangeStart" to (draft.config["rangeStart"] ?: "08:00")) +
                                                ("rangeEnd" to (draft.config["rangeEnd"] ?: "18:00"))
                                        )
                                    )
                                },
                                label = stringResource(R.string.time_range)
                            )
                        }
                        if (rangeMode) {
                            val start = draft.config["rangeStart"] ?: "08:00"
                            val end = draft.config["rangeEnd"] ?: "18:00"
                            TimeField(
                                label = stringResource(R.string.time_range_start),
                                value = start,
                                onClick = { timePickerTarget = "rangeStart"; showTimePicker = true }
                            )
                            TimeField(
                                label = stringResource(R.string.time_range_end),
                                value = end,
                                onClick = { timePickerTarget = "rangeEnd"; showTimePicker = true }
                            )
                            val overnight = parseTimeMinutes(end) < parseTimeMinutes(start)
                            Text(
                                text = if (overnight) stringResource(R.string.range_overnight) else stringResource(R.string.range_same_day),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (overnight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { timePickerTarget = "time"; showTimePicker = true }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.trigger_time),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = stringResource(R.string.repeat_daily),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                TextButton(onClick = { timePickerTarget = "time"; showTimePicker = true }) {
                                    Text(text = draft.config["time"] ?: "08:00")
                                }
                            }
                        }
                        SpecialPermissionStatusRow(
                            hintText = stringResource(R.string.special_exact_alarm_hint),
                            special = SpecialPermission.EXACT_ALARM,
                            context = context,
                            refreshKey = refreshKey,
                            onRequest = { onExplainSpecial(SpecialPermission.EXACT_ALARM) }
                        )
                        TimeRepeatSection(
                            draft = draft,
                            onConfigChange = onConfigChange,
                            onPickDate = { datePickerTarget = it }
                        )
                    }
                }
                TriggerType.BATTERY -> {
                    val direction = draft.config["direction"] ?: "ABOVE"
                    val chargerType = draft.config["chargerType"] ?: "ANY"
                    val chargingState = draft.config["chargingState"] ?: "ANY"
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val threshold = (draft.config["above"] ?: "80").toIntOrNull() ?: 80
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BatteryChargingFull,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.battery_trigger_title),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (direction == "ABOVE") {
                                        stringResource(R.string.battery_above_sub)
                                    } else {
                                        stringResource(R.string.battery_below_sub)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        // ── Battery level (independent of charging) ──────────
                        Text(
                            text = stringResource(R.string.battery_level_section),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip(
                                selected = direction == "ABOVE",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("direction" to "ABOVE")))
                                },
                                label = stringResource(R.string.battery_above)
                            )
                            SelectChip(
                                selected = direction == "BELOW",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("direction" to "BELOW")))
                                },
                                label = stringResource(R.string.battery_below)
                            )
                        }
                        SliderRow(
                            label = if (direction == "ABOVE") {
                                stringResource(R.string.minimum_battery, threshold)
                            } else {
                                stringResource(R.string.maximum_battery, threshold)
                            },
                            value = threshold.toFloat(),
                            onValueChange = { value ->
                                onConfigChange(
                                    draft.copy(config = draft.config + ("above" to value.toInt().toString()))
                                )
                            },
                            valueRange = 5f..100f
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        // ── Charging state (plug + charger type, separate) ────
                        Text(
                            text = stringResource(R.string.charging_state_section),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val chargingOptions = listOf(
                            "ANY" to R.string.charging_any,
                            "CHARGING" to R.string.charging_yes,
                            "NOT_CHARGING" to R.string.charging_no
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            chargingOptions.forEach { (value, labelRes) ->
                                SelectChip(
                                    selected = chargingState == value,
                                    onClick = {
                                        onConfigChange(draft.copy(config = draft.config + ("chargingState" to value)))
                                    },
                                    label = stringResource(labelRes)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.charger_type_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        val chargerOptions = listOf(
                            "ANY" to R.string.charger_any,
                            "AC" to R.string.charger_ac,
                            "USB" to R.string.charger_usb,
                            "WIRELESS" to R.string.charger_wireless
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            chargerOptions.forEach { (value, labelRes) ->
                                SelectChip(
                                    selected = chargerType == value,
                                    onClick = {
                                        onConfigChange(draft.copy(config = draft.config + ("chargerType" to value)))
                                    },
                                    label = stringResource(labelRes)
                                )
                            }
                        }
                    }
                }
                TriggerType.APPLICATION -> {
                    val packages = (draft.config["packages"] ?: draft.config["package"] ?: "")
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trigger_app),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (packages.isEmpty()) {
                                        stringResource(R.string.no_apps_selected)
                                    } else {
                                        stringResource(R.string.selected_apps_count, packages.size)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onPickApp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Filled.Apps, contentDescription = null)
                            Text(
                                text = stringResource(R.string.choose_apps),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        // Guided lifecycle hint: the task runs while the
                        // app stays open and its end options apply on close.
                        Text(
                            text = stringResource(R.string.trigger_app_while_open),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        // Live accessibility-service badge (granted / not granted)
                        // refreshed on resume; tapping it explains and opens the
                        // accessibility settings screen.
                        SpecialPermissionStatusRow(
                            hintText = stringResource(R.string.app_detection_hint),
                            special = SpecialPermission.ACCESSIBILITY,
                            context = context,
                            refreshKey = refreshKey,
                            onRequest = { onExplainSpecial(SpecialPermission.ACCESSIBILITY) }
                        )
                    }
                }
                TriggerType.DEVICE -> {
                    val event = draft.config["event"] ?: "SCREEN_ON"
                    val isBluetooth = event == "BLUETOOTH_CONNECTED" || event == "BLUETOOTH_DISCONNECTED"
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                        OptionChips(
                            options = listOf(
                                "SCREEN_ON",
                                "SCREEN_OFF",
                                "POWER_CONNECTED",
                                "POWER_DISCONNECTED",
                                "HEADSET_CONNECTED",
                                "HEADSET_DISCONNECTED",
                                "BLUETOOTH"
                            ),
                            labels = mapOf(
                                "SCREEN_ON" to stringResource(R.string.device_screen_on),
                                "SCREEN_OFF" to stringResource(R.string.device_screen_off),
                                "POWER_CONNECTED" to stringResource(R.string.device_power_connected),
                                "POWER_DISCONNECTED" to stringResource(R.string.device_power_disconnected),
                                "HEADSET_CONNECTED" to stringResource(R.string.device_headset_connected),
                                "HEADSET_DISCONNECTED" to stringResource(R.string.device_headset_disconnected),
                                "BLUETOOTH" to stringResource(R.string.device_bluetooth)
                            ),
                            selected = if (isBluetooth) "BLUETOOTH" else event,
                            onSelect = { value ->
                                if (value == "BLUETOOTH") {
                                    onConfigChange(
                                        draft.copy(
                                            config = draft.config +
                                                ("event" to "BLUETOOTH_CONNECTED") +
                                                ("deviceName" to (draft.config["deviceName"] ?: ""))
                                        )
                                    )
                                } else {
                                    onConfigChange(draft.copy(config = draft.config + ("event" to value)))
                                }
                            }
                        )
                        if (isBluetooth) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.trigger_bluetooth),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = (draft.config["deviceName"] ?: "").ifBlank {
                                            stringResource(R.string.no_bluetooth_device)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onPickBluetooth,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Filled.Bluetooth, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.choose_bluetooth_device),
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                            Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall)
                            OptionChips(
                                options = listOf("BLUETOOTH_CONNECTED", "BLUETOOTH_DISCONNECTED"),
                                labels = mapOf(
                                    "BLUETOOTH_CONNECTED" to stringResource(R.string.state_connected),
                                    "BLUETOOTH_DISCONNECTED" to stringResource(R.string.state_disconnected)
                                ),
                                selected = event,
                                onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                            )
                            RuntimePermissionHint(
                                context = context,
                                permissions = listOf(android.Manifest.permission.BLUETOOTH_CONNECT),
                                text = stringResource(R.string.permission_bluetooth_body),
                                buttonLabel = stringResource(R.string.grant),
                                onRequest = { onRequestPermission(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT)) }
                            )
                            BluetoothEnabledHint(
                                context = context,
                                hintText = stringResource(R.string.bluetooth_permission_hint),
                                buttonLabel = stringResource(R.string.enable),
                                refreshKey = refreshKey,
                                onRequest = { onExplainSpecial(SpecialPermission.BLUETOOTH) }
                            )
                        }
                    }
                }
                TriggerType.HOTSPOT -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall)
                        OptionChips(
                            options = listOf("ON", "OFF"),
                            labels = mapOf(
                                "ON" to stringResource(R.string.builder_state_on),
                                "OFF" to stringResource(R.string.builder_state_off)
                            ),
                            selected = draft.config["state"] ?: "ON",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                        )
                    }
                }
                TriggerType.WIFI_CONNECTED,
                TriggerType.MOBILE_DATA_CONNECTED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall)
                        OptionChips(
                            options = listOf("CONNECTED", "DISCONNECTED"),
                            labels = mapOf(
                                "CONNECTED" to stringResource(R.string.state_connected),
                                "DISCONNECTED" to stringResource(R.string.state_disconnected)
                            ),
                            selected = draft.config["state"] ?: "CONNECTED",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                        )
                    }
                }
                TriggerType.CONNECTIVITY -> {
                    val network = draft.config["network"] ?: "WIFI"
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Legacy combined trigger (saved tasks only): the network
                        // choice stays as saved; only the state is editable.
                        Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall)
                        when (network) {
                            "HOTSPOT" -> OptionChips(
                                options = listOf("ON", "OFF"),
                                labels = mapOf(
                                    "ON" to stringResource(R.string.builder_state_on),
                                    "OFF" to stringResource(R.string.builder_state_off)
                                ),
                                selected = draft.config["state"] ?: "ON",
                                onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                            )
                            "NETWORK_MODE" -> OptionChips(
                                options = listOf("AUTO", "2G", "3G", "4G", "5G"),
                                labels = mapOf(
                                    "AUTO" to stringResource(R.string.network_mode_auto),
                                    "2G" to stringResource(R.string.network_mode_2g),
                                    "3G" to stringResource(R.string.network_mode_3g),
                                    "4G" to stringResource(R.string.network_mode_4g),
                                    "5G" to stringResource(R.string.network_mode_5g)
                                ),
                                selected = draft.config["state"] ?: "4G",
                                onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                            )
                            else -> OptionChips(
                                options = listOf("CONNECTED", "DISCONNECTED"),
                                labels = mapOf(
                                    "CONNECTED" to stringResource(R.string.state_connected),
                                    "DISCONNECTED" to stringResource(R.string.state_disconnected)
                                ),
                                selected = draft.config["state"] ?: "CONNECTED",
                                onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                            )
                        }
                    }
                }
                TriggerType.NETWORK_MODE -> {
                    var currentGen by remember { mutableStateOf<String?>(null) }
                    var currentGenLoading by remember { mutableStateOf(false) }
                    var currentGenRefreshToken by remember { mutableStateOf(0) }
                    LaunchedEffect(currentGenRefreshToken, refreshKey) {
                        currentGenLoading = true
                        currentGen = withContext(Dispatchers.IO) {
                            currentCellularGeneration(context)
                        }
                        currentGenLoading = false
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Live read of the device's actual cellular generation,
                        // using the same real-5G detection as the runtime monitor.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.network_mode_current),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = if (currentGenLoading) {
                                    stringResource(R.string.network_mode_unknown)
                                } else {
                                    currentGen ?: stringResource(R.string.network_mode_unknown)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                enabled = !currentGenLoading,
                                onClick = { currentGenRefreshToken++ }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.refresh)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.network_mode_current_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        RuntimePermissionHint(
                            context = context,
                            permissions = listOf(android.Manifest.permission.READ_PHONE_STATE),
                            text = stringResource(R.string.network_mode_permission_hint),
                            buttonLabel = stringResource(R.string.enable),
                            onRequest = {
                                onRequestPermission(arrayOf(android.Manifest.permission.READ_PHONE_STATE))
                            }
                        )
                        Text(
                            text = stringResource(R.string.network_mode_fire_when),
                            style = MaterialTheme.typography.titleSmall
                        )
                        OptionChips(
                            options = listOf("AUTO", "2G", "3G", "4G", "5G"),
                            labels = mapOf(
                                "AUTO" to stringResource(R.string.network_mode_auto),
                                "2G" to stringResource(R.string.network_mode_2g),
                                "3G" to stringResource(R.string.network_mode_3g),
                                "4G" to stringResource(R.string.network_mode_4g),
                                "5G" to stringResource(R.string.network_mode_5g)
                            ),
                            selected = draft.config["state"] ?: "4G",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                        )
                    }
                }
                TriggerType.LOCATION -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Two distinct location modes share the existing location engine:
                        // current device location or a provider-independent fixed point.
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onUseCurrentLocation,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Filled.MyLocation, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.use_current_location),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = onPickFromMap,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Filled.Map, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.pick_on_map),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                        // Read-only summary of a selected fixed point. Coordinates are
                        // provider-independent and remain editable through the picker.
                        val lat = draft.config["lat"] ?: ""
                        val lng = draft.config["lng"] ?: ""
                        if (lat.isNotBlank() && lng.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.location_coordinates, lat, lng),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.location_not_set),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        // The current-location flow uses the shared location fix;
                        // selected-location stores a fixed coordinate and its radius.
                        val radius = (draft.config["radius"]?.toIntOrNull() ?: 100)
                            .coerceIn(LOCATION_RADIUS_MIN_M, LOCATION_RADIUS_MAX_M)
                        val locationSource = draft.config["source"] ?: "current"
                        if (locationSource == "current") {
                            // Current-location mode keeps the existing radius editor.
                            Text(
                                text = stringResource(R.string.radius_meters_format, radius),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Slider(
                                value = radius.toFloat(),
                                onValueChange = { value ->
                                    onConfigChange(
                                        draft.copy(
                                            config = draft.config + ("radius" to value.toInt().toString())
                                        )
                                    )
                                },
                                valueRange = LOCATION_RADIUS_MIN_M.toFloat()..LOCATION_RADIUS_MAX_M.toFloat(),
                                steps = LOCATION_RADIUS_STEPS
                            )
                        } else {
                            // Selected fixed-location mode keeps the saved radius visible.
                            Text(
                                text = stringResource(R.string.map_radius_summary, radius),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        // Inside / outside the defined location (both flows).
                        val event = draft.config["event"] ?: "ENTER"
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip(
                                selected = event == "ENTER",
                                onClick = { onConfigChange(draft.copy(config = draft.config + ("event" to "ENTER"))) },
                                label = stringResource(R.string.location_inside),
                                modifier = Modifier.weight(1f)
                            )
                            SelectChip(
                                selected = event == "EXIT",
                                onClick = { onConfigChange(draft.copy(config = draft.config + ("event" to "EXIT"))) },
                                label = stringResource(R.string.location_outside),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        RuntimePermissionHint(
                            context = context,
                            permissions = listOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            ),
                            text = stringResource(R.string.location_hint),
                            buttonLabel = stringResource(R.string.grant),
                            onRequest = {
                                onRequestPermission(
                                    arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
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
                        // Body-match mode: contains / exact / any text.
                        val storedMode = (draft.config["matchMode"] ?: "CONTAINS").trim().uppercase()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SelectChip(
                                selected = storedMode == "CONTAINS",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("matchMode" to "CONTAINS")))
                                },
                                label = stringResource(R.string.sms_match_contains),
                                modifier = Modifier.weight(1f)
                            )
                            SelectChip(
                                selected = storedMode == "EXACT",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("matchMode" to "EXACT")))
                                },
                                label = stringResource(R.string.sms_match_exact),
                                modifier = Modifier.weight(1f)
                            )
                            SelectChip(
                                selected = storedMode == "ANY",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("matchMode" to "ANY")))
                                },
                                label = stringResource(R.string.sms_match_any),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (storedMode != "ANY") {
                            OutlinedTextField(
                                value = draft.config["contains"] ?: "",
                                onValueChange = { onConfigChange(draft.copy(config = draft.config + ("contains" to it))) },
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text(
                                        text = if (storedMode == "EXACT") {
                                            stringResource(R.string.sms_match_exact)
                                        } else {
                                            stringResource(R.string.sms_contains)
                                        }
                                    )
                                },
                                placeholder = { Text(text = stringResource(R.string.sms_contains_hint)) },
                                singleLine = true
                            )
                        }
                        RuntimePermissionHint(
                            context = context,
                            permissions = listOf(android.Manifest.permission.RECEIVE_SMS),
                            text = stringResource(R.string.sms_permission_hint),
                            buttonLabel = stringResource(R.string.grant),
                            onRequest = { onRequestPermission(arrayOf(android.Manifest.permission.RECEIVE_SMS)) }
                        )
                    }
                }
                TriggerType.INCOMING_CALL -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val storedModeForNumber = (draft.config["matchMode"] ?: "ANY").trim().uppercase()
                        if (storedModeForNumber != "ANY") {
                            OutlinedTextField(
                                value = draft.config["from"] ?: "",
                                onValueChange = { onConfigChange(draft.copy(config = draft.config + ("from" to it))) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(text = stringResource(R.string.call_number_filter)) },
                                placeholder = { Text(text = stringResource(R.string.call_from_hint)) },
                                singleLine = true
                            )
                        }
                        val storedMode = (draft.config["matchMode"] ?: "ANY").trim().uppercase()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SelectChip(
                                selected = storedMode == "CONTAINS",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("matchMode" to "CONTAINS")))
                                },
                                label = stringResource(R.string.sms_match_contains),
                                modifier = Modifier.weight(1f)
                            )
                            SelectChip(
                                selected = storedMode == "EXACT",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("matchMode" to "EXACT")))
                                },
                                label = stringResource(R.string.sms_match_exact),
                                modifier = Modifier.weight(1f)
                            )
                            SelectChip(
                                selected = storedMode == "ANY",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("matchMode" to "ANY")))
                                },
                                label = stringResource(R.string.call_match_any),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // The number filter applies to the caller number, not a
                        // message body — keep the hint explicit so the modes are
                        // not confused with the SMS trigger's text matching.
                        Text(
                            text = stringResource(R.string.call_mode_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Caller category: any / unknown / private / contact.
                        val storedCategory = (draft.config["category"] ?: "ANY").trim().uppercase()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SelectChip(
                                selected = storedCategory == "ANY",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("category" to "ANY")))
                                },
                                label = stringResource(R.string.call_category_any),
                                modifier = Modifier.weight(1f)
                            )
                            SelectChip(
                                selected = storedCategory == "UNKNOWN",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("category" to "UNKNOWN")))
                                },
                                label = stringResource(R.string.call_category_unknown),
                                modifier = Modifier.weight(1f)
                            )
                            SelectChip(
                                selected = storedCategory == "PRIVATE",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("category" to "PRIVATE")))
                                },
                                label = stringResource(R.string.call_category_private),
                                modifier = Modifier.weight(1f)
                            )
                            SelectChip(
                                selected = storedCategory == "CONTACT",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("category" to "CONTACT")))
                                },
                                label = stringResource(R.string.call_category_contact),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        RuntimePermissionHint(
                            context = context,
                            permissions = listOf(Manifest.permission.READ_PHONE_STATE),
                            text = stringResource(R.string.call_screening_hint),
                            buttonLabel = stringResource(R.string.grant),
                            onRequest = { onRequestPermission(arrayOf(Manifest.permission.READ_PHONE_STATE)) }
                        )
                    }
                }
                TriggerType.RINGER_MODE -> {
                    val mode = draft.config["mode"] ?: "NORMAL"
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.trigger_ringer),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = stringResource(R.string.trigger_ringer_sub),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Text(text = stringResource(R.string.ringer_mode_label), style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val modes = listOf(
                                "NORMAL" to R.string.ringer_normal,
                                "VIBRATE" to R.string.ringer_vibrate,
                                "SILENT" to R.string.ringer_silent
                            )
                            modes.forEach { (value, labelRes) ->
                                SelectChip(
                                    selected = mode == value,
                                    onClick = { onConfigChange(draft.copy(config = draft.config + ("mode" to value))) },
                                    label = stringResource(labelRes)
                                )
                            }
                        }
                        PermissionHint(
                            text = stringResource(R.string.ringer_mode_hint),
                            buttonLabel = stringResource(R.string.change_now),
                            onClick = {
                                runCatching {
                                    val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                                    when (mode) {
                                        "SILENT" -> audio.ringerMode = android.media.AudioManager.RINGER_MODE_SILENT
                                        "VIBRATE" -> audio.ringerMode = android.media.AudioManager.RINGER_MODE_VIBRATE
                                        else -> audio.ringerMode = android.media.AudioManager.RINGER_MODE_NORMAL
                                    }
                                }
                            }
                        )
                    }
                }
                TriggerType.BLUETOOTH_DEVICE -> {
                    val deviceName = draft.config["deviceName"] ?: ""
                    val deviceAddress = draft.config["deviceAddress"] ?: ""
                    val isAny = BluetoothTriggerConfig.isAnyDevice(deviceName, deviceAddress)
                    val event = draft.config["event"] ?: "CONNECTED"
                    var advancedBluetoothExpanded by rememberSaveable {
                        mutableStateOf(deviceName.isNotBlank() && !isAny || deviceAddress.isNotBlank())
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bluetooth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trigger_bluetooth),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (isAny) {
                                        stringResource(R.string.any_bluetooth_device)
                                    } else {
                                        deviceName.ifBlank { deviceAddress }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onPickBluetooth,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Filled.Bluetooth, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.choose_bluetooth_device),
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = { onConfigChange(draft.copy(config = draft.config + mapOf("deviceName" to "", "deviceAddress" to ""))) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = stringResource(R.string.any_device))
                            }
                        }
                        if (isAny) {
                            Text(
                                text = stringResource(R.string.any_bluetooth_device_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall)
                        OptionChips(
                            options = listOf("CONNECTED", "DISCONNECTED"),
                            selected = event,
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                        )
                        TextButton(onClick = { advancedBluetoothExpanded = !advancedBluetoothExpanded }) {
                            Text(
                                text = stringResource(
                                    if (advancedBluetoothExpanded) R.string.bluetooth_advanced_hide
                                    else R.string.bluetooth_advanced
                                )
                            )
                        }
                        if (advancedBluetoothExpanded) {
                            OutlinedTextField(
                                value = deviceName.takeUnless { BluetoothTriggerConfig.isAnySentinel(it) }.orEmpty(),
                                onValueChange = { value ->
                                    onConfigChange(draft.copy(config = draft.config + mapOf(
                                        "deviceName" to value,
                                        "deviceAddress" to if (value.isBlank()) deviceAddress else ""
                                    )))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.bluetooth_device_name)) },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = deviceAddress,
                                onValueChange = { value ->
                                    onConfigChange(draft.copy(config = draft.config + mapOf(
                                        "deviceAddress" to value.trim(),
                                        "deviceName" to if (value.isBlank()) deviceName else ""
                                    )))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.bluetooth_device_address)) },
                                singleLine = true
                            )
                        }
                        // Two independent, self-hiding rows — never a permanent prompt:
                        // 1) BLUETOOTH_CONNECT runtime permission (system dialog).
                        //    The Bluetooth settings screen cannot grant it, so it
                        //    must go through onRequestPermission, not openSpecial.
                        // 2) Adapter OFF advisory (settings screen via the special
                        //    explain flow). Hidden when the permission is missing
                        //    (row 1 owns that case) or when Bluetooth is already
                        //    ON — so a healthy device shows no prompt at all.
                        RuntimePermissionHint(
                            context = context,
                            permissions = listOf(android.Manifest.permission.BLUETOOTH_CONNECT),
                            text = stringResource(R.string.permission_bluetooth_body),
                            buttonLabel = stringResource(R.string.grant),
                            onRequest = {
                                onRequestPermission(
                                    arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT)
                                )
                            }
                        )
                        BluetoothEnabledHint(
                            context = context,
                            hintText = stringResource(R.string.bluetooth_permission_hint),
                            buttonLabel = stringResource(R.string.enable),
                            refreshKey = refreshKey,
                            onRequest = { onExplainSpecial(SpecialPermission.BLUETOOTH) }
                        )
                    }
                }
                TriggerType.CALENDAR -> {
                    val calendarName = draft.config["calendar"] ?: ""
                    val event = draft.config["event"] ?: "EVENT_START"
                    val beforeMinutes = (draft.config["beforeMinutes"] ?: "0").toIntOrNull() ?: 0
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trigger_calendar),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (calendarName.isBlank()) {
                                        stringResource(R.string.any_calendar)
                                    } else {
                                        calendarName
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onPickCalendar,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Filled.DateRange, contentDescription = null)
                            Text(
                                text = stringResource(R.string.choose_calendar),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        OutlinedTextField(
                            value = draft.config["contains"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("contains" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.calendar_contains)) },
                            placeholder = { Text(text = stringResource(R.string.calendar_contains_hint)) },
                            singleLine = true
                        )
                        Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val events = listOf(
                                "EVENT_START" to R.string.calendar_event_start,
                                "EVENT_END" to R.string.calendar_event_end,
                                "EVENT_CREATED" to R.string.calendar_event_created
                            )
                            events.forEach { (value, labelRes) ->
                                SelectChip(
                                    selected = event == value,
                                    onClick = { onConfigChange(draft.copy(config = draft.config + ("event" to value))) },
                                    label = stringResource(labelRes)
                                )
                            }
                        }
                        if (event == "EVENT_START") {
                            SliderRow(
                                label = if (beforeMinutes == 0) {
                                    stringResource(R.string.calendar_before_none)
                                } else {
                                    stringResource(R.string.calendar_before_minutes, beforeMinutes)
                                },
                                value = beforeMinutes.toFloat(),
                                onValueChange = { value ->
                                    onConfigChange(
                                        draft.copy(config = draft.config + ("beforeMinutes" to value.toInt().toString()))
                                    )
                                },
                                valueRange = 0f..120f
                            )
                        }
                        RuntimePermissionHint(
                            context = context,
                            permissions = listOf(android.Manifest.permission.READ_CALENDAR),
                            text = stringResource(R.string.calendar_permission_hint),
                            buttonLabel = stringResource(R.string.grant),
                            onRequest = { onRequestPermission(arrayOf(android.Manifest.permission.READ_CALENDAR)) }
                        )
                    }
                }
                TriggerType.SENSOR -> {
                    val sensor = (draft.config["sensor"] ?: "PROXIMITY").uppercase(java.util.Locale.ROOT)
                    val sensorHardware = remember(context) { com.nexaflow.core.execution.compat.HardwareProfile.probe(context) }
                    val sensorCompatibility = remember { com.nexaflow.core.execution.compat.CommandCompatibilityEngine() }
                    val event = draft.config["event"] ?: "COVERED"
                    val threshold = (draft.config["threshold"] ?: "200").toIntOrNull() ?: 200
                    val sensitivity = (draft.config["sensitivity"] ?: "14").toIntOrNull() ?: 14
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.sensor_kind),
                            style = MaterialTheme.typography.titleSmall
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val kinds = listOf(
                                "PROXIMITY" to R.string.sensor_proximity,
                                "SHAKE" to R.string.sensor_shake,
                                "LIGHT" to R.string.sensor_light,
                                "STEP" to R.string.sensor_step,
                                "PRESSURE" to R.string.sensor_numeric_pressure,
                                "TEMPERATURE" to R.string.sensor_numeric_temperature,
                                "HUMIDITY" to R.string.sensor_numeric_humidity,
                                "MAGNETIC" to R.string.sensor_numeric_magnetic,
                                "ACCELERATION" to R.string.sensor_numeric_acceleration,
                                "GYROSCOPE" to R.string.sensor_numeric_gyroscope,
                                "GRAVITY" to R.string.sensor_numeric_gravity,
                                "HINGE" to R.string.sensor_numeric_hinge
                            )
                            kinds.forEach { (value, labelRes) ->
                                SelectChip(
                                    selected = sensor == value,
                                    onClick = {
                                        onConfigChange(
                                            draft.copy(config = com.nexaflow.domain.models.NumericSensors.configurationFor(value, draft.config))
                                        )
                                    },
                                    label = stringResource(labelRes)
                                )
                            }
                        }
                        if (!sensorCompatibility.isSensorAvailable(sensor, sensorHardware)) {
                            Text(stringResource(R.string.sensor_unavailable), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        val numeric = com.nexaflow.domain.models.NumericSensors.specs[sensor]
                        if (numeric != null) {
                            Text(stringResource(R.string.sensor_numeric_help), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("ABOVE" to R.string.sensor_event_above, "BELOW" to R.string.sensor_event_below,
                                    "AT_LEAST" to R.string.sensor_event_at_least, "AT_MOST" to R.string.sensor_event_at_most,
                                    "BETWEEN" to R.string.sensor_event_between).forEach { (comparison, label) ->
                                    SelectChip(selected = (draft.config["event"] ?: "ABOVE") == comparison,
                                        onClick = { onConfigChange(draft.copy(config = draft.config + ("event" to comparison))) },
                                        label = stringResource(label))
                                }
                            }
                            val fields = if (draft.config["event"] == "BETWEEN") listOf("threshold", "upperThreshold") else listOf("threshold")
                            val lower = draft.config["threshold"]?.toFloatOrNull()?.takeIf { it.isFinite() }
                            fields.forEach { key ->
                                val parsed = draft.config[key]?.toFloatOrNull()?.takeIf { it.isFinite() }
                                val invalidRange = key == "upperThreshold" && parsed != null && lower != null && parsed < lower
                                val invalid = parsed == null || invalidRange
                                OutlinedTextField(value = draft.config[key].orEmpty(),
                                    onValueChange = { onConfigChange(draft.copy(config = draft.config + (key to it))) },
                                    label = { Text(stringResource(if (key == "threshold") R.string.sensor_numeric_lower else R.string.sensor_numeric_upper, numeric.unit)) },
                                    isError = invalid,
                                    supportingText = { if (invalid) Text(stringResource(if (invalidRange) R.string.sensor_invalid_range else R.string.sensor_invalid_threshold)) },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                            }
                        }
                        when (sensor) {
                            "PROXIMITY" -> {
                                Text(
                                    text = stringResource(R.string.event),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val events = listOf(
                                        "COVERED" to R.string.sensor_event_covered,
                                        "UNCOVERED" to R.string.sensor_event_uncovered
                                    )
                                    events.forEach { (value, labelRes) ->
                                        SelectChip(
                                            selected = event == value,
                                            onClick = {
                                                onConfigChange(
                                                    draft.copy(config = draft.config + ("event" to value))
                                                )
                                            },
                                            label = stringResource(labelRes)
                                        )
                                    }
                                }
                            }
                            "LIGHT" -> {
                                Text(
                                    text = stringResource(R.string.event),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val events = listOf(
                                        "ABOVE" to R.string.sensor_event_above,
                                        "BELOW" to R.string.sensor_event_below
                                    )
                                    events.forEach { (value, labelRes) ->
                                        SelectChip(
                                            selected = event == value,
                                            onClick = {
                                                onConfigChange(
                                                    draft.copy(config = draft.config + ("event" to value))
                                                )
                                            },
                                            label = stringResource(labelRes)
                                        )
                                    }
                                }
                                SliderRow(
                                    label = stringResource(R.string.sensor_threshold_label, threshold),
                                    value = threshold.toFloat(),
                                    onValueChange = { value ->
                                        onConfigChange(
                                            draft.copy(config = draft.config + ("threshold" to value.toInt().toString()))
                                        )
                                    },
                                    valueRange = 5f..1000f
                                )
                            }
                            "SHAKE" -> {
                                SliderRow(
                                    label = stringResource(R.string.sensor_sensitivity_label, sensitivity),
                                    value = sensitivity.toFloat(),
                                    onValueChange = { value ->
                                        onConfigChange(
                                            draft.copy(config = draft.config + ("sensitivity" to value.toInt().toString()))
                                        )
                                    },
                                    valueRange = 5f..30f
                                )
                            }
                            "STEP" -> {
                                Text(
                                    text = stringResource(R.string.sensor_step_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                // Raw step-counter reads on Android 10+ need the
                                // ACTIVITY_RECOGNITION runtime permission.
                                RuntimePermissionHint(
                                    context = context,
                                    permissions = listOf(android.Manifest.permission.ACTIVITY_RECOGNITION),
                                    text = stringResource(R.string.sensor_permission_hint),
                                    buttonLabel = stringResource(R.string.grant),
                                    onRequest = {
                                        onRequestPermission(
                                            arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                TriggerType.WEBHOOK -> {
                    val path = draft.config["path"] ?: "/nexaflow"
                    val method = draft.config["method"] ?: "POST"
                    val token = draft.config["token"] ?: ""
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = path,
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("path" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.webhook_path)) },
                            placeholder = { Text(text = "/nexaflow") },
                            singleLine = true
                        )
                        Text(
                            text = stringResource(R.string.event),
                            style = MaterialTheme.typography.titleSmall
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val methods = listOf("ANY", "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
                            methods.forEach { value ->
                                SelectChip(
                                    selected = method == value,
                                    onClick = {
                                        onConfigChange(draft.copy(config = draft.config + ("method" to value)))
                                    },
                                    label = value
                                )
                            }
                        }
                        OutlinedTextField(
                            value = token,
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("token" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.webhook_token)) },
                            placeholder = { Text(text = stringResource(R.string.webhook_token_hint)) },
                            singleLine = true
                        )
                        Text(
                            text = stringResource(R.string.webhook_url_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                TriggerType.ROM_SETTING -> {
                    val namespace = draft.config["namespace"] ?: "SYSTEM"
                    val operator = draft.config["operator"] ?: "EQUALS"
                    val key = draft.config["key"] ?: ""
                    val value = draft.config["value"] ?: ""
                    var showRomSettingPicker by remember { mutableStateOf(false) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.rom_setting_trigger_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(text = stringResource(R.string.rom_setting_trigger_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        // ── Namespace (system / secure / global) ────────────
                        Text(
                            text = stringResource(R.string.rom_setting_namespace),
                            style = MaterialTheme.typography.titleSmall
                        )
                        OptionChips(
                            options = listOf("SYSTEM", "SECURE", "GLOBAL"),
                            labels = mapOf(
                                "SYSTEM" to stringResource(R.string.rom_setting_namespace_system),
                                "SECURE" to stringResource(R.string.rom_setting_namespace_secure),
                                "GLOBAL" to stringResource(R.string.rom_setting_namespace_global)
                            ),
                            selected = namespace,
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("namespace" to it))) }
                        )
                        // ── Key: professional vendor custom setting picker (chip-driven) ───
                        // The key is chosen from the live device keys or the
                        // curated catalog — never typed by hand — so the stored
                        // key always matches a real ROM key.
                        val selectedKeyCategory = if (key.isNotBlank()) {
                            com.nexaflow.core.rom.RomSettingCatalog.categorize(key)
                        } else null
                        OutlinedButton(
                            onClick = { showRomSettingPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Filled.Bolt, contentDescription = null)
                            Text(
                                text = if (key.isNotBlank()) {
                                    key
                                } else {
                                    stringResource(R.string.rom_setting_pick_key)
                                },
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        if (selectedKeyCategory != null) {
                            Text(
                                stringResource(
                                    R.string.rom_setting_key_category,
                                    selectedKeyCategory.displayName,
                                    selectedKeyCategory.description
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (showRomSettingPicker) {
                            RomSettingPickerDialog(
                                onPick = { entry ->
                                    onConfigChange(draft.copy(config = draft.config + mapOf("namespace" to entry.namespace.name, "key" to entry.key, "value" to entry.value)))
                                    showRomSettingPicker = false
                                },
                                onDismiss = { showRomSettingPicker = false }
                            )
                        }
                        // ── Operator + target value (chips, no free text) ───
                        Text(
                            text = stringResource(R.string.rom_setting_operator),
                            style = MaterialTheme.typography.titleSmall
                        )
                        OptionChips(
                            options = listOf("EQUALS", "NOT_EQUALS"),
                            labels = mapOf(
                                "EQUALS" to stringResource(R.string.rom_setting_equals),
                                "NOT_EQUALS" to stringResource(R.string.rom_setting_not_equals)
                            ),
                            selected = operator,
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("operator" to it))) }
                        )
                        // Value chips follow the selected key's value type from
                        // the catalog: booleans get on/off, enums get their fixed
                        // option set, everything else gets the common values.
                        val keyMeta = if (key.isNotBlank()) {
                            com.nexaflow.core.rom.RomSettingCatalog.metaFor(key)
                        } else null
                        val valueChoices: List<String> = when (keyMeta?.valueType) {
                            com.nexaflow.core.rom.RomSettingCatalog.ValueType.BOOLEAN -> listOf("1", "0")
                            com.nexaflow.core.rom.RomSettingCatalog.ValueType.ENUM -> keyMeta.options.ifEmpty { listOf("0", "1", "2") }
                            com.nexaflow.core.rom.RomSettingCatalog.ValueType.INTEGER -> listOf("0", "1", "2", "5", "10", "48")
                            else -> listOf("1", "0", "true", "false", "on", "off")
                        }
                        OptionChips(
                            options = valueChoices,
                            selected = if (valueChoices.contains(value)) value else "",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("value" to it))) }
                        )
                        Text(
                            text = stringResource(R.string.rom_setting_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                TriggerType.NOTIFICATION -> {
                    val packages = (draft.config["packages"] ?: draft.config["package"] ?: "")
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    val event = draft.config["event"] ?: "POSTED"
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trigger_notification),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (packages.isEmpty()) {
                                        stringResource(R.string.any_app)
                                    } else {
                                        stringResource(R.string.selected_apps_count, packages.size)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onPickApp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Filled.Apps, contentDescription = null)
                            Text(
                                text = stringResource(R.string.choose_apps),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        OutlinedTextField(
                            value = draft.config["contains"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("contains" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.notification_contains)) },
                            placeholder = { Text(text = stringResource(R.string.notification_contains_hint)) },
                            singleLine = true
                        )
                        Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                        OptionChips(
                            options = listOf("POSTED", "REMOVED"),
                            selected = event,
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                        )
                        // Live notification-listener badge (granted / not granted)
                        // refreshed on resume; tapping it explains and opens the
                        // notification access settings screen.
                        SpecialPermissionStatusRow(
                            hintText = stringResource(R.string.notification_access_hint),
                            special = SpecialPermission.NOTIFICATION_ACCESS,
                            context = context,
                            refreshKey = refreshKey,
                            onRequest = { onExplainSpecial(SpecialPermission.NOTIFICATION_ACCESS) }
                        )
                    }
                }
                TriggerType.HEADPHONE -> {
                    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("CONNECTED", "DISCONNECTED"),
                        labels = mapOf(
                            "CONNECTED" to stringResource(R.string.state_connected),
                            "DISCONNECTED" to stringResource(R.string.state_disconnected)
                        ),
                        selected = draft.config["event"] ?: "CONNECTED",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                    )
                }
                TriggerType.CHARGER -> {
                    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("CONNECTED", "DISCONNECTED"),
                        labels = mapOf(
                            "CONNECTED" to stringResource(R.string.state_connected),
                            "DISCONNECTED" to stringResource(R.string.state_disconnected)
                        ),
                        selected = draft.config["event"] ?: "CONNECTED",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                    )
                }
                TriggerType.AIRPLANE_MODE -> {
                    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("ON", "OFF"),
                        labels = mapOf(
                            "ON" to stringResource(R.string.builder_state_on),
                            "OFF" to stringResource(R.string.builder_state_off)
                        ),
                        selected = draft.config["state"] ?: "ON",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                    )
                }
                TriggerType.DARK_MODE -> {
                    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("ON", "OFF"),
                        labels = mapOf(
                            "ON" to stringResource(R.string.builder_state_on),
                            "OFF" to stringResource(R.string.builder_state_off)
                        ),
                        selected = draft.config["state"] ?: "ON",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                    )
                }
                TriggerType.CALL_STATE -> {
                    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("INCOMING", "OUTGOING", "ENDED"),
                        labels = mapOf(
                            "INCOMING" to stringResource(R.string.call_incoming),
                            "OUTGOING" to stringResource(R.string.call_outgoing),
                            "ENDED" to stringResource(R.string.call_ended)
                        ),
                        selected = draft.config["event"] ?: "INCOMING",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                    )
                }
                TriggerType.APP_INSTALLED -> {
                    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("INSTALLED", "REMOVED", "UPDATED"),
                        labels = mapOf(
                            "INSTALLED" to stringResource(R.string.app_installed),
                            "REMOVED" to stringResource(R.string.app_removed),
                            "UPDATED" to stringResource(R.string.app_updated)
                        ),
                        selected = draft.config["event"] ?: "INSTALLED",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                    )
                    OutlinedTextField(
                        value = draft.config["package"] ?: "",
                        onValueChange = { v ->
                            onConfigChange(draft.copy(config = draft.config + ("package" to v)))
                        },
                        label = { Text(text = stringResource(R.string.optional_package)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TriggerType.MEDIA_PLAYING -> {
                    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("STARTED", "STOPPED"),
                        labels = mapOf(
                            "STARTED" to stringResource(R.string.media_started),
                            "STOPPED" to stringResource(R.string.media_stopped)
                        ),
                        selected = draft.config["event"] ?: "STARTED",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                    )
                }
                TriggerType.VOLUME_CHANGED -> {
                    Text(text = stringResource(R.string.volume_stream), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("MUSIC", "RING", "ALARM", "NOTIFICATION"),
                        labels = mapOf(
                            "MUSIC" to stringResource(R.string.volume_stream_music),
                            "RING" to stringResource(R.string.volume_stream_ring),
                            "ALARM" to stringResource(R.string.volume_stream_alarm),
                            "NOTIFICATION" to stringResource(R.string.volume_stream_notification)
                        ),
                        selected = draft.config["stream"] ?: "MUSIC",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("stream" to it))) }
                    )
                    Text(text = stringResource(R.string.volume_direction), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("ABOVE", "BELOW"),
                        labels = mapOf(
                            "ABOVE" to stringResource(R.string.volume_above),
                            "BELOW" to stringResource(R.string.volume_below)
                        ),
                        selected = draft.config["direction"] ?: "ABOVE",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
                    )
                    OutlinedTextField(
                        value = draft.config["threshold"] ?: "50",
                        onValueChange = { v ->
                            onConfigChange(draft.copy(config = draft.config + ("threshold" to v.filter { it.isDigit() })))
                        },
                        label = { Text(text = stringResource(R.string.volume_threshold)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // State triggers with their own labels (power saver, Wi-Fi, NFC…).
                TriggerType.POWER_SAVER, TriggerType.BLUETOOTH_STATE, TriggerType.AUTO_ROTATE,
                TriggerType.DATA_SAVER_STATE, TriggerType.WIFI_STATE, TriggerType.NFC_STATE -> {
                    val labelRes = ON_OFF_TRIGGER_LABELS[draft.type] ?: R.string.trigger_state_label
                    Text(text = stringResource(labelRes), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("ON", "OFF"),
                        labels = mapOf(
                            "ON" to stringResource(R.string.builder_state_on),
                            "OFF" to stringResource(R.string.builder_state_off)
                        ),
                        selected = draft.config["state"] ?: "ON",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                    )
                }
                TriggerType.BRIGHTNESS_LEVEL -> {
                    Text(text = stringResource(R.string.trigger_brightness_direction), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("ABOVE", "BELOW"),
                        labels = mapOf(
                            "ABOVE" to stringResource(R.string.volume_above),
                            "BELOW" to stringResource(R.string.volume_below)
                        ),
                        selected = draft.config["direction"] ?: "ABOVE",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
                    )
                    OutlinedTextField(
                        value = draft.config["threshold"] ?: "128",
                        onValueChange = { v ->
                            onConfigChange(draft.copy(config = draft.config + ("threshold" to v.filter { it.isDigit() })))
                        },
                        label = { Text(text = stringResource(R.string.trigger_brightness_threshold)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TriggerType.STORAGE_LOW -> {
                    Text(text = stringResource(R.string.trigger_storage_direction), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("BELOW", "ABOVE"),
                        labels = mapOf(
                            "BELOW" to stringResource(R.string.storage_below),
                            "ABOVE" to stringResource(R.string.storage_above)
                        ),
                        selected = draft.config["direction"] ?: "BELOW",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
                    )
                    OutlinedTextField(
                        value = draft.config["threshold"] ?: "1024",
                        onValueChange = { v ->
                            onConfigChange(draft.copy(config = draft.config + ("threshold" to v.filter { it.isDigit() })))
                        },
                        label = { Text(text = stringResource(R.string.trigger_storage_threshold)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TriggerType.DEVICE_LOCKED -> {
                    Text(text = stringResource(R.string.trigger_device_locked_state), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("LOCKED", "UNLOCKED"),
                        labels = mapOf(
                            "LOCKED" to stringResource(R.string.device_locked),
                            "UNLOCKED" to stringResource(R.string.device_unlocked)
                        ),
                        selected = draft.config["state"] ?: "LOCKED",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                    )
                }
                TriggerType.LOCATION_STATE -> {
                    Text(text = stringResource(R.string.trigger_location_mode), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("ON", "OFF"),
                        labels = mapOf(
                            "ON" to stringResource(R.string.builder_state_on),
                            "OFF" to stringResource(R.string.builder_state_off)
                        ),
                        selected = when (draft.config["mode"]?.uppercase()) {
                            "OFF" -> "OFF"
                            else -> "ON"
                        },
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("mode" to it))) }
                    )
                }
                TriggerType.SCREEN_ROTATION_STATE -> {
                    Text(text = stringResource(R.string.trigger_rotation_state), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("PORTRAIT", "LANDSCAPE"),
                        labels = mapOf(
                            "PORTRAIT" to stringResource(R.string.rotation_portrait),
                            "LANDSCAPE" to stringResource(R.string.rotation_landscape)
                        ),
                        selected = draft.config["state"] ?: "PORTRAIT",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                    )
                }
                TriggerType.WIFI_SIGNAL_STRENGTH -> {
                    Text(text = stringResource(R.string.trigger_signal_threshold), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("1", "2", "3", "4"),
                        selected = draft.config["threshold"] ?: "3",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("threshold" to it))) }
                    )
                    OptionChips(
                        options = listOf("ABOVE", "BELOW"),
                        labels = mapOf(
                            "ABOVE" to stringResource(R.string.volume_above),
                            "BELOW" to stringResource(R.string.volume_below)
                        ),
                        selected = draft.config["direction"] ?: "ABOVE",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
                    )
                }
                TriggerType.CELL_SIGNAL_STRENGTH -> {
                    Text(text = stringResource(R.string.trigger_signal_threshold), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("1", "2", "3", "4"),
                        selected = draft.config["threshold"] ?: "3",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("threshold" to it))) }
                    )
                    OptionChips(
                        options = listOf("ABOVE", "BELOW"),
                        labels = mapOf(
                            "ABOVE" to stringResource(R.string.volume_above),
                            "BELOW" to stringResource(R.string.volume_below)
                        ),
                        selected = draft.config["direction"] ?: "ABOVE",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
                    )
                }
                TriggerType.BATTERY_TEMPERATURE -> {
                    Text(text = stringResource(R.string.trigger_temperature_threshold), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = draft.config["threshold"] ?: "40",
                        onValueChange = { input ->
                            // Temperature is a decimal number (°C): keep digits
                            // and a single decimal separator only.
                            val cleaned = buildString {
                                input.forEachIndexed { index, char ->
                                    when {
                                        char.isDigit() -> append(char)
                                        char == '-' && index == 0 && isEmpty() -> append(char)
                                        char == '.' && '.' !in this -> append(char)
                                    }
                                }
                            }
                            onConfigChange(draft.copy(config = draft.config + ("threshold" to cleaned)))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.trigger_temperature_threshold)) },
                        supportingText = { Text(stringResource(R.string.battery_temp_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OptionChips(
                        options = listOf("ABOVE", "BELOW"),
                        labels = mapOf(
                            "ABOVE" to stringResource(R.string.volume_above),
                            "BELOW" to stringResource(R.string.volume_below)
                        ),
                        selected = draft.config["direction"] ?: "ABOVE",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
                    )
                }
                // Simple ON/OFF state triggers (generic "State" title).
                TriggerType.USB_CONNECTED, TriggerType.HDMI_CONNECTED,
                TriggerType.ETHERNET_CONNECTED, TriggerType.VPN_CONNECTED,
                TriggerType.DND_STATE, TriggerType.STAY_AWAKE_STATE,
                TriggerType.AUTO_BRIGHTNESS_STATE, TriggerType.DATA_ROAMING_STATE -> {
                    Text(text = stringResource(R.string.trigger_state_label), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("ON", "OFF"),
                        labels = mapOf(
                            "ON" to stringResource(R.string.builder_state_on),
                            "OFF" to stringResource(R.string.builder_state_off)
                        ),
                        selected = draft.config["state"] ?: "ON",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                    )
                }
                TriggerType.CLIPBOARD_CHANGED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.trigger_desc_clipboard), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = draft.config["contains"].orEmpty(),
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("contains" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.notification_contains)) },
                            placeholder = { Text(stringResource(R.string.sms_contains_hint)) },
                            singleLine = true
                        )
                    }
                }
                TriggerType.SCREEN_TIMEOUT_CHANGED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.trigger_desc_screen_timeout), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = draft.config["seconds"].orEmpty(),
                            onValueChange = { value ->
                                onConfigChange(
                                    draft.copy(config = draft.config + ("seconds" to value.filter { it.isDigit() }))
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.action_screen_timeout)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
                TriggerType.TIMEZONE_CHANGED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.trigger_desc_timezone), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = draft.config["zone"].orEmpty(),
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("zone" to it.trim()))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.timezone_label)) },
                            singleLine = true
                        )
                    }
                }
                TriggerType.BOOT_COMPLETED ->
                    Text(text = stringResource(R.string.trigger_desc_boot), style = MaterialTheme.typography.bodyMedium)
                TriggerType.NFC_TAG_SCANNED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.trigger_desc_nfc_tag), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = draft.config["contains"].orEmpty(),
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("contains" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.notification_contains)) },
                            placeholder = { Text(stringResource(R.string.sms_contains_hint)) },
                            singleLine = true
                        )
                    }
                }
                TriggerType.ALARM_SET_CHANGED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.trigger_desc_alarm_set), style = MaterialTheme.typography.bodyMedium)
                        Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                        OptionChips(
                            options = listOf("SET", "CLEARED"),
                            selected = draft.config["event"] ?: "SET",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                        )
                    }
                }
                TriggerType.WEAR_EVENT -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.trigger_wear_event_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        OutlinedTextField(
                            value = draft.config["watchInstallId"].orEmpty(),
                            onValueChange = {
                                onConfigChange(
                                    draft.copy(
                                        config = draft.config + ("watchInstallId" to it.trim())
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.trigger_wear_event)) },
                            placeholder = { Text(stringResource(R.string.any_device)) },
                            singleLine = true
                        )
                        Text(
                            text = stringResource(R.string.state),
                            style = MaterialTheme.typography.titleSmall
                        )
                        OptionChips(
                            options = listOf("CONNECTED", "DISCONNECTED"),
                            labels = mapOf(
                                "CONNECTED" to stringResource(R.string.state_connected),
                                "DISCONNECTED" to stringResource(R.string.state_disconnected)
                            ),
                            selected = draft.config["state"] ?: "CONNECTED",
                            onSelect = {
                                onConfigChange(
                                    draft.copy(config = draft.config + ("state" to it))
                                )
                            }
                        )
                    }
                }
                // External component identity and approval are managed only by
                // the plugin configuration flow. A persisted trigger stays
                // visible but cannot be changed to an unsafe partial config.
                TriggerType.PLUGIN_EVENT ->
                    Text(text = stringResource(R.string.plugin_no_edit), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val currentTime = when (timePickerTarget) {
            "rangeStart" -> draft.config["rangeStart"] ?: "08:00"
            "rangeEnd" -> draft.config["rangeEnd"] ?: "18:00"
            else -> draft.config["time"] ?: "08:00"
        }
        TimePickerAlert(
            initialTime = currentTime,
            onConfirm = {
                onConfigChange(draft.copy(config = draft.config + (timePickerTarget to it)))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    // The picker state is (re)created fresh on every open because the dialog
    // leaves composition whenever datePickerTarget resets to null on dismiss.
    datePickerTarget?.let { target ->
        val initialMillis = draft.config[target]?.let(::parseDateMillis)
            ?: System.currentTimeMillis()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onConfigChange(
                            draft.copy(config = draft.config + (target to millisToDateString(millis)))
                        )
                    }
                    datePickerTarget = null
                }) {
                    Text(text = stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTarget = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

internal object BluetoothTriggerConfig {
    fun isAnySentinel(deviceName: String): Boolean =
        deviceName.isBlank() || deviceName == "__ANY__" || deviceName == "*" ||
            deviceName.equals("ANY", ignoreCase = true)

    fun isAnyDevice(deviceName: String, deviceAddress: String): Boolean =
        deviceAddress.isBlank() && isAnySentinel(deviceName)
}

private fun parseTimeMinutes(value: String): Int {
    val parts = value.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}
