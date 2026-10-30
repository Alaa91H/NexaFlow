package com.nexaflow.feature.builder

import android.Manifest
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.SelectChip
import com.nexaflow.domain.models.TriggerType

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
    var datePickerTarget by remember { mutableStateOf<String?>(null) } // "date" | "startDate" | "endDate" | "excludedDate"
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
                    val temporalFiltersBinding =
                        CanonicalBuilderSchemaBridge.temporalFiltersBindingForTrigger(draft.type)
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
                        if (temporalFiltersBinding != null) {
                            HorizontalDivider()
                            Text(
                                text = stringResource(R.string.trigger_temporal_filters_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            CanonicalSchemaFieldEditor(
                                binding = temporalFiltersBinding,
                                config = draft.config,
                                onConfigChange = { updated ->
                                    onConfigChange(draft.copy(config = updated))
                                },
                            )
                        }
                    } else {
                        when (draft.type) {
                TriggerType.TIME -> TimeTriggerConfig(
                    draft = draft,
                    onConfigChange = onConfigChange,
                    onPickTime = { target -> timePickerTarget = target; showTimePicker = true },
                    onPickDate = { datePickerTarget = it },
                    refreshKey = refreshKey,
                    onExplainSpecial = onExplainSpecial
                )
                TriggerType.BATTERY ->
                    BatteryTriggerConfig(draft, onConfigChange)
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
                            val calibration = draft.config[com.nexaflow.domain.models.NumericSensors.CALIBRATION_OFFSET_KEY].orEmpty()
                            val parsedCalibration = calibration.toFloatOrNull()?.takeIf { it.isFinite() }
                            val calibrationInvalid = calibration.isNotBlank() &&
                                (parsedCalibration == null || kotlin.math.abs(parsedCalibration) > numeric.calibrationLimit)
                            OutlinedTextField(
                                value = calibration,
                                onValueChange = {
                                    onConfigChange(draft.copy(config = draft.config +
                                        (com.nexaflow.domain.models.NumericSensors.CALIBRATION_OFFSET_KEY to it)))
                                },
                                label = { Text(stringResource(R.string.sensor_numeric_calibration, numeric.unit)) },
                                isError = calibrationInvalid,
                                supportingText = {
                                    if (calibrationInvalid) Text(stringResource(R.string.sensor_invalid_calibration, numeric.calibrationLimit, numeric.unit))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            val samplingPeriod = com.nexaflow.domain.models.NumericSensors.samplePeriodUs(draft.config)
                            Text(stringResource(R.string.sensor_sampling_rate), style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                com.nexaflow.domain.models.NumericSensors.samplePeriodsUs.sorted().forEach { period ->
                                    val hz = 1_000_000 / period
                                    SelectChip(
                                        selected = samplingPeriod == period,
                                        onClick = { onConfigChange(draft.copy(config = draft.config +
                                            (com.nexaflow.domain.models.NumericSensors.SAMPLE_PERIOD_KEY to period.toString()))) },
                                        label = stringResource(R.string.sensor_sampling_rate_option, hz)
                                    )
                                }
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
                TriggerType.APP_INSTALLED ->
                    AppInstalledConfig(draft, onConfigChange, onPickApp)
                TriggerType.VOLUME_CHANGED -> VolumeChangedConfig(draft, onConfigChange)
                TriggerType.BRIGHTNESS_LEVEL -> BrightnessLevelConfig(draft, onConfigChange)
                TriggerType.STORAGE_LOW -> StorageLowConfig(draft, onConfigChange)
                TriggerType.WIFI_SIGNAL_STRENGTH, TriggerType.CELL_SIGNAL_STRENGTH ->
                    SignalStrengthConfig(draft, onConfigChange)
                TriggerType.BATTERY_TEMPERATURE -> BatteryTemperatureConfig(draft, onConfigChange)
                TriggerType.CLIPBOARD_CHANGED -> ClipboardChangedConfig(draft, onConfigChange)
                TriggerType.SCREEN_TIMEOUT_CHANGED -> ScreenTimeoutChangedConfig(draft, onConfigChange)
                TriggerType.NFC_TAG_SCANNED -> NfcTagScannedConfig(draft, onConfigChange)
                TriggerType.WEAR_EVENT -> WearEventConfig(draft, onConfigChange)
                // External component identity and approval are managed only by
                // the plugin configuration flow. A persisted trigger stays
                // visible but cannot be changed to an unsafe partial config.
                TriggerType.PLUGIN_EVENT ->
                    Text(text = stringResource(R.string.plugin_no_edit), style = MaterialTheme.typography.bodyMedium)
                // Every other kind is rendered by CanonicalSchemaFieldEditor
                // above; TriggerEditorRoutingTest fails if a type loses its
                // schema binding and would land here.
                else -> Unit
                        }
                        if (temporalFiltersBinding != null) {
                            HorizontalDivider()
                            Text(
                                text = stringResource(R.string.trigger_temporal_filters_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            CanonicalSchemaFieldEditor(
                                binding = temporalFiltersBinding,
                                config = draft.config,
                                onConfigChange = { updated ->
                                    onConfigChange(draft.copy(config = updated))
                                },
                            )
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
                        val selectedDate = millisToDateString(millis)
                        val updatedConfig = if (target == "excludedDate") {
                            val dates = draft.config["excludedDates"].orEmpty().split(',')
                                .map(String::trim).filter(String::isNotBlank)
                            draft.config + ("excludedDates" to (dates + selectedDate).distinct().sorted().joinToString(","))
                        } else {
                            draft.config + (target to selectedDate)
                        }
                        onConfigChange(draft.copy(config = updatedConfig))
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

