package com.nexaflow.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.SettingRow

/**
 * The About dialog: build identity, license, and every project surface a user
 * can reach (source repository, announcement channel, community group, the
 * developer's own contacts and optional support).
 *
 * The rows are split from `SettingsScreen` so the developer/channel facts live
 * in one place; the visible order is developer → repository → channel →
 * community → support, so a link always states where it leads before it opens.
 */
@Composable
internal fun AboutDialog(
    visible: Boolean,
    appVersion: String,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        // The settings module cannot reference the app module's resources, so
        // the brand name stays a literal (it is not translated anyway).
        title = { Text("NexaFlow") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.version, appVersion))
                Text(
                    text = stringResource(R.string.about_description),
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = stringResource(R.string.about_license),
                    modifier = Modifier.padding(top = 8.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SettingRow(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.about_developer),
                    subtitle = DEVELOPER_HANDLE
                )
                SettingRow(
                    icon = Icons.Filled.Code,
                    title = stringResource(R.string.about_github),
                    subtitle = stringResource(R.string.about_github_sub),
                    onClick = { openUrl(context, DEV_GITHUB_URL) }
                )
                SettingRow(
                    icon = Icons.Filled.Email,
                    title = stringResource(R.string.about_email),
                    subtitle = stringResource(R.string.about_email_sub),
                    onClick = { openEmail(context, DEV_EMAIL) }
                )
                SettingRow(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = stringResource(R.string.about_telegram),
                    subtitle = stringResource(R.string.about_telegram_sub),
                    onClick = { openUrl(context, DEV_TELEGRAM_URL) }
                )
                SettingRow(
                    icon = Icons.Filled.Code,
                    title = stringResource(R.string.about_repository),
                    subtitle = stringResource(R.string.about_repository_sub),
                    onClick = { openUrl(context, PROJECT_REPOSITORY_URL) }
                )
                SettingRow(
                    icon = Icons.Filled.Campaign,
                    title = stringResource(R.string.about_channel),
                    subtitle = stringResource(R.string.about_channel_sub),
                    onClick = { openUrl(context, PROJECT_CHANNEL_URL) }
                )
                SettingRow(
                    icon = Icons.Filled.Group,
                    title = stringResource(R.string.about_community),
                    subtitle = stringResource(R.string.about_community_sub),
                    onClick = { openUrl(context, PROJECT_COMMUNITY_URL) }
                )
                SettingRow(
                    icon = Icons.Filled.Favorite,
                    title = stringResource(R.string.about_support),
                    subtitle = stringResource(R.string.about_support_sub),
                    onClick = { openUrl(context, DEV_KOFI_URL) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    )
}

private const val DEVELOPER_HANDLE = "Alaa91H"
private const val DEV_GITHUB_URL = "https://github.com/Alaa91H"
private const val DEV_EMAIL = "alahus2591@gmail.com"
private const val DEV_TELEGRAM_URL = "https://t.me/Alaa91h"
private const val DEV_KOFI_URL = "https://ko-fi.com/alaa91h"

// Project surfaces: the source repository, the release/announcement channel
// and the user community group. Kept separate from the developer's personal
// contacts so each link states where it leads.
private const val PROJECT_REPOSITORY_URL = "https://github.com/Alaa91H/NexaFlow"
private const val PROJECT_CHANNEL_URL = "https://t.me/NexaFlowApp"
private const val PROJECT_COMMUNITY_URL = "https://t.me/NexaFlowCommunity"

/** Opens [url] in the system browser (best-effort, no crash on missing handler). */
private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/** Opens the default mail app addressed to [address] (best-effort). */
private fun openEmail(context: Context, address: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
    }
}
