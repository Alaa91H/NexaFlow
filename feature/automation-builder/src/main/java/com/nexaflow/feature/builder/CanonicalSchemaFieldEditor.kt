package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
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
    val values = remember(schema.schemaId, config) {
        schema.fields.mapNotNull { field ->
            val legacyKey = binding.legacyKeys[field.id.value] ?: field.id.value
            val raw = config[legacyKey] ?: field.default?.raw?.let(::canonicalValueToLegacy)
            raw?.let { parseCanonicalField(field, it) }?.let { NodeFieldValue(field.id, it) }
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
            )
        }
        else -> {
            OutlinedTextField(
                value = rawValue,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = field.id.value) },
                singleLine = field.type != NodeFieldType.TEXT,
                supportingText = if (field.minimum != null || field.maximum != null) {
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

private fun parseCanonicalField(field: NodeSchemaField, raw: String): CanonicalValue? =
    runCatching {
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

private fun canonicalValueToLegacy(value: CanonicalValue): String = when (value) {
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
