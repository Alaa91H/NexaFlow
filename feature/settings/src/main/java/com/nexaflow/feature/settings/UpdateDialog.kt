package com.nexaflow.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * In-app update dialog (Google-style): shows the new version, release notes
 * and APK size, with a primary "Download & install" action that runs
 * immediately when tapped. While the APK is downloading the button turns into
 * a progress indicator and stays disabled.
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    downloading: Boolean,
    onDownloadAndInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.update_dialog_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        R.string.update_available_sub,
                        info.version.removePrefix("v")
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                val notes = info.notes
                if (!notes.isNullOrBlank()) {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(max = 200.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                }
                val size = info.apkSizeBytes
                if (size != null) {
                    Text(
                        text = stringResource(R.string.update_dialog_size, formatBytes(size)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start
                    )
                }
            }
        },
        confirmButton = {
            if (downloading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.update_downloading),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                Button(onClick = onDownloadAndInstall) {
                    Text(stringResource(R.string.update_dialog_download))
                }
            }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_dialog_later))
                }
            }
        }
    )
}

/** Formats a byte count as a short human-readable size (e.g. "3.7 MB"). */
internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val decimals = if (unit == 0) 0 else 1
    return String.format(Locale.US, "%.${decimals}f %s", value, units[unit])
}
