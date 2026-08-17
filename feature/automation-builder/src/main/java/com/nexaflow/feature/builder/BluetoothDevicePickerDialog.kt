package com.nexaflow.feature.builder

import androidx.compose.ui.text.font.FontWeight

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.runtime.remember
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDevicePickerDialog(
    onPick: (PairedDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val devices = remember { loadPairedDevices(context) }

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
        if (devices.isEmpty()) {
            Text(
                text = stringResource(R.string.no_paired_devices),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 24.dp)
            ) {
                items(devices, key = { it.address }) { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(device) }
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
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = device.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
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
