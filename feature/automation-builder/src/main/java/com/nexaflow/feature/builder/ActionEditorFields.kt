package com.nexaflow.feature.builder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexaflow.core.execution.NotificationActionButton
import com.nexaflow.core.rom.DnsProviderCatalog
import com.nexaflow.core.rom.NetworkModeCapabilities
import com.nexaflow.core.rom.NetworkModePolicy
import com.nexaflow.core.rom.NetworkModeSnapshot
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.RootPermissionGranter
import com.nexaflow.core.rom.ShizukuShellBridge
import com.nexaflow.core.ui.SelectChip
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.variables.VariableResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Simple toggle actions that use "turn_on" as their label. */
private val TURN_ON_TOGGLE_ACTIONS = setOf(
    ActionType.SYSTEM_LOCATION,
    ActionType.SYSTEM_DND,
    ActionType.SYSTEM_WIFI,
    ActionType.SYSTEM_BLUETOOTH,
    ActionType.SYSTEM_FLASHLIGHT,
    ActionType.SYSTEM_AIRPLANE_MODE,
    ActionType.SYSTEM_STAY_AWAKE,
    ActionType.SYSTEM_AUTO_BRIGHTNESS,
    ActionType.SYSTEM_MOBILE_DATA,
    ActionType.SYSTEM_HOTSPOT,
    ActionType.SYSTEM_NFC,
    ActionType.SYSTEM_POWER_SAVER,
    ActionType.SYSTEM_ANIMATIONS,
    ActionType.SYSTEM_DARK_MODE,
    ActionType.SYSTEM_DATA_ROAMING,
    ActionType.SYSTEM_CALL_VIBRATION,
    ActionType.SYSTEM_STATUS_BAR_TOGGLE
)

/**
 * Network-mode editor backed by a live per-subscription telephony snapshot.
 * It never promotes a generic generation list to a device capability: when
 * Android/OEM policy blocks the read, the card says so and preserves existing
 * task data rather than inventing choices that the device may reject.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PackagePickerField(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
    onPickApp: () -> Unit,
    multiPackage: Boolean = false,
    label: Int = R.string.package_name
) {
    val key = if (multiPackage) "packages" else "package"
    val value = if (multiPackage) {
        (config["packages"] ?: config["package"] ?: "")
    } else {
        config["package"] ?: ""
    }
    val fieldLabel = if (multiPackage) R.string.apps_comma else label
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { onConfigChange((config - if (multiPackage) "package" else "packages") + (key to it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(fieldLabel)) },
            singleLine = true
        )
        TextButton(onClick = onPickApp) {
            Text(text = stringResource(R.string.choose_from_installed))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VariableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    availableVariables: List<String>,
    placeholder: String? = null,
    singleLine: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(label)) },
            placeholder = placeholder?.let { { Text(text = it) } },
            singleLine = singleLine
        )
        VariableInsertChips(
            availableVariables = availableVariables,
            currentValue = value,
            onValueChange = onValueChange
        )
    }
}

@Composable
internal fun RunsImmediatelyHint() {
    Text(
        text = stringResource(R.string.runs_immediately),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )
}


