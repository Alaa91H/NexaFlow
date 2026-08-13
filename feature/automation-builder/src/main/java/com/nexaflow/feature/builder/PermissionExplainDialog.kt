package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.nexaFlowDialogEnter

/** Icon, title and explanation shown right before granting a permission. */
data class PermissionExplainInfo(
    val icon: ImageVector,
    val color: Color,
    val titleRes: Int,
    val bodyRes: Int
)

/** Special permissions granted via their dedicated system settings screen. */
enum class SpecialPermission {
    WRITE_SETTINGS,
    DND_ACCESS,
    NOTIFICATION_ACCESS,
    ACCESSIBILITY,
    SHIZUKU,
    ROOT,
    ELEVATED,
    BLUETOOTH
}

/**
 * Maps a requested runtime permission set to its Samsung-style explanation
 * (icon + reason). Falls back to a generic explanation for unknown permissions.
 */
fun permissionExplainInfo(permissions: Array<String>): PermissionExplainInfo = when {
    permissions.any { it == android.Manifest.permission.ACCESS_FINE_LOCATION || it == android.Manifest.permission.ACCESS_COARSE_LOCATION } ->
        PermissionExplainInfo(Icons.Filled.LocationOn, Color(0xFF006D3C), R.string.permission_location_title, R.string.permission_location_body)
    permissions.any { it == android.Manifest.permission.RECEIVE_SMS } ->
        PermissionExplainInfo(Icons.AutoMirrored.Filled.Message, Color(0xFF0B57D0), R.string.permission_sms_title, R.string.permission_sms_body)
    permissions.any { it == android.Manifest.permission.SEND_SMS } ->
        PermissionExplainInfo(Icons.AutoMirrored.Filled.Message, Color(0xFF006A6C), R.string.permission_send_sms_title, R.string.permission_send_sms_body)
    permissions.any { it == android.Manifest.permission.CAMERA } ->
        PermissionExplainInfo(Icons.Filled.FlashOn, Color(0xFF8F4C00), R.string.permission_camera_title, R.string.permission_camera_body)
    permissions.any { it == android.Manifest.permission.POST_NOTIFICATIONS } ->
        PermissionExplainInfo(Icons.Filled.NotificationsActive, Color(0xFF6750A4), R.string.permission_notifications_title, R.string.permission_notifications_body)
    permissions.any { it == android.Manifest.permission.BLUETOOTH_CONNECT } ->
        PermissionExplainInfo(Icons.Filled.Bluetooth, Color(0xFF006D3C), R.string.permission_bluetooth_title, R.string.permission_bluetooth_body)
    permissions.any { it == android.Manifest.permission.READ_CALENDAR } ->
        PermissionExplainInfo(Icons.Filled.DateRange, Color(0xFFBA1A1A), R.string.permission_calendar_title, R.string.permission_calendar_body)
    permissions.any { it == android.Manifest.permission.ACCESS_LOCAL_NETWORK } ->
        PermissionExplainInfo(Icons.Filled.Language, Color(0xFF006A6C), R.string.permission_local_network_title, R.string.permission_local_network_body)
    else ->
        PermissionExplainInfo(Icons.Filled.Lock, Color(0xFF0B57D0), R.string.permission_generic_title, R.string.permission_generic_body)
}

/**
 * Maps a special (settings-screen) permission to its Samsung-style explanation.
 * These open a dedicated system settings screen instead of a permission dialog,
 * so the explain screen explains why and where the user is being taken.
 */
fun specialPermissionExplainInfo(type: SpecialPermission): PermissionExplainInfo = when (type) {
    SpecialPermission.WRITE_SETTINGS ->
        PermissionExplainInfo(Icons.Filled.Settings, Color(0xFF0B57D0), R.string.special_write_settings_title, R.string.special_write_settings_body)
    SpecialPermission.DND_ACCESS ->
        PermissionExplainInfo(Icons.Filled.DoNotDisturb, Color(0xFFBA1A1A), R.string.special_dnd_title, R.string.special_dnd_body)
    SpecialPermission.NOTIFICATION_ACCESS ->
        PermissionExplainInfo(Icons.Filled.Notifications, Color(0xFF8F4C00), R.string.special_notification_access_title, R.string.special_notification_access_body)
    SpecialPermission.ACCESSIBILITY ->
        PermissionExplainInfo(Icons.Filled.Accessibility, Color(0xFF6750A4), R.string.special_accessibility_title, R.string.special_accessibility_body)
    SpecialPermission.SHIZUKU ->
        PermissionExplainInfo(Icons.Filled.Terminal, Color(0xFF006A6C), R.string.special_shizuku_title, R.string.special_shizuku_body)
    SpecialPermission.ROOT ->
        PermissionExplainInfo(Icons.Filled.Terminal, Color(0xFFBA1A1A), R.string.special_root_title, R.string.special_root_body)
    SpecialPermission.ELEVATED ->
        PermissionExplainInfo(Icons.Filled.Lock, Color(0xFF6750A4), R.string.special_elevated_title, R.string.special_elevated_body)
    SpecialPermission.BLUETOOTH ->
        PermissionExplainInfo(Icons.Filled.Bluetooth, Color(0xFF006D3C), R.string.special_bluetooth_title, R.string.special_bluetooth_body)
}

/**
 * Google-style explain screen shown inside the editor right before a permission
 * is granted (either the system permission dialog or a dedicated settings screen):
 * a large tinted icon, a short reason and a clear Continue action.
 */
@Composable
fun PermissionExplainDialog(
    info: PermissionExplainInfo,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .nexaFlowDialogEnter()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconBadge(
                    icon = info.icon,
                    containerColor = info.color.copy(alpha = 0.16f),
                    contentColor = info.color,
                    size = 72
                )
                Text(
                    text = stringResource(R.string.permission_explain_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(info.titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(info.bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(text = stringResource(R.string.permission_continue))
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.permission_not_now))
                }
            }
        }
    }
}
