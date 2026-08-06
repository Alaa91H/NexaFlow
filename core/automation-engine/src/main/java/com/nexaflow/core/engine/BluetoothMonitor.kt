package com.nexaflow.core.engine

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires automations with a BLUETOOTH_DEVICE trigger when a specific paired
 * Bluetooth device connects or disconnects (e.g. headphones). The trigger
 * config supports:
 *  - "deviceName": display name of the paired device (required)
 *  - "event": "CONNECTED" or "DISCONNECTED"
 */
@Singleton
class BluetoothMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
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
            val event = when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> "CONNECTED"
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> "DISCONNECTED"
                else -> return
            }
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
                        trigger.type == TriggerType.BLUETOOTH_DEVICE &&
                            matchesDevice(trigger.config, address, deviceName)
                    }
                }
                .forEach { automation ->
                    val deviceTriggers = automation.triggers.filter {
                        it.type == TriggerType.BLUETOOTH_DEVICE && matchesDevice(it.config, address, deviceName)
                    }
                    val firesOnConnect = deviceTriggers.any { (it.config["event"] ?: "CONNECTED") == "CONNECTED" }
                    val firesOnDisconnect = deviceTriggers.any { (it.config["event"] ?: "CONNECTED") == "DISCONNECTED" }
                    if (event == "CONNECTED") {
                        if (firesOnConnect) {
                            val last = lastRunAt[automation.id] ?: 0L
                            if (now - last > COOLDOWN_MS) {
                                lastRunAt[automation.id] = now
                                activeConnections[automation.id] = address
                                executionEngine.runAutomation(automation)
                            }
                        } else if (firesOnDisconnect && activeConnections[automation.id] == address) {
                            // The device reconnected: the disconnect condition ended.
                            activeConnections.remove(automation.id)
                            executionEngine.runExit(automation)
                        }
                    } else {
                        if (firesOnDisconnect) {
                            val last = lastRunAt[automation.id] ?: 0L
                            if (now - last > COOLDOWN_MS) {
                                lastRunAt[automation.id] = now
                                activeConnections[automation.id] = address
                                executionEngine.runAutomation(automation)
                            }
                        } else if (firesOnConnect && activeConnections[automation.id] == address) {
                            // The device disconnected: the connect condition ended.
                            activeConnections.remove(automation.id)
                            executionEngine.runExit(automation)
                        }
                    }
                }
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

    companion object {
        private const val COOLDOWN_MS = 5_000L
    }
}
