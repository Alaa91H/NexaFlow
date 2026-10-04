package com.nexaflow.feature.settings

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nexaflow.core.ui.SettingRow

@Composable
internal fun SmsDefaultRoleSettingRow() {
    val context = LocalContext.current
    var showDisclosure by remember { mutableStateOf(false) }
    var isDefaultSmsApp by remember {
        mutableStateOf(Telephony.Sms.getDefaultSmsPackage(context) == context.packageName)
    }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultSmsApp = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

    SettingRow(
        icon = Icons.Filled.Notifications,
        title = stringResource(R.string.sms_default_role_title),
        subtitle = stringResource(R.string.sms_default_role_subtitle),
        trailing = {
            Text(
                text = stringResource(
                    if (isDefaultSmsApp) R.string.sms_default_role_active else R.string.sms_default_role_set
                ),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        onClick = { if (!isDefaultSmsApp) showDisclosure = true },
    )

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.sms_default_role_disclosure_title)) },
            text = { Text(stringResource(R.string.sms_default_role_disclosure_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val roles = context.getSystemService(RoleManager::class.java)
                        if (roles.isRoleAvailable(RoleManager.ROLE_SMS)) {
                            roles.createRequestRoleIntent(RoleManager.ROLE_SMS)
                        } else Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                    } else {
                        Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(
                            Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                            context.packageName,
                        )
                    }
                    roleLauncher.launch(request)
                }) { Text(stringResource(R.string.sms_default_role_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
