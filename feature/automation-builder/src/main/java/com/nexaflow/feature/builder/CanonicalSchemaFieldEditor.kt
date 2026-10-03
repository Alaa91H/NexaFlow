package com.nexaflow.feature.builder

import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.nexaflow.core.ui.SelectChip
import com.nexaflow.domain.canonical.BooleanValue
import com.nexaflow.domain.canonical.CanonicalValue
import com.nexaflow.domain.canonical.CanonicalValueKind
import com.nexaflow.domain.canonical.CollectionValue
import com.nexaflow.domain.canonical.CoordinateValue
import com.nexaflow.domain.canonical.DateValue
import com.nexaflow.domain.canonical.DecimalValue
import com.nexaflow.domain.canonical.DisclosureState
import com.nexaflow.domain.canonical.DurationValue
import com.nexaflow.domain.canonical.EnumTokenValue
import com.nexaflow.domain.canonical.ExpressionValue
import com.nexaflow.domain.canonical.IntegerValue
import com.nexaflow.domain.canonical.JsonValue
import com.nexaflow.domain.canonical.NodeConfiguratorState
import com.nexaflow.domain.canonical.NodeFieldType
import com.nexaflow.domain.canonical.NodeFieldValue
import com.nexaflow.domain.canonical.NodeSchemaField
import com.nexaflow.domain.canonical.NodeSchemaLevel
import com.nexaflow.domain.canonical.PackageIdValue
import com.nexaflow.domain.canonical.PercentageValue
import com.nexaflow.domain.canonical.TextValue
import com.nexaflow.domain.canonical.TimeOfDayValue
import com.nexaflow.domain.canonical.TimezoneValue
import com.nexaflow.domain.canonical.UriValue
import kotlinx.serialization.json.Json

/**
 * T12 schema-driven field renderer. The canonical schema decides which fields
 * exist, their typed values, enum choices, bounds, defaults, visibility and
 * validation. V1/V2 config keys are translated only at the boundary.
 */
@Composable
internal fun CanonicalSchemaFieldEditor(
    binding: CanonicalBuilderSchemaBinding,
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val schema = binding.schema
    val parseErrors = remember(schema.schemaId, config, binding.legacyKeys) {
        canonicalFieldParseErrors(schema, binding.legacyKeys, config)
    }
    val values = remember(schema.schemaId, config) {
        schema.fields.mapNotNull { field ->
            val legacyKey = binding.legacyKeys[field.id.value] ?: field.id.value
            val raw = config[legacyKey] ?: field.default?.raw?.let(::canonicalValueToLegacy)
            raw?.let { value ->
                val parsed = parseCanonicalField(field, value)
                when {
                    parsed != null -> NodeFieldValue(field.id, parsed)
                    field.type != NodeFieldType.SECRET_REFERENCE && value.isNotBlank() ->
                        NodeFieldValue(field.id, TextValue(value))
                    else -> null
                }
            }
        }
    }
    var disclosureLevelName by rememberSaveable(schema.schemaId) {
        mutableStateOf(NodeSchemaLevel.BASIC.name)
    }
    val disclosure = remember(schema.schemaId, disclosureLevelName) {
        DisclosureState(
            runCatching { NodeSchemaLevel.valueOf(disclosureLevelName) }
                .getOrDefault(NodeSchemaLevel.BASIC),
        )
    }
    val state = remember(schema.schemaId, values, disclosure) {
        NodeConfiguratorState(schema = schema, values = values, disclosure = disclosure)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.visibleFields().forEach { field ->
            val legacyKey = binding.legacyKeys[field.id.value] ?: field.id.value
            val current = config[legacyKey]
                ?: field.default?.raw?.let(::canonicalValueToLegacy)
                .orEmpty()
            CanonicalFieldControl(
                field = field,
                rawValue = current,
                hasParseError = field.id.value in parseErrors,
                onValueChange = { updated ->
                    onConfigChange(config + (legacyKey to updated))
                },
            )
            field.helpText?.takeIf { it.isNotBlank() }?.let { help ->
                Text(text = help, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }

        val nextLevel = when (state.disclosure.maximumLevel) {
            NodeSchemaLevel.BASIC -> NodeSchemaLevel.ADVANCED
            NodeSchemaLevel.ADVANCED -> NodeSchemaLevel.EXPERT
            NodeSchemaLevel.EXPERT -> null
        }
        if (nextLevel != null && schema.fields.any { it.level == nextLevel }) {
            TextButton(
                onClick = { disclosureLevelName = nextLevel.name }
            ) {
                Text(text = nextLevel.name)
            }
        }

        state.validationIssues().forEach { issue ->
            Text(
                text = issue.message,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CanonicalFieldControl(
    field: NodeSchemaField,
    rawValue: String,
    hasParseError: Boolean,
    onValueChange: (String) -> Unit,
) {
    when (field.type) {
        NodeFieldType.BOOLEAN -> {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = field.id.value)
                Switch(
                    checked = rawValue.toBooleanStrictOrNull() ?: false,
                    onCheckedChange = { onValueChange(it.toString()) },
                )
            }
            if (hasParseError) Text(
                stringResource(R.string.canonical_field_invalid_value),
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            )
        }
        NodeFieldType.ENUM_TOKEN -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = field.id.value)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    field.allowedTokens.forEach { token ->
                        SelectChip(
                            selected = rawValue == token,
                            onClick = { onValueChange(token) },
                            label = token,
                        )
                    }
                }
                if (hasParseError) Text(
                    stringResource(R.string.canonical_field_invalid_value),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
            }
        }
        NodeFieldType.SECRET_REFERENCE -> {
            // Canonical workflows store a secret reference, never the secret.
            OutlinedTextField(
                value = if (rawValue.isBlank()) "" else "••••",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = field.id.value) },
                singleLine = true,
                isError = hasParseError,
                supportingText = if (hasParseError) {
                    { Text(stringResource(R.string.canonical_field_invalid_value)) }
                } else null,
            )
        }
        NodeFieldType.TIME_OF_DAY -> {
            var showPicker by remember(field.id.value) { mutableStateOf(false) }
            val parts = rawValue.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
            val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
            val pickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rawValue,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(text = field.id.value) },
                    singleLine = true,
                    isError = hasParseError,
                    supportingText = if (hasParseError) ({ Text(stringResource(R.string.canonical_field_invalid_value)) }) else null,
                )
                Button(onClick = { showPicker = true }) { Text(stringResource(R.string.canonical_choose_time)) }
            }
            if (showPicker) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            onValueChange("%02d:%02d".format(pickerState.hour, pickerState.minute))
                            showPicker = false
                        }) { Text(stringResource(R.string.ok)) }
                    },
                    dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) } },
                    text = { TimePicker(state = pickerState) },
                )
            }
        }
        NodeFieldType.DATE -> {
            var showPicker by remember(field.id.value) { mutableStateOf(false) }
            val initialMillis = remember(rawValue) {
                runCatching { LocalDate.parse(rawValue).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
                    .getOrDefault(System.currentTimeMillis())
            }
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rawValue,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(text = field.id.value) },
                    singleLine = true,
                    isError = hasParseError,
                    supportingText = if (hasParseError) ({ Text(stringResource(R.string.canonical_field_invalid_value)) }) else null,
                )
                Button(onClick = { showPicker = true }) { Text(stringResource(R.string.canonical_choose_date)) }
            }
            if (showPicker) {
                DatePickerDialog(
                    onDismissRequest = { showPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            pickerState.selectedDateMillis?.let { millis ->
                                onValueChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                            }
                            showPicker = false
                        }) { Text(stringResource(R.string.ok)) }
                    },
                    dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) } },
                ) { DatePicker(state = pickerState) }
            }
        }
        NodeFieldType.TIMEZONE_ID -> {
            val zones = remember { java.util.TimeZone.getAvailableIDs().sorted() }
            var expanded by remember(field.id.value) { mutableStateOf(false) }
            Column {
                OutlinedTextField(
                    value = rawValue,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(field.id.value) },
                    singleLine = true,
                    isError = hasParseError,
                    supportingText = if (hasParseError) ({ Text(stringResource(R.string.canonical_field_invalid_value)) }) else null,
                    trailingIcon = {
                        TextButton(onClick = { expanded = true }) {
                            Text(stringResource(R.string.canonical_browse))
                        }
                    },
                )
                androidx.compose.material3.DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    zones.forEach { zone ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(zone) },
                            onClick = { onValueChange(zone); expanded = false },
                        )
                    }
                }
            }
        }
        NodeFieldType.COLLECTION -> {
            val hint = remember(field.collectionElementKind) {
                when (field.collectionElementKind) {
                    CanonicalValueKind.PACKAGE_ID -> "com.example.app | org.example.app"
                    CanonicalValueKind.URI -> "content://... | https://..."
                    CanonicalValueKind.INTEGER, CanonicalValueKind.DURATION_MS,
                    CanonicalValueKind.TIMESTAMP_MS -> "1 | 2 | 3"
                    else -> "value 1 | value 2"
                }
            }
            OutlinedTextField(
                value = rawValue,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(field.id.value) },
                placeholder = { Text(hint) },
                isError = hasParseError,
                supportingText = if (hasParseError) ({ Text(stringResource(R.string.canonical_field_invalid_value)) }) else null,
            )
        }
        NodeFieldType.PACKAGE_ID -> {
            val context = LocalContext.current
            var showPicker by remember(field.id.value) { mutableStateOf(false) }
            var query by remember(field.id.value) { mutableStateOf("") }
            val applications = remember(showPicker, context) {
                if (!showPicker) emptyList() else runCatching {
                    context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                        .map { info ->
                            info.packageName to context.packageManager.getApplicationLabel(info).toString()
                        }
                        .sortedWith(compareBy(Collator.getInstance()) { it.second })
                }.getOrDefault(emptyList())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rawValue,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(field.id.value) },
                    singleLine = true,
                    isError = hasParseError,
                    supportingText = if (hasParseError) ({ Text(stringResource(R.string.canonical_field_invalid_value)) }) else null,
                )
                Button(onClick = { showPicker = true }) {
                    Text(stringResource(R.string.canonical_choose_app))
                }
            }
            if (showPicker) {
                AlertDialog(
                    onDismissRequest = { showPicker = false },
                    confirmButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) } },
                    title = { Text(stringResource(R.string.canonical_choose_app)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.canonical_search_apps)) },
                                singleLine = true,
                            )
                            val filtered = applications.filter { (packageName, label) ->
                                query.isBlank() || label.contains(query, ignoreCase = true) ||
                                    packageName.contains(query, ignoreCase = true)
                            }
                            if (filtered.isEmpty()) {
                                Text(stringResource(R.string.canonical_no_apps_found))
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                                    items(filtered, key = { it.first }) { (packageName, label) ->
                                        TextButton(
                                            onClick = {
                                                onValueChange(packageName)
                                                showPicker = false
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(packageName, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
        NodeFieldType.URI -> {
            val context = LocalContext.current
            var permissionError by remember(field.id.value) { mutableStateOf(false) }
            val filePicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri: Uri? ->
                if (uri != null) {
                    val retained = runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }.isSuccess
                    permissionError = !retained
                    if (retained) onValueChange(uri.toString())
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rawValue,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(field.id.value) },
                    singleLine = true,
                    isError = hasParseError,
                    supportingText = when {
                        hasParseError -> ({ Text(stringResource(R.string.canonical_field_invalid_value)) })
                        permissionError -> ({ Text(stringResource(R.string.canonical_file_permission_error)) })
                        else -> null
                    },
                )
                Button(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.canonical_choose_file))
                }
            }
        }
        NodeFieldType.JSON -> {
            OutlinedTextField(
                value = rawValue,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(field.id.value) },
                minLines = 3,
                maxLines = 8,
                isError = hasParseError,
                supportingText = if (hasParseError) ({ Text(stringResource(R.string.canonical_field_invalid_value)) }) else null,
            )
        }
        NodeFieldType.COORDINATE -> {
            val coordinates = rawValue.split(",", limit = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = coordinates.getOrNull(0).orEmpty(),
                    onValueChange = { onValueChange("$it,${coordinates.getOrNull(1).orEmpty()}") },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.canonical_latitude)) },
                    isError = hasParseError,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = coordinates.getOrNull(1).orEmpty(),
                    onValueChange = { onValueChange("${coordinates.getOrNull(0).orEmpty()},$it") },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.canonical_longitude)) },
                    isError = hasParseError,
                    singleLine = true,
                )
            }
            if (hasParseError) Text(stringResource(R.string.canonical_field_invalid_value), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }
        else -> {
            OutlinedTextField(
                value = rawValue,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = field.id.value) },
                singleLine = field.type != NodeFieldType.TEXT,
                isError = hasParseError,
                supportingText = if (hasParseError) {
                    { Text(stringResource(R.string.canonical_field_invalid_value)) }
                } else if (field.minimum != null || field.maximum != null) {
                    {
                        Text(
                            text = listOfNotNull(field.minimum, field.maximum)
                                .joinToString("..")
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

internal fun parseCanonicalField(field: NodeSchemaField, raw: String): CanonicalValue? =
    runCatching {
        if (field.expressionCapable && EXPRESSION_MARKER.containsMatchIn(raw)) {
            return@runCatching ExpressionValue(raw, canonicalKindFor(field.type))
        }
        when (field.type) {
            NodeFieldType.BOOLEAN -> BooleanValue(raw.toBooleanStrict())
            NodeFieldType.INTEGER -> IntegerValue(raw.toLong())
            NodeFieldType.DECIMAL -> DecimalValue(raw)
            NodeFieldType.TEXT -> TextValue(raw)
            NodeFieldType.PERCENTAGE -> PercentageValue(raw)
            NodeFieldType.DURATION_MS -> DurationValue(raw.toLong())
            NodeFieldType.TIMESTAMP_MS -> com.nexaflow.domain.canonical.TimestampValue(raw.toLong())
            NodeFieldType.TIME_OF_DAY -> {
                val parts = raw.split(":")
                val minute = parts.getOrNull(0)!!.toInt() * 60 + parts.getOrNull(1)!!.toInt()
                TimeOfDayValue(minute)
            }
            NodeFieldType.DATE -> DateValue(raw)
            NodeFieldType.TIMEZONE_ID -> TimezoneValue(raw)
            NodeFieldType.PACKAGE_ID -> PackageIdValue(raw)
            NodeFieldType.URI -> UriValue(raw)
            NodeFieldType.COORDINATE -> {
                val parts = raw.split(",", limit = 2)
                require(parts.size == 2) { "coordinate must use latitude,longitude" }
                CoordinateValue(parts[0].trim().toDouble(), parts[1].trim().toDouble())
            }
            NodeFieldType.ENUM_TOKEN -> EnumTokenValue(
                requireNotNull(field.enumType),
                raw,
            )
            NodeFieldType.JSON -> JsonValue(Json.parseToJsonElement(raw))
            NodeFieldType.COLLECTION -> {
                val elementKind = requireNotNull(field.collectionElementKind) {
                    "collection field requires collectionElementKind"
                }
                val items = raw.split('|', ';', ',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .map { token -> parseCollectionElement(elementKind, token) }
                CollectionValue(elementKind, items)
            }
            NodeFieldType.SECRET_REFERENCE -> null
        }
    }.getOrNull()

internal fun canonicalFieldParseErrors(
    schema: com.nexaflow.domain.canonical.NodeSchema,
    legacyKeys: Map<String, String>,
    config: Map<String, String>,
): Set<String> = schema.fields.mapNotNullTo(linkedSetOf()) { field ->
    if (field.type == NodeFieldType.SECRET_REFERENCE) return@mapNotNullTo null
    val legacyKey = legacyKeys[field.id.value] ?: field.id.value
    val raw = config[legacyKey] ?: field.default?.raw?.let(::canonicalValueToLegacy) ?: return@mapNotNullTo null
    if (raw.isBlank() && !field.alwaysRequired && field.requiredWhen.isEmpty()) return@mapNotNullTo null
    if (parseCanonicalField(field, raw) == null) field.id.value else null
}

internal fun canonicalValueToLegacy(value: CanonicalValue): String = when (value) {
    is BooleanValue -> value.value.toString()
    is IntegerValue -> value.value.toString()
    is DecimalValue -> value.value
    is TextValue -> value.value
    is PercentageValue -> value.value
    is DurationValue -> value.milliseconds.toString()
    is com.nexaflow.domain.canonical.TimestampValue -> value.epochMilliseconds.toString()
    is TimeOfDayValue -> "%02d:%02d".format(value.minuteOfDay / 60, value.minuteOfDay % 60)
    is DateValue -> value.isoDate
    is TimezoneValue -> value.zoneId
    is PackageIdValue -> value.packageName
    is UriValue -> value.value
    is CoordinateValue -> "${value.latitude},${value.longitude}"
    is EnumTokenValue -> value.token
    is JsonValue -> value.value.toString()
    is CollectionValue -> value.values.joinToString("|", transform = ::canonicalValueToLegacy)
    is com.nexaflow.domain.canonical.SecretReferenceValue -> ""
    is com.nexaflow.domain.canonical.ExpressionValue -> value.source
}

private fun canonicalKindFor(type: NodeFieldType): CanonicalValueKind = when (type) {
    NodeFieldType.BOOLEAN -> CanonicalValueKind.BOOLEAN
    NodeFieldType.INTEGER -> CanonicalValueKind.INTEGER
    NodeFieldType.DECIMAL -> CanonicalValueKind.DECIMAL
    NodeFieldType.TEXT -> CanonicalValueKind.TEXT
    NodeFieldType.PERCENTAGE -> CanonicalValueKind.PERCENTAGE
    NodeFieldType.DURATION_MS -> CanonicalValueKind.DURATION_MS
    NodeFieldType.TIMESTAMP_MS -> CanonicalValueKind.TIMESTAMP_MS
    NodeFieldType.TIME_OF_DAY -> CanonicalValueKind.TIME_OF_DAY
    NodeFieldType.DATE -> CanonicalValueKind.DATE
    NodeFieldType.TIMEZONE_ID -> CanonicalValueKind.TIMEZONE_ID
    NodeFieldType.PACKAGE_ID -> CanonicalValueKind.PACKAGE_ID
    NodeFieldType.URI -> CanonicalValueKind.URI
    NodeFieldType.COORDINATE -> CanonicalValueKind.COORDINATE
    NodeFieldType.ENUM_TOKEN -> CanonicalValueKind.ENUM_TOKEN
    NodeFieldType.JSON -> CanonicalValueKind.JSON
    NodeFieldType.COLLECTION -> CanonicalValueKind.COLLECTION
    NodeFieldType.SECRET_REFERENCE -> CanonicalValueKind.SECRET_REFERENCE
}

private val EXPRESSION_MARKER = Regex("%(?:CTX\\.|[A-Za-z_])")

private fun parseCollectionElement(
    kind: CanonicalValueKind,
    raw: String,
): CanonicalValue = when (kind) {
    CanonicalValueKind.BOOLEAN -> BooleanValue(raw.toBooleanStrict())
    CanonicalValueKind.INTEGER -> IntegerValue(raw.toLong())
    CanonicalValueKind.DECIMAL -> DecimalValue(raw)
    CanonicalValueKind.TEXT -> TextValue(raw)
    CanonicalValueKind.PERCENTAGE -> PercentageValue(raw)
    CanonicalValueKind.DURATION_MS -> DurationValue(raw.toLong())
    CanonicalValueKind.TIMESTAMP_MS ->
        com.nexaflow.domain.canonical.TimestampValue(raw.toLong())
    CanonicalValueKind.TIME_OF_DAY -> {
        val parts = raw.split(":")
        require(parts.size == 2) { "time must use HH:mm" }
        TimeOfDayValue(parts[0].toInt() * 60 + parts[1].toInt())
    }
    CanonicalValueKind.DATE -> DateValue(raw)
    CanonicalValueKind.TIMEZONE_ID -> TimezoneValue(raw)
    CanonicalValueKind.PACKAGE_ID -> PackageIdValue(raw)
    CanonicalValueKind.URI -> UriValue(raw)
    CanonicalValueKind.COORDINATE -> {
        val parts = raw.split(",", limit = 2)
        require(parts.size == 2) { "coordinate must use latitude,longitude" }
        CoordinateValue(parts[0].trim().toDouble(), parts[1].trim().toDouble())
    }
    CanonicalValueKind.JSON -> JsonValue(Json.parseToJsonElement(raw))
    CanonicalValueKind.ENUM_TOKEN,
    CanonicalValueKind.SECRET_REFERENCE,
    CanonicalValueKind.COLLECTION,
    CanonicalValueKind.EXPRESSION ->
        throw IllegalArgumentException("unsupported collection element kind $kind")
}
