package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType

/**
 * A numeric field whose value is bounded to [min]..[max]. Instead of silently
 * truncating an out-of-range entry, the field shows the clamped value inline
 * ("clamped to 0–23") while the out-of-range text is still on screen, so the
 * user sees exactly what the engine will store. The stored config always
 * receives the clamped value, never the raw input.
 */
@Composable
fun BoundedNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    min: Long,
    max: Long,
    label: String,
    modifier: Modifier = Modifier,
    unitHint: String? = null
) {
    var clampedNotice by remember { mutableStateOf<String?>(null) }
    val rangeNotice = stringResource(R.string.clamp_range, if (min == max) "$min" else "$min–$max")
    val rangeUnitNotice = stringResource(
        R.string.clamp_range_unit,
        if (min == max) "$min" else "$min–$max",
        unitHint ?: ""
    )
    val notice = if (unitHint.isNullOrBlank()) rangeNotice else rangeUnitNotice
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(max.toString().length + 1)
            val parsed = digits.toLongOrNull()
            if (parsed == null) {
                clampedNotice = null
                onValueChange("")
                return@OutlinedTextField
            }
            when {
                parsed < min -> {
                    clampedNotice = notice
                    onValueChange(min.toString())
                }
                parsed > max -> {
                    clampedNotice = notice
                    onValueChange(max.toString())
                }
                else -> {
                    clampedNotice = null
                    onValueChange(parsed.toString())
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        supportingText = {
            clampedNotice?.let { notice ->
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        isError = clampedNotice != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}


