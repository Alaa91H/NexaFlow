package com.nexaflow.feature.builder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.ScreenRotation
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

private val actionOptions = listOf(
    ActionOption("Brightness", "Set screen brightness", Icons.Filled.FlashOn, Color(0xFF1B62B7), ActionType.SYSTEM_BRIGHTNESS),
    ActionOption("Volume", "Change media volume", Icons.Filled.VolumeUp, Color(0xFF7A5BD1), ActionType.SYSTEM_VOLUME),
    ActionOption("Do Not Disturb", "Toggle DND mode", Icons.Filled.DoNotDisturb, Color(0xFFE5533D), ActionType.SYSTEM_DND),
    ActionOption("Open app", "Launch an application", Icons.Filled.Add, Color(0xFF2FA84F), ActionType.SYSTEM_OPEN_APP),
    ActionOption("Notification", "Send a notification", Icons.Filled.NotificationImportant, Color(0xFFE8A33D), ActionType.SYSTEM_SEND_NOTIFICATION),
    ActionOption("Screen Rotation", "Toggle rotation lock", Icons.Filled.ScreenRotation, Color(0xFF13A5A8), ActionType.SYSTEM_SCREEN_ROTATION)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationBuilderScreen(navController: NavController) {
    val viewModel: AutomationBuilderViewModel = hiltViewModel()
    var name by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("Time") }
    var batteryCondition by remember { mutableStateOf(false) }
    var timeRangeCondition by remember { mutableStateOf(false) }
    var selectedIconIndex by remember { mutableStateOf(0) }
    var scheduledTime by remember { mutableStateOf("08:00") }
    var showTimePicker by remember { mutableStateOf(false) }
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
        val conditions = buildList {
            if (batteryCondition) add(Condition(ConditionType.BATTERY_PERCENTAGE, mapOf("above" to "20")))
            if (timeRangeCondition) add(Condition(ConditionType.TIME_RANGE, mapOf("range" to "custom")))
        }
        val actions = selectedActions.map { Action(it.actionType, emptyMap()) }
        val triggerConfig = if (triggerType == TriggerType.TIME) mapOf("time" to scheduledTime) else emptyMap()
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
                }
            }
            SectionHeader(text = "CONDITIONS")
            NexaFlowCard {
                ToggleRow(
                    icon = Icons.Filled.Bolt,
                    title = "Battery above 20%",
                    subtitle = "Only run when battery is above 20%",
                    checked = batteryCondition,
                    onCheckedChange = { batteryCondition = it }
                )
                ToggleRow(
                    icon = Icons.Filled.ScreenRotation,
                    title = "Within time range",
                    subtitle = "Only run between the configured hours",
                    checked = timeRangeCondition,
                    onCheckedChange = { timeRangeCondition = it }
                )
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
                    }
                }
            }
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Save Automation")
            }
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = scheduledTime.substringBefore(":").toIntOrNull() ?: 8,
            initialMinute = scheduledTime.substringAfter(":").toIntOrNull() ?: 0
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    scheduledTime = "%02d:%02d".format(pickerState.hour, pickerState.minute)
                    showTimePicker = false
                }) {
                    Text(text = "OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(text = "Cancel")
                }
            },
            text = { TimePicker(state = pickerState) }
        )
    }
}
