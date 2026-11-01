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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.math.BigDecimal
import java.math.RoundingMode
import com.nexaflow.core.ui.CategoryChip
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
import com.nexaflow.domain.canonical.SecretReferenceValue
import com.nexaflow.domain.canonical.TimeOfDayValue
import com.nexaflow.domain.canonical.TimezoneValue
import com.nexaflow.domain.canonical.UriValue
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch

/**
 * Where `SECRET_REFERENCE` values are stored. The builder screen provides the
 * vault-backed implementation; the default no-op keeps this renderer free of
 * Hilt, so every other control can be composed in JVM tests.
 */
internal val LocalWorkflowSecretStore = staticCompositionLocalOf<suspend (String, String) -> Unit> {
    { _, _ -> }
}

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
    /** Enum tokens that a dedicated trigger already owns, hidden from the chips. */
    hiddenTokens: Set<String> = emptySet(),
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
        val visibleFields = state.visibleFields()
        // A single enum choice is self-describing ("Connected" / "Disconnected");
        // printing its storage key above the chips only adds noise.
        val showLabels = visibleFields.size > 1
        visibleFields.forEach { field ->
            val legacyKey = binding.legacyKeys[field.id.value] ?: field.id.value
            val current = config[legacyKey]
                ?: field.default?.raw?.let(::canonicalValueToLegacy)
                .orEmpty()
            CanonicalFieldControl(
                field = field,
                schemaTarget = schema.target.value,
                rawValue = current,
                showLabel = showLabels,
                hasParseError = field.id.value in parseErrors,
                hiddenTokens = hiddenTokens,
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
            NodeFieldType.SECRET_REFERENCE -> SecretReferenceValue(raw)
        }
    }.getOrNull()

internal fun canonicalFieldParseErrors(
    schema: com.nexaflow.domain.canonical.NodeSchema,
    legacyKeys: Map<String, String>,
    config: Map<String, String>,
): Set<String> = schema.fields.mapNotNullTo(linkedSetOf()) { field ->
    val legacyKey = legacyKeys[field.id.value] ?: field.id.value
    val raw = config[legacyKey] ?: field.default?.raw?.let(::canonicalValueToLegacy) ?: return@mapNotNullTo null
    if (raw.isBlank() && !field.alwaysRequired && field.requiredWhen.isEmpty()) return@mapNotNullTo null
    if (parseCanonicalField(field, raw) == null) field.id.value else null
}

internal fun durationInputToMillis(input: String, unitMillis: Long): Long? = runCatching {
    require(unitMillis > 0L)
    BigDecimal(input).multiply(BigDecimal.valueOf(unitMillis)).longValueExact()
}.getOrNull()?.takeIf { it >= 0L }

internal fun durationDisplayValue(rawMillis: String, unitMillis: Long): String = runCatching {
    require(unitMillis > 0L)
    BigDecimal(rawMillis)
        .divide(BigDecimal.valueOf(unitMillis), 3, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}.getOrDefault(rawMillis)

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
    is com.nexaflow.domain.canonical.SecretReferenceValue -> value.referenceId
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
