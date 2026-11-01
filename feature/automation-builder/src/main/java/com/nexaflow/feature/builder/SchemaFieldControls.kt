package com.nexaflow.feature.builder

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.CategoryChip
import com.nexaflow.domain.canonical.CanonicalValueKind
import com.nexaflow.domain.canonical.NodeFieldType
import com.nexaflow.domain.canonical.NodeSchemaField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The controls behind [CanonicalSchemaFieldEditor].
 *
 * Every control is chosen from the field's type and the shared vocabulary in
 * [SchemaFieldPresentation]: choices are chips with translated labels, bounded
 * numbers are a −/+ stepper with a slider, screen timeout is a ROM preset list,
 * and packages are chosen from the app list. A schema field only falls back to
 * a text box for genuinely free text (a path, a search phrase, a JSON body).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CanonicalFieldControl(
    field: NodeSchemaField,
    schemaTarget: String?,
    rawValue: String,
    showLabel: Boolean,
    hasParseError: Boolean,
    hiddenTokens: Set<String> = emptySet(),
    onValueChange: (String) -> Unit,
) {
    val label = schemaFieldLabel(field, showLabel)
    when (field.type) {
        NodeFieldType.BOOLEAN -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = label.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = rawValue.toBooleanStrictOrNull() ?: false,
                    onCheckedChange = { onValueChange(it.toString()) },
                )
            }
            if (hasParseError) ParseErrorText()
        }

        NodeFieldType.ENUM_TOKEN -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                label?.let { Text(text = it, style = MaterialTheme.typography.titleSmall) }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    field.allowedTokens
                        .filterNot { it in hiddenTokens }
                        .forEach { token ->
                            CategoryChip(
                                selected = rawValue == token,
                                onClick = { onValueChange(token) },
                                label = schemaTokenText(token),
                            )
                        }
                }
                if (hasParseError) ParseErrorText()
            }
        }

        NodeFieldType.INTEGER,
        NodeFieldType.DECIMAL,
        NodeFieldType.PERCENTAGE -> {
            NumericFieldControl(
                field = field,
                schemaTarget = schemaTarget,
                rawValue = rawValue,
                label = label,
                hasParseError = hasParseError,
                onValueChange = onValueChange,
            )
        }

        NodeFieldType.DURATION_MS -> {
            DurationFieldControl(
                schemaTarget = schemaTarget,
                rawValue = rawValue,
                label = label,
                hasParseError = hasParseError,
                onValueChange = onValueChange,
            )
        }

        NodeFieldType.PACKAGE_ID -> {
            PackageFieldControl(
                label = label,
                rawValue = rawValue,
                hasParseError = hasParseError,
                onValueChange = onValueChange,
            )
        }

        NodeFieldType.COLLECTION -> {
            if (field.collectionElementKind == CanonicalValueKind.PACKAGE_ID) {
                PackagesCollectionControl(
                    label = label,
                    rawValue = rawValue,
                    onValueChange = onValueChange,
                )
            } else {
                OutlinedTextField(
                    value = rawValue,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = label.orEmpty()) },
                    isError = hasParseError,
                    supportingText = if (hasParseError) ({ ParseErrorText() }) else null,
                )
            }
        }

        NodeFieldType.TIME_OF_DAY -> TimeOfDayFieldControl(
            label = label,
            rawValue = rawValue,
            onValueChange = onValueChange,
        )

        NodeFieldType.DATE -> DateFieldControl(
            label = label,
            rawValue = rawValue,
            onValueChange = onValueChange,
        )

        NodeFieldType.TIMEZONE_ID -> TimezoneFieldControl(
            label = label,
            rawValue = rawValue,
            onValueChange = onValueChange,
        )

        NodeFieldType.COORDINATE -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                label?.let { Text(text = it, style = MaterialTheme.typography.titleSmall) }
                Text(
                    text = rawValue.ifBlank { stringResource(R.string.location_not_set) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                if (hasParseError) ParseErrorText()
            }
        }

        else -> OutlinedTextField(
            value = rawValue,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = label.orEmpty()) },
            singleLine = field.type != NodeFieldType.TEXT && field.type != NodeFieldType.JSON,
            minLines = if (field.type == NodeFieldType.JSON) 3 else 1,
            maxLines = if (field.type == NodeFieldType.JSON) 8 else Int.MAX_VALUE,
            isError = hasParseError,
            supportingText = if (hasParseError) ({ ParseErrorText() }) else null,
        )
    }
}

/** Translated label of a schema field, or null when the single choice explains itself. */
@Composable
internal fun schemaFieldLabel(field: NodeSchemaField, showLabel: Boolean): String? {
    if (!showLabel) return null
    val res = schemaFieldView(field.id.value).labelRes
    return if (res != null) stringResource(res) else humanizeSchemaToken(field.id.value)
}

/** Translated token label, falling back to a readable phrase instead of a constant. */
@Composable
internal fun schemaTokenText(token: String): String {
    val res = schemaTokenLabel(token)
    return if (res != null) stringResource(res) else humanizeSchemaToken(token)
}

@Composable
private fun ParseErrorText() {
    Text(
        text = stringResource(R.string.canonical_field_invalid_value),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/**
 * Bounded number: a value line, − and + buttons and a slider. The user never
 * types a number, and the shown unit follows the field (percent, °C, MB/GB).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumericFieldControl(
    field: NodeSchemaField,
    schemaTarget: String?,
    rawValue: String,
    label: String?,
    hasParseError: Boolean,
    onValueChange: (String) -> Unit,
) {
    val bounds = schemaSliderBounds(field.id.value, schemaTarget, field.minimum, field.maximum)
    val step = schemaStepSize(field.id.value, schemaTarget)
    val current = rawValue.trim().replace(',', '.').toFloatOrNull()?.coerceIn(bounds) ?: bounds.start
    val valueText = schemaNumberText(rawValue, field.id.value, schemaTarget)
    SteppedValueRow(
        label = label.orEmpty(),
        valueText = valueText,
        value = current,
        valueRange = bounds,
        step = step,
        onValueChange = { value ->
            val bounded = value.coerceIn(bounds)
            onValueChange(
                if (field.type == NodeFieldType.DECIMAL) formatDecimal(bounded) else bounded.roundToInt().toString()
            )
        },
    )
    if (hasParseError) ParseErrorText()
}

/** Formatted stored value using the field's unit. */
@Composable
internal fun schemaNumberText(rawValue: String, fieldId: String, schemaTarget: String?): String =
    schemaValueText(
        raw = rawValue,
        fieldId = fieldId,
        schemaTarget = schemaTarget,
        percentPattern = stringResource(R.string.schema_value_percent),
        celsiusPattern = stringResource(R.string.schema_value_celsius),
        megabytePattern = stringResource(R.string.schema_value_megabytes),
        gigabytePattern = stringResource(R.string.schema_value_gigabytes),
        secondsPattern = stringResource(R.string.schema_value_seconds),
        minutesPattern = stringResource(R.string.schema_value_minutes),
        barsPattern = stringResource(R.string.schema_value_bars),
    )

private fun formatDecimal(value: Float): String =
    if (value == value.roundToInt().toFloat()) value.roundToInt().toString()
    else String.format(Locale.US, "%.1f", value)

/**
 * Duration: for the screen-timeout trigger the choices are the ROM's own preset
 * values (plus whatever the device currently reports), otherwise a −/+ stepper
 * in minutes. Either way the value is picked, never typed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationFieldControl(
    schemaTarget: String?,
    rawValue: String,
    label: String?,
    hasParseError: Boolean,
    onValueChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val isScreenTimeout = schemaTarget?.contains("screen_timeout", ignoreCase = true) == true
    // Reading the ROM's current timeout makes the list the device's own values
    // instead of a hard-coded table.
    val deviceSeconds = remember(isScreenTimeout, context) {
        if (!isScreenTimeout) null else runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) / 1000L
        }.getOrNull()?.takeIf { it > 0 }
    }
    val currentSeconds = rawValue.toLongOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (isScreenTimeout) {
            val presets = (listOfNotNull(deviceSeconds) + screenTimeoutPresetsSeconds)
                .distinct()
                .sorted()
            label?.let { Text(text = it, style = MaterialTheme.typography.titleSmall) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                presets.forEach { seconds ->
                    CategoryChip(
                        selected = currentSeconds == seconds,
                        onClick = { onValueChange((seconds * 1000L).toString()) },
                        label = screenTimeoutLabel(seconds),
                    )
                }
            }
        } else {
            val minutes = (currentSeconds ?: 0L) / 60_000L
            SteppedValueRow(
                label = label.orEmpty(),
                valueText = stringResource(R.string.schema_value_minutes, minutes),
                value = minutes.toFloat().coerceIn(0f, 180f),
                valueRange = 0f..180f,
                step = 1f,
                onValueChange = { onValueChange((it.roundToLong() * 60_000L).toString()) },
            )
        }
        if (hasParseError) ParseErrorText()
    }
}

@Composable
private fun screenTimeoutLabel(seconds: Long): String =
    formatScreenTimeout(
        seconds = seconds,
        minutesPattern = stringResource(R.string.schema_duration_minutes),
        secondsPattern = stringResource(R.string.schema_duration_seconds),
    )

/** One application, chosen from the installed list. */
@Composable
private fun PackageFieldControl(
    label: String?,
    rawValue: String,
    hasParseError: Boolean,
    onValueChange: (String) -> Unit,
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    val packageLabel = remember(rawValue, showPicker) {
        if (rawValue.isBlank()) null else runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(rawValue, 0)
            ).toString()
        }.getOrDefault(rawValue)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let { Text(text = it, style = MaterialTheme.typography.titleSmall) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = packageLabel ?: stringResource(R.string.any_app),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { showPicker = true }) {
                Text(stringResource(R.string.canonical_choose_app))
            }
        }
        if (hasParseError) ParseErrorText()
    }
    if (showPicker) {
        // The builder's own picker: one search field, the system-app filter and
        // the same bottom sheet every other app choice in the app opens.
        AppPickerDialog(
            onPickSingle = { app ->
                onValueChange(app.packageName)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** A list of applications, chosen and removed without typing package names. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackagesCollectionControl(
    label: String?,
    rawValue: String,
    onValueChange: (String) -> Unit,
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    val packages = remember(rawValue) {
        rawValue.split('|', ',', ';').map(String::trim).filter(String::isNotEmpty)
    }
    val labels = remember(packages) {
        packages.associateWith { packageName ->
            runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            }.getOrDefault(packageName)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let { Text(text = it, style = MaterialTheme.typography.titleSmall) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (packages.isEmpty()) {
                Text(
                    text = stringResource(R.string.any_app),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            packages.forEach { packageName ->
                AssistChip(
                    onClick = {
                        onValueChange(packages.filterNot { it == packageName }.joinToString(","))
                    },
                    label = { Text(labels[packageName] ?: packageName, maxLines = 1) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.remove_trigger),
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
        TextButton(onClick = { showPicker = true }) {
            Text(stringResource(R.string.choose_apps))
        }
    }
    if (showPicker) {
        // One write path for the sheet: the multi-select mode reports a list,
        // and the single handler (unused while `multiSelect` is on) reuses it.
        fun applyPackages(names: List<String>) {
            onValueChange(names.joinToString(","))
            showPicker = false
        }
        AppPickerDialog(
            onPickSingle = { app -> applyPackages(listOf(app.packageName)) },
            onDismiss = { showPicker = false },
            onPickMultiple = { picked -> applyPackages(picked.map { it.packageName }) },
            multiSelect = true,
            preSelectedPackages = packages,
        )
    }
}


@Composable
private fun TimeOfDayFieldControl(
    label: String?,
    rawValue: String,
    onValueChange: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val parts = rawValue.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val pickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let { Text(text = it, style = MaterialTheme.typography.titleSmall) }
        Button(onClick = { showPicker = true }) {
            Text(rawValue.ifBlank { stringResource(R.string.canonical_choose_time) })
        }
    }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange("%02d:%02d".format(pickerState.hour, pickerState.minute))
                    showPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
}

@Composable
private fun DateFieldControl(
    label: String?,
    rawValue: String,
    onValueChange: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val initialMillis = remember(rawValue) {
        runCatching { LocalDate.parse(rawValue).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let { Text(text = it, style = MaterialTheme.typography.titleSmall) }
        Button(onClick = { showPicker = true }) {
            Text(rawValue.ifBlank { stringResource(R.string.canonical_choose_date) })
        }
    }
    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onValueChange(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        )
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }
}

/** Time zone picker: a searchable list, so the zone id is never typed. */
@Composable
private fun TimezoneFieldControl(
    label: String?,
    rawValue: String,
    onValueChange: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let { Text(text = it, style = MaterialTheme.typography.titleSmall) }
        Button(onClick = { showPicker = true }) {
            Text(rawValue.ifBlank { stringResource(R.string.schema_field_timezone) })
        }
    }
    if (showPicker) {
        val zones = remember { java.util.TimeZone.getAvailableIDs().sorted() }
        var query by remember { mutableStateOf("") }
        val filtered = zones.filter { it.contains(query, ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.schema_field_timezone)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.canonical_search_apps)) },
                        singleLine = true,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filtered) { zone ->
                            TextButton(
                                onClick = { onValueChange(zone); showPicker = false },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(zone) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * Value line with − / + and a slider — the single numeric control of the
 * builder. Both buttons and the slider write the same value.
 */
@Composable
internal fun SteppedValueRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    step: Float = 1f,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange((value - step).coerceAtLeast(valueRange.start)) }) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.schema_decrease),
                )
            }
            SyncedSlider(
                value = value,
                onValueChange = { onValueChange(it.coerceIn(valueRange)) },
                valueRange = valueRange,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onValueChange((value + step).coerceAtMost(valueRange.endInclusive)) }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.schema_increase),
                )
            }
        }
    }
}
