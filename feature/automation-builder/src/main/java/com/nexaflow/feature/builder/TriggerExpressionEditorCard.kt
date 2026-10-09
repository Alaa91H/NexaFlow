package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.workflow.TemporalEventSourcePolicy
import com.nexaflow.domain.workflow.TriggerExpressionDefinitionV2
import com.nexaflow.domain.workflow.TriggerExpressionNodeV2
import com.nexaflow.domain.workflow.TriggerExpressionValidator

private enum class EditorKind { ALL, ANY, NOT_STATE, SEQUENCE, COUNT }

@Composable
internal fun TriggerExpressionEditorCard(
    triggers: List<Trigger>,
    legacyMatch: TriggerMatchMode,
    definition: TriggerExpressionDefinitionV2?,
    onDefinitionChange: (TriggerExpressionDefinitionV2?) -> Unit
) {
    var kind by remember(definition, legacyMatch) { mutableStateOf(kindFor(definition, legacyMatch)) }
    var first by remember(definition, triggers) { mutableIntStateOf((definition?.root as? TriggerExpressionNodeV2.Sequence)?.firstIndex ?: 0) }
    var second by remember(definition, triggers) { mutableIntStateOf((definition?.root as? TriggerExpressionNodeV2.Sequence)?.secondIndex ?: 1) }
    var countIndex by remember(definition, triggers) { mutableIntStateOf((definition?.root as? TriggerExpressionNodeV2.Count)?.index ?: 0) }
    var minimum by remember(definition) { mutableStateOf(((definition?.root as? TriggerExpressionNodeV2.Count)?.minimumCount ?: 2).toString()) }
    var seconds by remember(definition) {
        mutableStateOf((((definition?.root as? TriggerExpressionNodeV2.Sequence)?.withinMs
            ?: (definition?.root as? TriggerExpressionNodeV2.Count)?.withinMs ?: 60_000L) / 1000L).toString())
    }
    var nested by remember(definition) {
        val children = when (val root = definition?.root) {
            is TriggerExpressionNodeV2.AllOf -> root.children
            is TriggerExpressionNodeV2.AnyOf -> root.children
            else -> emptyList()
        }
        mutableStateOf(children.any { it is TriggerExpressionNodeV2.AllOf || it is TriggerExpressionNodeV2.AnyOf })
    }
    var nestedAll by remember(definition) {
        val children = when (val root = definition?.root) {
            is TriggerExpressionNodeV2.AllOf -> root.children
            is TriggerExpressionNodeV2.AnyOf -> root.children
            else -> emptyList()
        }
        mutableStateOf(children.any { it is TriggerExpressionNodeV2.AllOf })
    }
    var preview by remember { mutableStateOf(false) }

    NexaFlowCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.trigger_expression_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.trigger_expression_description), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = definition != null, enabled = triggers.isNotEmpty(), onCheckedChange = {
                    onDefinitionChange(if (it) createDefault(triggers, legacyMatch) else null)
                    preview = false
                })
            }
            if (definition != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EditorKind.entries.forEach { option ->
                        TextButton(onClick = {
                            kind = option
                            onDefinitionChange(build(option, triggers, first, second, countIndex, minimum, seconds, nested, nestedAll))
                            preview = false
                        }) { Text(kindText(option), color = if (kind == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                    }
                }
                when (kind) {
                    EditorKind.NOT_STATE -> IndexPicker(
                        stringResource(R.string.trigger_expression_trigger),
                        triggers.indices.filter { TriggerExpressionValidator.isStateReadable(triggers[it]) }, first, triggers
                    ) { first = it; onDefinitionChange(build(kind, triggers, it, second, countIndex, minimum, seconds, nested, nestedAll)) }
                    EditorKind.SEQUENCE -> {
                        val allowed = triggers.indices.filter { TemporalEventSourcePolicy.supportsStableIdentity(triggers[it]) }
                        IndexPicker(stringResource(R.string.trigger_expression_trigger), allowed, first, triggers) {
                            first = it; onDefinitionChange(build(kind, triggers, it, second, countIndex, minimum, seconds, nested, nestedAll))
                        }
                        IndexPicker(stringResource(R.string.trigger_expression_second_trigger), allowed, second, triggers) {
                            second = it; onDefinitionChange(build(kind, triggers, first, it, countIndex, minimum, seconds, nested, nestedAll))
                        }
                        WindowField(seconds) { seconds = it; onDefinitionChange(build(kind, triggers, first, second, countIndex, minimum, it, nested, nestedAll)) }
                    }
                    EditorKind.COUNT -> {
                        val allowed = triggers.indices.filter { TemporalEventSourcePolicy.supportsStableIdentity(triggers[it]) }
                        IndexPicker(stringResource(R.string.trigger_expression_trigger), allowed, countIndex, triggers) {
                            countIndex = it; onDefinitionChange(build(kind, triggers, first, second, it, minimum, seconds, nested, nestedAll))
                        }
                        OutlinedTextField(minimum, { minimum = it.filter(Char::isDigit).take(4); onDefinitionChange(build(kind, triggers, first, second, countIndex, minimum, seconds, nested, nestedAll)) },
                            label = { Text(stringResource(R.string.trigger_expression_minimum_count)) }, modifier = Modifier.fillMaxWidth())
                        WindowField(seconds) { seconds = it; onDefinitionChange(build(kind, triggers, first, second, countIndex, minimum, it, nested, nestedAll)) }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.trigger_expression_add_nested), modifier = Modifier.weight(1f))
                            Switch(checked = nested, onCheckedChange = { nested = it; onDefinitionChange(build(kind, triggers, first, second, countIndex, minimum, seconds, it, nestedAll)) })
                        }
                        if (nested) TextButton(onClick = {
                            nestedAll = !nestedAll
                            onDefinitionChange(build(kind, triggers, first, second, countIndex, minimum, seconds, nested, nestedAll))
                        }) { Text(if (nestedAll) stringResource(R.string.trigger_expression_all) else stringResource(R.string.trigger_expression_any)) }
                    }
                }
                val issues = TriggerExpressionValidator.validate(definition, triggers)
                if (issues.isNotEmpty()) Text(stringResource(R.string.trigger_expression_invalid), color = MaterialTheme.colorScheme.error)
                else {
                    Button(onClick = { preview = !preview }) { Text(stringResource(R.string.trigger_expression_preview)) }
                    if (preview) TriggerExpressionPreview(definition, triggers)
                }
            }
        }
    }
}

@Composable
private fun IndexPicker(title: String, indices: List<Int>, selected: Int, triggers: List<Trigger>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = { expanded = true }, enabled = indices.isNotEmpty()) {
            Text(triggers.getOrNull(selected)?.type?.let { stringResource(it.labelRes()) }
                ?: stringResource(R.string.trigger_expression_unsupported_source))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            indices.forEach { index -> DropdownMenuItem(
                text = { Text(stringResource(triggers[index].type.labelRes())) },
                onClick = { expanded = false; onSelect(index) }
            ) }
        }
    }
}

@Composable
private fun WindowField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter(Char::isDigit).take(8)) },
        label = { Text(stringResource(R.string.trigger_expression_window_seconds)) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun kindText(kind: EditorKind): String = when (kind) {
    EditorKind.ALL -> stringResource(R.string.trigger_expression_all)
    EditorKind.ANY -> stringResource(R.string.trigger_expression_any)
    EditorKind.NOT_STATE -> stringResource(R.string.trigger_expression_not_state)
    EditorKind.SEQUENCE -> stringResource(R.string.trigger_expression_sequence)
    EditorKind.COUNT -> stringResource(R.string.trigger_expression_count)
}

private fun kindFor(definition: TriggerExpressionDefinitionV2?, match: TriggerMatchMode) = when (definition?.root) {
    is TriggerExpressionNodeV2.AnyOf -> EditorKind.ANY
    is TriggerExpressionNodeV2.NotState -> EditorKind.NOT_STATE
    is TriggerExpressionNodeV2.Sequence -> EditorKind.SEQUENCE
    is TriggerExpressionNodeV2.Count -> EditorKind.COUNT
    is TriggerExpressionNodeV2.AllOf -> EditorKind.ALL
    else -> if (match == TriggerMatchMode.ALL) EditorKind.ALL else EditorKind.ANY
}

private fun createDefault(triggers: List<Trigger>, match: TriggerMatchMode): TriggerExpressionDefinitionV2 {
    val leaves = triggers.mapIndexed(::leaf)
    val root = when {
        leaves.size == 1 -> leaves.single()
        match == TriggerMatchMode.ALL -> TriggerExpressionNodeV2.AllOf(leaves)
        else -> TriggerExpressionNodeV2.AnyOf(leaves)
    }
    return TriggerExpressionDefinitionV2(root = root)
}

private fun build(kind: EditorKind, triggers: List<Trigger>, first: Int, second: Int, countIndex: Int,
                  minimum: String, seconds: String, nested: Boolean, nestedAll: Boolean): TriggerExpressionDefinitionV2 {
    val windowMs = (seconds.toLongOrNull() ?: 0L).coerceAtMost(604_800L) * 1000L
    val root = when (kind) {
        EditorKind.NOT_STATE -> TriggerExpressionNodeV2.NotState(first)
        EditorKind.SEQUENCE -> TriggerExpressionNodeV2.Sequence(first, second, windowMs)
        EditorKind.COUNT -> TriggerExpressionNodeV2.Count(countIndex, minimum.toIntOrNull() ?: 0, windowMs)
        EditorKind.ALL, EditorKind.ANY -> {
            val leaves = triggers.mapIndexed(::leaf)
            val children = if (nested && leaves.size >= 3) listOf(
                leaves.first(),
                if (nestedAll) TriggerExpressionNodeV2.AllOf(leaves.drop(1)) else TriggerExpressionNodeV2.AnyOf(leaves.drop(1))
            ) else leaves
            if (kind == EditorKind.ALL) TriggerExpressionNodeV2.AllOf(children) else TriggerExpressionNodeV2.AnyOf(children)
        }
    }
    return TriggerExpressionDefinitionV2(root = root)
}

private fun leaf(index: Int, trigger: Trigger): TriggerExpressionNodeV2 =
    if (TriggerExpressionValidator.isStateReadable(trigger)) TriggerExpressionNodeV2.State(index)
    else TriggerExpressionNodeV2.Event(index)
