package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
internal fun WaitDurationEditor(
    durationSeconds: Long,
    onDurationChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val parts = WaitDuration.fromSeconds(durationSeconds)
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DurationCounter(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.wait_hours),
                value = parts.hours,
                maxValue = 24,
                onValueChange = { onDurationChange(WaitDuration.toSeconds(it, parts.minutes, parts.seconds)) }
            )
            DurationCounter(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.wait_minutes),
                value = parts.minutes,
                maxValue = 59,
                onValueChange = { onDurationChange(WaitDuration.toSeconds(parts.hours, it, parts.seconds)) }
            )
            DurationCounter(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.wait_seconds),
                value = parts.seconds,
                maxValue = 59,
                onValueChange = { onDurationChange(WaitDuration.toSeconds(parts.hours, parts.minutes, it)) }
            )
        }
    }
}

@Composable
private fun DurationCounter(
    label: String,
    value: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable(label) { mutableStateOf(value.toString()) }
    var focused by rememberSaveable(label) { mutableStateOf(false) }
    LaunchedEffect(value, focused) {
        if (!focused) text = value.toString()
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            enabled = value < maxValue,
            onClick = { onValueChange((value + 1).coerceAtMost(maxValue)) }
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.wait_increase, label),
                tint = if (value < maxValue) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                val digits = input.filter(Char::isDigit).take(2)
                text = digits
                digits.toIntOrNull()?.let { onValueChange(it.coerceIn(0, maxValue)) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (!focused && state.isFocused) text = ""
                    if (focused && !state.isFocused) {
                        val committed = text.toIntOrNull()?.coerceIn(0, maxValue) ?: value
                        onValueChange(committed)
                        text = committed.toString()
                    }
                    focused = state.isFocused
                },
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.titleMedium
        )
        IconButton(
            enabled = value > 0,
            onClick = { onValueChange((value - 1).coerceAtLeast(0)) }
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.wait_decrease, label),
                tint = if (value > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            )
        }
    }
}
