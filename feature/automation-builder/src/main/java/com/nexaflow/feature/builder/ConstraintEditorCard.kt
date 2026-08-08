package com.nexaflow.feature.builder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.domain.models.ConstraintType

/**
 * Editable draft of a constraint inside the builder. Immutable by contract
 * (edits produce new instances via `copy()`, the config map is never mutated)
 * so `ConstraintEditorCard` can skip recomposition when the draft is unchanged.
 */
@Immutable
data class ConstraintDraft private constructor(
    val type: ConstraintType,
    val config: Map<String, String>
) {
    companion object {
        /**
         * Freezes the config on construction so the @Immutable contract is
         * enforceable, not just documented: a mutable Gson LinkedTreeMap from
         * a loaded automation can never be mutated in place afterwards.
         */
        operator fun invoke(
            type: ConstraintType,
            config: Map<String, String> = emptyMap()
        ): ConstraintDraft = ConstraintDraft(type, config.toMap())
    }
}

internal val constraintTypeOptions = listOf(
    ConstraintType.WIFI,
    ConstraintType.BATTERY,
    ConstraintType.SCREEN_LOCKED,
    ConstraintType.HEADSET
)

/** Sensible default config for a freshly added constraint. */
internal fun defaultConstraintConfig(type: ConstraintType): Map<String, String> = when (type) {
    ConstraintType.BATTERY -> mapOf("direction" to "BELOW", "level" to "20")
    else -> emptyMap()
}

internal fun ConstraintType.labelRes(): Int = when (this) {
    ConstraintType.WIFI -> R.string.constraint_type_wifi
    ConstraintType.BATTERY -> R.string.constraint_type_battery
    ConstraintType.SCREEN_LOCKED -> R.string.constraint_type_screen_locked
    ConstraintType.HEADSET -> R.string.constraint_type_headset
}

internal fun ConstraintType.subtitleRes(): Int = when (this) {
    ConstraintType.WIFI -> R.string.constraint_type_wifi_sub
    ConstraintType.BATTERY -> R.string.constraint_type_battery_sub
    ConstraintType.SCREEN_LOCKED -> R.string.constraint_type_screen_locked_sub
    ConstraintType.HEADSET -> R.string.constraint_type_headset_sub
}

internal fun ConstraintType.icon(): ImageVector = when (this) {
    ConstraintType.WIFI -> Icons.Filled.Wifi
    ConstraintType.BATTERY -> Icons.Filled.BatteryChargingFull
    ConstraintType.SCREEN_LOCKED -> Icons.Filled.Lock
    ConstraintType.HEADSET -> Icons.Filled.Headphones
}

/** Lets the user choose which constraint to add. */
@Composable
fun ConstraintTypePickerDialog(
    onPick: (ConstraintType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.add_constraint)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(count = constraintTypeOptions.size, key = { constraintTypeOptions[it].name }) { index ->
                    val type = constraintTypeOptions[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(type) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconBadge(
                            icon = type.icon(),
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(type.labelRes()),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(type.subtitleRes()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * One constraint card: type chips (switching type), a type-specific config
 * editor (battery direction + level), and a remove button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConstraintEditorCard(
    draft: ConstraintDraft,
    index: Int,
    onConfigChange: (ConstraintDraft) -> Unit,
    onRemove: () -> Unit
) {
    NexaFlowCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.constraint_n, index + 1),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.constraint_remove),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                constraintTypeOptions.forEach { option ->
                    SelectChip(
                        selected = draft.type == option,
                        onClick = {
                            val defaults = defaultConstraintConfig(option)
                            onConfigChange(
                                ConstraintDraft(
                                    type = option,
                                    config = defaults + draft.config.filterKeys { it in defaults.keys }
                                )
                            )
                        },
                        label = stringResource(option.labelRes()),
                        leadingIcon = option.icon()
                    )
                }
            }
            when (draft.type) {
                ConstraintType.BATTERY -> {
                    val direction = draft.config["direction"] ?: "BELOW"
                    val level = (draft.config["level"] ?: "20").toIntOrNull() ?: 20
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectChip(
                            selected = direction == "ABOVE",
                            onClick = { onConfigChange(draft.copy(config = draft.config + ("direction" to "ABOVE"))) },
                            label = stringResource(R.string.constraint_above)
                        )
                        SelectChip(
                            selected = direction == "BELOW",
                            onClick = { onConfigChange(draft.copy(config = draft.config + ("direction" to "BELOW"))) },
                            label = stringResource(R.string.constraint_below)
                        )
                    }
                    SliderRow(
                        label = if (direction == "ABOVE") {
                            stringResource(R.string.minimum_battery, level)
                        } else {
                            stringResource(R.string.maximum_battery, level)
                        },
                        value = level.toFloat(),
                        onValueChange = { value ->
                            onConfigChange(
                                draft.copy(config = draft.config + ("level" to value.toInt().toString()))
                            )
                        },
                        valueRange = 5f..100f
                    )
                }
                ConstraintType.WIFI,
                ConstraintType.SCREEN_LOCKED,
                ConstraintType.HEADSET -> {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = stringResource(draft.type.subtitleRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
