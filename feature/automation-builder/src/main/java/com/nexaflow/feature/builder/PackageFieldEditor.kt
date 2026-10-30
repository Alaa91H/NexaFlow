package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * The canonical `package` field with the installed-app picker.
 *
 * Launch, close, and Play Store-listing actions all execute the same package
 * contract, so they edit it with one composable instead of three copies that
 * could drift apart.
 */
@Composable
internal fun PackageFieldEditor(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
    onPickApp: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = config["package"] ?: "",
            onValueChange = { onConfigChange(mapOf("package" to it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.package_name)) },
            singleLine = true
        )
        TextButton(onClick = onPickApp) {
            Text(text = stringResource(R.string.choose_from_installed))
        }
    }
}
