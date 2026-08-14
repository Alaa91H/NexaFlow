package com.nexaflow.core.engine

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires automations when a specific paired Bluetooth device connects or
 * disconnects (e.g. headphones). Matches both the legacy BLUETOOTH_DEVICE
 * trigger type and DEVICE triggers configured with the "BLUETOOTH" event
 * (event = "BLUETOOTH_CONNECTED" / "BLUETOOTH_DISCONNECTED").
 *
 * The trigger config supports:
 *  - "deviceName": display name of the paired device (required)
 *  - "event": "CONNECTED"/"DISCONNECTED" (legacy) or
 *    "BLUETOOTH_CONNECTED"/"BLUETOOTH_DISCONNECTED" (merged DEVICE trigger)
 */
@Singleton
class BluetoothMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    private val lastRunAt = mutableMapOf<String, Long>()
    private val activeConnections = mutableMapOf<String, String>() // automationId -> deviceAddress

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val device = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val connected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
            if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED &&
                intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED
            ) {
                return
            }
            val event = if (connected) "CONNECTED" else "DISCONNECTED"
            val name = if (
                android.os.Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                runCatching { device.name }.getOrNull() ?: device.address ?: ""
            } else {
                device.address ?: ""
            }
            handleEvent(device.address ?: "", name, event)
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
        scope.launch {
            // Re-arm the durable active set BEFORE the next ACL broadcast:
            // a task whose connect/disconnect condition already ended while
            // the process was down fires its missed exit on the next broadcast
            // for that device.
            rearmFromLedger()
        }
    }

    /**
     * Restores the durable active keys into the in-memory map. Keys carry the
     * device address (`id|AA:BB:..`), so the restored entry matches the exit
     * check exactly. Stale keys for deleted/disabled automations are pruned.
     */
    private suspend fun rearmFromLedger() {
        val enabledIds = repository.getAutomations().first()
            .filter { it.enabled }
            .map { it.id }
            .toSet()
        activeStore.activeKeys(SOURCE).forEach { key ->
            val id = key.substringBefore('|')
            if (id in enabledIds) {
                activeConnections[id] = key.substringAfter('|', "")
            } else {
                activeStore.clearAutomation(SOURCE, id)
            }
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun handleEvent(address: String, deviceName: String, event: String) {
        scope.launch {
            val automations = repository.getAutomations().first()
            val now = System.currentTimeMillis()
            automations
                .filter { automation ->
                    automation.enabled && automation.triggers.any { trigger ->
                        isBluetoothTrigger(trigger.type) &&
                            matchesDevice(trigger.config, address, deviceName)
                    }
                }
                .forEach { automation ->
                    val deviceTriggers = automation.triggers.filter {
                        isBluetoothTrigger(it.type) && matchesDevice(it.config, address, deviceName)
                    }
                    val firesOnConnect = deviceTriggers.any { wantsEvent(it.config, "CONNECTED") }
                    val firesOnDisconnect = deviceTriggers.any { wantsEvent(it.config, "DISCONNECTED") }
                    if (event == "CONNECTED") {
                        if (firesOnConnect) {
                            val last = lastRunAt[automation.id] ?: 0L
                            if (now - last > automation.cooldownMillis) {
                                lastRunAt[automation.id] = now
                                activeConnections[automation.id] = address
                                activeStore.markActive(SOURCE, "${automation.id}|$address")
                                executionEngine.runAutomation(automation)
                            }
                        } else if (firesOnDisconnect && activeConnections[automation.id] == address) {
                            // The device reconnected: the disconnect condition ended.
                            activeConnections.remove(automation.id)
                            activeStore.clearAutomation(SOURCE, automation.id)
                            executionEngine.runExit(automation)
                        }
                    } else {
                        if (firesOnDisconnect) {
                            val last = lastRunAt[automation.id] ?: 0L
                            if (now - last > automation.cooldownMillis) {
                                lastRunAt[automation.id] = now
                                activeConnections[automation.id] = address
                                activeStore.markActive(SOURCE, "${automation.id}|$address")
                                executionEngine.runAutomation(automation)
                            }
                        } else if (firesOnConnect && activeConnections[automation.id] == address) {
                            // The device disconnected: the connect condition ended.
                            activeConnections.remove(automation.id)
                            activeStore.clearAutomation(SOURCE, automation.id)
                            executionEngine.runExit(automation)
                        }
                    }
                }
        }
    }

    private fun isBluetoothTrigger(type: TriggerType): Boolean =
        type == TriggerType.BLUETOOTH_DEVICE || type == TriggerType.DEVICE

    /** True when the trigger fires for the given connect/disconnect event. */
    private fun wantsEvent(config: Map<String, String>, event: String): Boolean {
        val value = config["event"] ?: "CONNECTED"
        return when (event) {
            "CONNECTED" -> value == "CONNECTED" || value == "BLUETOOTH_CONNECTED"
            "DISCONNECTED" -> value == "DISCONNECTED" || value == "BLUETOOTH_DISCONNECTED"
            else -> false
        }
    }

    private fun matchesDevice(config: Map<String, String>, address: String, deviceName: String): Boolean {
        val configuredName = config["deviceName"].orEmpty().trim()
        if (configuredName.isEmpty()) return false
        // Match by name, or by the address stored together with the name.
        val storedAddress = config["deviceAddress"].orEmpty()
        return deviceName.equals(configuredName, ignoreCase = true) ||
            (storedAddress.isNotEmpty() && storedAddress.equals(address, ignoreCase = true))
    }

    private companion object {
        const val SOURCE = "bluetooth"
    }
}
