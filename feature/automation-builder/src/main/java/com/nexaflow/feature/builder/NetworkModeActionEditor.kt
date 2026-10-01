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
internal fun NetworkModeSelector(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<NetworkModeSnapshot?>(null) }
    var permissionRevision by remember { mutableStateOf(0) }
    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Always re-read: a denial remains explicit, while a grant can expose
        // the live per-SIM capabilities without requiring the user to reopen.
        permissionRevision += 1
    }
    LaunchedEffect(context, permissionRevision) {
        snapshot = withContext(Dispatchers.IO) {
            NetworkModeCapabilities(context.applicationContext).read()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.network_mode_label), style = MaterialTheme.typography.titleSmall)
        when (val state = snapshot) {
            null -> Text(
                text = stringResource(R.string.network_mode_reading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            else -> when (state.status) {
                NetworkModeSnapshot.Status.AVAILABLE -> {
                    val subscriptions = state.subscriptions
                    val selectedSubscriptionId = NetworkModePolicy.selectSubscriptionId(
                        savedSubscriptionId = config["network_subscription_id"]?.toIntOrNull(),
                        activeDataSubscriptionId = state.activeDataSubscriptionId,
                        availableSubscriptionIds = subscriptions.map { it.subscriptionId }
                    ) ?: subscriptions.first().subscriptionId
                    val selectedSubscription = subscriptions.first { it.subscriptionId == selectedSubscriptionId }
                    if (subscriptions.size > 1) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            subscriptions.forEach { subscription ->
                                val simLabel = stringResource(
                                    R.string.network_mode_sim,
                                    subscription.slotIndex + 1
                                )
                                SelectChip(
                                    selected = subscription.subscriptionId == selectedSubscriptionId,
                                    onClick = {
                                        onConfigChange(
                                            config + ("network_subscription_id" to subscription.subscriptionId.toString()) -
                                                setOf("network_mask", "network_mask_schema")
                                        )
                                    },
                                    label = if (subscription.isActiveDataSubscription) {
                                        "$simLabel · ${stringResource(R.string.network_mode_data_sim)}"
                                    } else {
                                        simLabel
                                    }
                                )
                            }
                        }
                    }
                    selectedSubscription.configuredUserMask?.let { configured ->
                        Text(
                            text = stringResource(
                                R.string.network_mode_configured,
                                NetworkModePolicy.describe(configured)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        selectedSubscription.knownEffectiveMask
                            ?.takeIf { it != configured }
                            ?.let { effective ->
                                Text(
                                    text = stringResource(
                                        R.string.network_mode_effective,
                                        NetworkModePolicy.describe(effective)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                    }
                    val savedMask = config["network_mask"]?.toLongOrNull()
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selectedSubscription.options.forEach { option ->
                            val selected = savedMask == option.allowedNetworkTypes ||
                                (savedMask == null && option.isAutomatic &&
                                    (config["mode"] ?: "AUTO") == "AUTO")
                            SelectChip(
                                selected = selected,
                                onClick = {
                                    onConfigChange(
                                        config + mapOf(
                                            "mode" to if (option.isAutomatic) "AUTO" else "DYNAMIC",
                                            "network_mask" to option.allowedNetworkTypes.toString(),
                                            "network_mask_schema" to NetworkModePolicy.NETWORK_MASK_SCHEMA_AOSP_V1,
                                            "network_subscription_id" to selectedSubscriptionId.toString()
                                        )
                                    )
                                },
                                label = if (option.isAutomatic) {
                                    "${stringResource(R.string.network_mode_auto)}: ${NetworkModePolicy.displayLabel(option)}"
                                } else {
                                    NetworkModePolicy.displayLabel(option)
                                }
                            )
                        }
                    }
                }
                NetworkModeSnapshot.Status.NO_TELEPHONY,
                NetworkModeSnapshot.Status.NO_ACTIVE_SUBSCRIPTION,
                NetworkModeSnapshot.Status.UNREADABLE -> {
                    Text(
                        text = stringResource(R.string.network_mode_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    state.diagnostics.firstOrNull()?.let { diagnostic ->
                        Text(
                            text = diagnostic,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    val phoneStateGranted = context.checkSelfPermission(
                        Manifest.permission.READ_PHONE_STATE
                    ) == PackageManager.PERMISSION_GRANTED
                    if (state.status == NetworkModeSnapshot.Status.UNREADABLE) {
                        if (!phoneStateGranted) {
                            TextButton(
                                onClick = {
                                    // If `su` exists but has not yet approved
                                    // NexaFlow, request that approval first. The
                                    // helper grants and verifies READ_PHONE_STATE
                                    // before Android's dialog is considered.
                                    RootPermissionGranter.requestRuntimePermissionsWithRootPrompt(
                                        context = context,
                                        permissions = listOf(Manifest.permission.READ_PHONE_STATE)
                                    ) { result ->
                                        // Always re-read the live SIM capabilities
                                        // after an elevated attempt. Android's
                                        // dialog is only a factual fallback for a
                                        // permission still missing after it.
                                        permissionRevision += 1
                                        if (result.remaining.isNotEmpty()) {
                                            phoneStatePermissionLauncher.launch(
                                                Manifest.permission.READ_PHONE_STATE
                                            )
                                        }
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.network_mode_grant_phone_permission))
                            }
                        }
                        val shizukuReady = PrivilegedRunner.isShizukuGranted() &&
                            ShizukuShellBridge.isUserServiceBound
                        val elevatedReady = shizukuReady || PrivilegedRunner.isRootAvailable()
                        if (!elevatedReady) {
                            TextButton(
                                onClick = {
                                    if (PrivilegedRunner.isShizukuGranted()) {
                                        // A granted server can survive while its
                                        // UserService is disconnected on some ROMs.
                                        // Reconnect rather than falsely presenting
                                        // the permission as a usable execution path.
                                        ShizukuShellBridge.reconnect(context)
                                        permissionRevision += 1
                                    } else {
                                        // Use the targeted root path so the UI
                                        // refreshes on the main thread after the
                                        // superuser grant and verified permission
                                        // check, rather than leaving an old
                                        // UNREADABLE snapshot on screen.
                                        RootPermissionGranter.requestRuntimePermissionsWithRootPrompt(
                                            context = context,
                                            permissions = listOf(Manifest.permission.READ_PHONE_STATE)
                                        ) {
                                            permissionRevision += 1
                                        }
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.special_elevated_title))
                            }
                        }
                        TextButton(
                            onClick = {
                                if (PrivilegedRunner.isShizukuGranted() &&
                                    !ShizukuShellBridge.isUserServiceBound
                                ) {
                                    ShizukuShellBridge.reconnect(context)
                                }
                                permissionRevision += 1
                            }
                        ) {
                            Text(stringResource(R.string.refresh))
                        }
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.network_mode_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

