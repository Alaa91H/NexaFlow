package com.nexaflow.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

/**
 * The app's search field.
 *
 * A fully rounded field rather than the Material 3 outlined rectangle, so
 * it reads as a distinct, softer control against the card-shaped surfaces
 * around it.
 */
@Composable
fun NexaFlowSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    onClear: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = {
            Text(
                text = placeholder ?: stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = if (query.isNotEmpty() && onClear != null) {
            {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.clear),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(percent = 50),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Preview(name = "NexaFlowSearchField", showBackground = true)
@Composable
private fun NexaFlowSearchFieldPreview() {
    MaterialTheme {
        NexaFlowSearchField(
            query = "",
            onQueryChange = {}
        )
    }
}
