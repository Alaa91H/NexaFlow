package com.nexaflow.feature.builder

import androidx.compose.ui.text.font.FontWeight

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** A paired Bluetooth device shown in the picker. */
data class PairedDevice(
    val name: String,
    val address: String
)

/**
 * Lists all paired Bluetooth devices so the user can pick the one that should
 * trigger the task when it connects or disconnects (e.g. headphones).
 *
 * The list is loaded live on every [refreshTick]: when Bluetooth is off the
 * sheet explains that and offers a single tap that fires the system
 * ACTION_REQUEST_ENABLE dialog (the only user-visible, launcher-approved way
 * to turn Bluetooth on), and when BLUETOOTH_CONNECT is still missing it
 * offers the runtime grant instead. Returning from either flow re-probes and
 * repopulates the list without closing the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDevicePickerDialog(
    onPick: (PairedDevice) -> Unit,
    onDismiss: () -> Unit,
    /** Currently configured device address, pre-marked when the picker reopens. */
    preSelectedAddress: String? = null
) {
    val context = LocalContext.current
    // Re-probe on every resume: after the enable dialog or the runtime grant
    // dialog closes, the sheet updates itself in place instead of showing a
    // stale "Bluetooth is off" state.
    var refreshTick by remember { mutableIntStateOf(0) }
    // Read the lifecycle owner in composable scope; inside sheet content it
    // resolves to the host activity's lifecycle, which is what resumes after
    // the system Bluetooth dialog returns.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val devices = remember(refreshTick) { loadPairedDevices(context) }
    val adapterAvailable = remember(refreshTick) {
        context.getSystemService(BluetoothManager::class.java)?.adapter != null
    }
    val bluetoothEnabled = remember(refreshTick) { isBluetoothAdapterEnabled(context) }
    val connectGranted = remember(refreshTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }
    // Selection model matches the app picker: tapping marks the device, OK
    // confirms it, Cancel discards. No tap applies anything by itself.
    var selectedAddress by remember {
        mutableStateOf(
            devices.firstOrNull { it.address == preSelectedAddress }?.address
        )
    }

    // One tap -> the system "Turn on Bluetooth?" dialog. Adapter.enable() is
    // unavailable to third-party apps; ACTION_REQUEST_ENABLE is its designed
    // replacement and needs no permission at all.
    val enableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // RESULT_OK (or a cancelled dialog) both land on the next ON_RESUME,
        // which bumps refreshTick and re-probes the adapter state.
        if (result.resultCode == Activity.RESULT_OK) refreshTick++
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) refreshTick++
    }

    // Google 2026: selection tasks open as a full-height modal bottom sheet.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Text(
            text = stringResource(R.string.choose_bluetooth_device),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
        )
        when {
            // Hardware without a Bluetooth adapter: nothing to offer, keep the
            // established empty message.
            !adapterAvailable -> {
                Text(
                    text = stringResource(R.string.no_paired_devices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            // Bluetooth is off: explain it and hand the user a one-tap fix.
            !bluetoothEnabled -> {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 8.dp)
                            .size(36.dp)
                    )
                    Text(
                        text = stringResource(R.string.bluetooth_turned_off_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.bluetooth_turned_off_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )
                    androidx.compose.material3.Button(
                        onClick = {
                            runCatching {
                                enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }.onFailure {
                                // Some builds ship no handler for the request dialog;
                                // fall back to the Bluetooth settings screen.
                                PermissionShortcuts.openBluetoothSettings(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.turn_on))
                    }
                }
            }
            // Paired devices exist but their names need the connect grant;
            // the engine also needs it to read the device name from ACL
            // broadcasts, so offer the runtime dialog instead of an address-
            // only list.
            devices.isEmpty() && !connectGranted -> {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = stringResource(R.string.bluetooth_permission_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    androidx.compose.material3.Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.enable))
                    }
                }
            }
            devices.isEmpty() -> {
                Text(
                    text = stringResource(R.string.no_paired_devices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .padding(horizontal = 24.dp)
                ) {
                    items(devices, key = { it.address }) { device ->
                        val isSelected = device.address == selectedAddress
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAddress = device.address }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bluetooth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                                Text(
                                    text = device.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        // Same confirm/discard contract as the app picker: OK applies the
        // marked device (disabled until one is marked), Cancel discards.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.cancel))
            }
            androidx.compose.material3.Button(
                onClick = {
                    devices.firstOrNull { it.address == selectedAddress }?.let(onPick)
                },
                enabled = selectedAddress != null,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.bt_pick_ok))
            }
        }
    }
}

// BLUETOOTH_CONNECT is checked at runtime above the runCatching block below;
// lint's dataflow cannot follow the guard through the try/catch boundary, so the
// suppression is scoped to this loader only.
@SuppressLint("MissingPermission")
private fun loadPairedDevices(context: Context): List<PairedDevice> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    if (!adapter.isEnabled) return emptyList()
    return runCatching {
        adapter.bondedDevices
            .filter { it.type != BluetoothDevice.DEVICE_TYPE_LE }
            .mapNotNull { device ->
                val name = runCatching { device.name }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                PairedDevice(name = name, address = device.address)
            }
            .sortedBy { it.name.lowercase() }
    }.getOrElse { emptyList() }
}

/** True when the adapter exists and is currently on (permission-safe probe). */
private fun isBluetoothAdapterEnabled(context: Context): Boolean = runCatching {
    context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
}.getOrDefault(false)
