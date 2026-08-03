package com.nexaflow.feature.builder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowIcons
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.ToggleRow
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Condition
import com.nexaflow.domain.models.ConditionType
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType

private data class ActionOption(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val actionType: ActionType
)

private data class InstalledApp(
    val label: String,
    val packageName: String
)

private val actionOptions = listOf(
    ActionOption("Brightness", "Set screen brightness", Icons.Filled.FlashOn, Color(0xFF1B62B7), ActionType.SYSTEM_BRIGHTNESS),
    ActionOption("Volume", "Change media volume", Icons.Filled.VolumeUp, Color(0xFF7A5BD1), ActionType.SYSTEM_VOLUME),
    ActionOption("Do Not Disturb", "Toggle DND mode", Icons.Filled.DoNotDisturb, Color(0xFFE5533D), ActionType.SYSTEM_DND),
    ActionOption("Open app", "Launch an application", Icons.Filled.Apps, Color(0xFF2FA84F), ActionType.SYSTEM_OPEN_APP),
    ActionOption("Notification", "Send a notification", Icons.Filled.NotificationImportant, Color(0xFFE8A33D), ActionType.SYSTEM_SEND_NOTIFICATION),
    ActionOption("Screen Rotation", "Toggle rotation lock", Icons.Filled.ScreenRotation, Color(0xFF13A5A8), ActionType.SYSTEM_SCREEN_ROTATION),
    ActionOption("Battery alert", "Notify when battery drops", Icons.Filled.BatteryAlert, Color(0xFFE5533D), ActionType.BATTERY_ALERTS),
    ActionOption("Shizuku command", "Run a shell command via Shizuku", Icons.Filled.Terminal, Color(0xFF1B62B7), ActionType.ADVANCED_SHIZUKU),
    ActionOption("Root command", "Run a shell command via root", Icons.Filled.Terminal, Color(0xFFE5533D), ActionType.ADVANCED_ROOT)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationBuilderScreen(navController: NavController) {
    val viewModel: AutomationBuilderViewModel = hiltViewModel()
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("Time") }
    var batteryCondition by remember { mutableStateOf(false) }
    var timeRangeCondition by remember { mutableStateOf(false) }
    var selectedIconIndex by remember { mutableStateOf(0) }
    var scheduledTime by remember { mutableStateOf("08:00") }
    var showTimePicker by remember { mutableStateOf(false) }
    var batteryThreshold by remember { mutableStateOf(20) }
    var rangeStart by remember { mutableStateOf("22:00") }
    var rangeEnd by remember { mutableStateOf("07:00") }
    var rangePickerTarget by remember { mutableStateOf<String?>(null) }
    var appPackage by remember { mutableStateOf("") }
    var appPickerTarget by remember { mutableStateOf<String?>(null) }
    val actionConfigs = remember { mutableStateMapOf<ActionType, Map<String, String>>() }
    val selectedActions = remember { mutableSetOf<ActionOption>() }

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    DisposableEffect(savedStateHandle) {
        val observer = Observer<Int> { index ->
            selectedIconIndex = index
        }
        savedStateHandle?.getLiveData<Int>("selected_icon")?.observeForever(observer)
        onDispose {
            savedStateHandle?.getLiveData<Int>("selected_icon")?.removeObserver(observer)
        }
    }

    fun save() {
        val triggerType = when (trigger) {
            "App" -> TriggerType.APPLICATION
            "Device" -> TriggerType.DEVICE
            "Connectivity" -> TriggerType.CONNECTIVITY
            else -> TriggerType.TIME
        }
        val triggerConfig = when (triggerType) {
            TriggerType.TIME -> mapOf("time" to scheduledTime)
            TriggerType.APPLICATION -> mapOf("package" to appPackage)
            else -> emptyMap()
        }
        val conditions = buildList {
            if (batteryCondition) {
                add(Condition(ConditionType.BATTERY_PERCENTAGE, mapOf("above" to batteryThreshold.toString())))
            }
            if (timeRangeCondition) {
                add(Condition(ConditionType.TIME_RANGE, mapOf("start" to rangeStart, "end" to rangeEnd)))
            }
        }
        val actions = selectedActions.map { Action(it.actionType, actionConfigs[it.actionType] ?: emptyMap()) }
        viewModel.saveAutomation(
            name = name,
            icon = NexaFlowIcons.all[selectedIconIndex].first,
            trigger = Trigger(triggerType, triggerConfig),
            conditions = conditions,
            actions = actions
        )
        navController.popBackStack()
    }

    val triggerOptions = listOf("Time", "App", "Device", "Connectivity")

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = "New Automation",
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { save() }) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NexaFlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Name", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(text = "e.g. Morning Routine") },
                        singleLine = true
                    )
                }
            }
            NexaFlowCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("icon_picker") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconBadge(
                        icon = iconVector(NexaFlowIcons.all[selectedIconIndex].first),
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Icon", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Tap to choose an icon",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
            SectionHeader(text = "WHEN")
            NexaFlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    triggerOptions.forEach { option ->
                        FilterChip(
                            selected = trigger == option,
                            onClick = { trigger = option },
                            label = { Text(text = option) }
                        )
                    }
                    if (trigger == "Time") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTimePicker = true }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Trigger time",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Runs daily at the chosen time",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            TextButton(onClick = { showTimePicker = true }) {
                                Text(text = scheduledTime)
                            }
                        }
                    }
                    if (trigger == "App") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = appPackage,
                                onValueChange = { appPackage = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(text = "Package name") },
                                placeholder = { Text(text = "e.g. com.whatsapp") },
                                singleLine = true
                            )
                            TextButton(onClick = { appPickerTarget = "trigger" }) {
                                Text(text = "Choose from installed apps")
                            }
                            PermissionHint(
                                text = "App detection needs the accessibility service",
                                buttonLabel = "Enable",
                                onClick = { PermissionShortcuts.openAccessibilitySettings(context) }
                            )
                        }
                    }
                    if (trigger == "Device" || trigger == "Connectivity") {
                        Text(
                            text = "This trigger type is not wired to an event listener yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            SectionHeader(text = "CONDITIONS")
            NexaFlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleRow(
                        icon = Icons.Filled.Bolt,
                        title = "Battery above threshold",
                        subtitle = "Only run when battery is above the level",
                        checked = batteryCondition,
                        onCheckedChange = { batteryCondition = it }
                    )
                    if (batteryCondition) {
                        SliderRow(
                            label = "Minimum battery: $batteryThreshold%",
                            value = batteryThreshold.toFloat(),
                            onValueChange = { batteryThreshold = it.toInt() },
                            valueRange = 5f..100f
                        )
                    }
                    ToggleRow(
                        icon = Icons.Filled.ScreenRotation,
                        title = "Within time range",
                        subtitle = "Only run between the configured hours",
                        checked = timeRangeCondition,
                        onCheckedChange = { timeRangeCondition = it }
                    )
                    if (timeRangeCondition) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "From", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { rangePickerTarget = "start" }) {
                                    Text(text = rangeStart)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "To", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { rangePickerTarget = "end" }) {
                                    Text(text = rangeEnd)
                                }
                            }
                        }
                    }
                }
            }
            SectionHeader(text = "THEN")
            NexaFlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    actionOptions.forEach { option ->
                        val checked = option in selectedActions
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedActions.remove(option) else selectedActions.add(option)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconBadge(icon = option.icon, containerColor = option.color, size = 36)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = option.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = option.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Icon(
                                imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Add,
                                contentDescription = null,
                                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        if (checked) {
                            val config = actionConfigs[option.actionType] ?: emptyMap()
                            ActionConfigEditor(
                                option = option,
                                config = config,
                                onConfigChange = { actionConfigs[option.actionType] = it },
                                onPickApp = { appPickerTarget = "action" }
                            )
                            when (option.actionType) {
                                ActionType.SYSTEM_BRIGHTNESS,
                                ActionType.SYSTEM_SCREEN_ROTATION -> PermissionHint(
                                    text = "Needs permission to modify system settings",
                                    buttonLabel = "Grant",
                                    onClick = { PermissionShortcuts.openWriteSettings(context) }
                                )
                                ActionType.SYSTEM_DND -> PermissionHint(
                                    text = "Needs Do Not Disturb access",
                                    buttonLabel = "Grant",
                                    onClick = { PermissionShortcuts.openNotificationPolicy(context) }
                                )
                                ActionType.ADVANCED_SHIZUKU -> PermissionHint(
                                    text = "Requires the Shizuku app and a grant",
                                    buttonLabel = "Info",
                                    onClick = { PermissionShortcuts.openShizukuManager(context) }
                                )
                                ActionType.ADVANCED_ROOT -> PermissionHint(
                                    text = "Requires root (su binary)",
                                    buttonLabel = "Info",
                                    onClick = { PermissionShortcuts.openShizukuManager(context) }
                                )
                                else -> Unit
                            }
                            Spacer(modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Save Automation")
            }
        }
    }

    if (showTimePicker) {
        TimePickerAlert(
            initialTime = scheduledTime,
            onConfirm = { scheduledTime = it; showTimePicker = false },
            onDismiss = { showTimePicker = false }
        )
    }

    rangePickerTarget?.let { target ->
        val initial = if (target == "start") rangeStart else rangeEnd
        TimePickerAlert(
            initialTime = initial,
            onConfirm = {
                if (target == "start") rangeStart = it else rangeEnd = it
                rangePickerTarget = null
            },
            onDismiss = { rangePickerTarget = null }
        )
    }

    appPickerTarget?.let { target ->
        InstalledAppsDialog(
            onPick = { app ->
                if (target == "trigger") {
                    appPackage = app.packageName
                } else {
                    actionConfigs[ActionType.SYSTEM_OPEN_APP] = mapOf("package" to app.packageName)
                }
                appPickerTarget = null
            },
            onDismiss = { appPickerTarget = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerAlert(
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
                Text(text = "OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        text = { TimePicker(state = pickerState) }
    )
}

@Composable
private fun SliderRow(
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

@Composable
private fun PermissionHint(
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
private fun ActionConfigEditor(
    option: ActionOption,
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
    onPickApp: () -> Unit
) {
    when (option.actionType) {
        ActionType.SYSTEM_BRIGHTNESS -> {
            val value = config["value"]?.toIntOrNull() ?: 128
            SliderRow(
                label = "Brightness: $value",
                value = value.toFloat(),
                onValueChange = { onConfigChange(mapOf("value" to it.toInt().toString())) },
                valueRange = 0f..255f
            )
        }
        ActionType.SYSTEM_VOLUME -> {
            val value = config["value"]?.toIntOrNull() ?: 50
            SliderRow(
                label = "Volume: $value",
                value = value.toFloat(),
                onValueChange = { onConfigChange(mapOf("value" to it.toInt().toString())) },
                valueRange = 0f..100f
            )
        }
        ActionType.SYSTEM_DND -> {
            val enabled = config["enabled"]?.toBoolean() ?: true
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Turn on", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = enabled,
                    onCheckedChange = { onConfigChange(mapOf("enabled" to it.toString())) }
                )
            }
        }
        ActionType.SYSTEM_OPEN_APP -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = config["package"] ?: "",
                    onValueChange = { onConfigChange(mapOf("package" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Package name") },
                    singleLine = true
                )
                TextButton(onClick = onPickApp) {
                    Text(text = "Choose from installed apps")
                }
            }
        }
        ActionType.SYSTEM_SEND_NOTIFICATION -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["title"] ?: "",
                    onValueChange = { onConfigChange(config + ("title" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Title") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["text"] ?: "",
                    onValueChange = { onConfigChange(config + ("text" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Text") },
                    singleLine = true
                )
            }
        }
        ActionType.SYSTEM_SCREEN_ROTATION -> {
            val autoRotate = config["autoRotate"]?.toBoolean() ?: true
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Auto-rotate", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = autoRotate,
                    onCheckedChange = { onConfigChange(mapOf("autoRotate" to it.toString())) }
                )
            }
        }
        ActionType.BATTERY_ALERTS -> {
            val below = config["below"]?.toIntOrNull() ?: 20
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SliderRow(
                    label = "Alert below: $below%",
                    value = below.toFloat(),
                    onValueChange = { onConfigChange(config + ("below" to it.toInt().toString())) },
                    valueRange = 5f..100f
                )
                OutlinedTextField(
                    value = config["message"] ?: "",
                    onValueChange = { onConfigChange(config + ("message" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Message (optional)") },
                    singleLine = true
                )
            }
        }
        ActionType.ADVANCED_SHIZUKU,
        ActionType.ADVANCED_ROOT -> {
            OutlinedTextField(
                value = config["command"] ?: "",
                onValueChange = { onConfigChange(mapOf("command" to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Shell command") },
                placeholder = { Text(text = "e.g. settings put global airplane_mode_on 1") }
            )
        }
        ActionType.BATTERY_CHARGING_NOTIFICATIONS,
        ActionType.APPLICATION_LAUNCH_APP,
        ActionType.APPLICATION_CLOSE_APP -> Unit
    }
}

@Composable
private fun InstalledAppsDialog(
    onPick: (InstalledApp) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val apps = remember { loadLaunchableApps(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Choose an app") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

private fun loadLaunchableApps(context: Context): List<InstalledApp> {
    return try {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { resolveInfo ->
                val info = resolveInfo.activityInfo ?: return@mapNotNull null
                val label = try {
                    packageManager.getApplicationLabel(info.applicationInfo).toString()
                } catch (_: Throwable) {
                    info.packageName
                }
                InstalledApp(label, info.packageName)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    } catch (_: Throwable) {
        emptyList()
    }
}

private object PermissionShortcuts {
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

    fun openShizukuManager(context: Context) {
        try {
            context.startActivity(context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api"))
        } catch (_: Throwable) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
