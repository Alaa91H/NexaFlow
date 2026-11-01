package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexaflow.core.engine.CanonicalBatteryTriggerDefinition
import com.nexaflow.core.ui.CategoryChip
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.domain.canonical.CanonicalWorkflowNode
import com.nexaflow.domain.canonical.EnumTokenValue
import com.nexaflow.domain.canonical.IntegerValue

/**
 * Battery threshold trigger, rendered **below** the trigger catalogue so the
 * picker starts at its search field instead of being pushed down by a single
 * special-case trigger.
 *
 * Each argument is an explicit choice. The previous version cycled the value on
 * every tap of a text button ("Charger: ANY" → AC → USB → WIRELESS → ANY), so
 * the available options were invisible and a wrong tap meant tapping round the
 * whole ring again. Now every option is on screen and picking one is a single
 * tap; the stored tokens (`ANY`/`AC`/`USB`/`WIRELESS`, `ANY`/`CHARGING`/
 * `NOT_CHARGING`, `BELOW`/`ABOVE`) are unchanged, so saved tasks and the engine
 * are untouched.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BatteryThresholdSection(
    nodes: SnapshotStateList<CanonicalWorkflowNode>,
    addEnabled: Boolean,
    onAdd: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.canonical_battery_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.canonical_battery_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(enabled = addEnabled, onClick = onAdd) {
                Text(stringResource(R.string.canonical_add_battery))
            }
        }

        nodes.forEachIndexed { index, node ->
            val threshold = node.argument(CanonicalBatteryTriggerDefinition.thresholdField)
                ?.let { (it as? IntegerValue)?.value?.toInt() } ?: 20
            val direction = node.token(CanonicalBatteryTriggerDefinition.directionField) ?: "BELOW"
            val charger = node.token(CanonicalBatteryTriggerDefinition.chargerField) ?: "ANY"
            val chargingState = node.token(CanonicalBatteryTriggerDefinition.chargingField) ?: "ANY"

            fun write(
                thresholdPercent: Int = threshold,
                directionToken: String = direction,
                chargerToken: String = charger,
                chargingToken: String = chargingState
            ) {
                nodes[index] = CanonicalBatteryTriggerDefinition.node(
                    id = node.node.id.value,
                    sequenceIndex = index,
                    thresholdPercent = thresholdPercent,
                    direction = directionToken,
                    chargerType = chargerToken,
                    chargingState = chargingToken
                )
            }

            NexaFlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.canonical_battery_threshold) + ": $threshold%",
                            style = MaterialTheme.typography.titleSmall
                        )
                        TextButton(onClick = { nodes.removeAt(index) }) {
                            Text(stringResource(R.string.remove))
                        }
                    }
                    Slider(
                        value = threshold.toFloat(),
                        onValueChange = { raw -> write(thresholdPercent = raw.toInt()) },
                        valueRange = 0f..100f,
                        steps = 99
                    )
                    OptionRow(
                        label = stringResource(R.string.battery_direction_label),
                        options = listOf(
                            "BELOW" to stringResource(R.string.constraint_below),
                            "ABOVE" to stringResource(R.string.constraint_above)
                        ),
                        selected = direction,
                        onSelect = { write(directionToken = it) }
                    )
                    OptionRow(
                        label = stringResource(R.string.charger_type_label),
                        options = listOf(
                            "ANY" to stringResource(R.string.charger_any),
                            "AC" to stringResource(R.string.charger_ac),
                            "USB" to stringResource(R.string.charger_usb),
                            "WIRELESS" to stringResource(R.string.charger_wireless)
                        ),
                        selected = charger,
                        onSelect = { write(chargerToken = it) }
                    )
                    OptionRow(
                        label = stringResource(R.string.charging_state_section),
                        options = listOf(
                            "ANY" to stringResource(R.string.charging_any),
                            "CHARGING" to stringResource(R.string.charging_yes),
                            "NOT_CHARGING" to stringResource(R.string.charging_no)
                        ),
                        selected = chargingState,
                        onSelect = { write(chargingToken = it) }
                    )
                }
            }
        }
    }
}

/** One labelled choice: a wrapping chip row where exactly one chip is selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (token, text) ->
                CategoryChip(
                    selected = selected == token,
                    onClick = { onSelect(token) },
                    label = text
                )
            }
        }
    }
}

private fun CanonicalWorkflowNode.argument(field: com.nexaflow.domain.canonical.CanonicalFieldId) =
    arguments.firstOrNull { it.field == field }?.value

private fun CanonicalWorkflowNode.token(field: com.nexaflow.domain.canonical.CanonicalFieldId): String? =
    (argument(field) as? EnumTokenValue)?.token
