package com.nexaflow.core.engine

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
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
    private val runtimeStore: AutomationRuntimeStore,
    private val exitCoordinator: ExitCoordinator,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    private val lastRunAt = mutableMapOf<String, Long>()
    private val activeConnections = mutableMapOf<String, String>() // automationId -> deviceAddress

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                handleBondStateChanged(intent)
                return
            }
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

    /**
     * Android 16+ surfaces bond losses (key missing, encryption change
     * failures) that previously arrived only as vendor-log noise. A removed
     * bond must immediately close every connect-condition task bound to that
     * device even when no ACL_DISCONNECT follows — a device that is powered
     * off or out of range never broadcasts one, which used to leave such
     * tasks durably "active" until the device returned.
     *
     * The reason extra is a hidden platform constant
     * (android.bluetooth.device.extra.REASON); it is read defensively and is
     * diagnostic-only — the lifecycle cleanup never depends on it.
     */
    private fun handleBondStateChanged(intent: Intent) {
        val device = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        } ?: return
        val newState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
        val previousState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
        if (newState != BluetoothDevice.BOND_NONE) return
        if (previousState != BluetoothDevice.BOND_BONDED && previousState != BluetoothDevice.BOND_BONDING) return
        val address = device.address ?: return
        val reason = intent.getIntExtra(EXTRA_BOND_LOSS_REASON, Int.MIN_VALUE)
        Log.w(
            TAG,
            "Bluetooth bond lost for $address (previous=$previousState, reason=$reason); " +
                "closing connect-condition tasks bound to this device"
        )
        scope.launch {
            val automations = repository.getAutomations().first()
            automations
                .filter { it.enabled && activeConnections[it.id] == address }
                .forEach { automation ->
                    val hasConnectCondition = automation.triggers.any { trigger ->
                        isBluetoothTrigger(trigger.type) && wantsEvent(trigger.config, "CONNECTED")
                    }
                    if (!hasConnectCondition) return@forEach
                    requestExit(
                        automation = automation,
                        reason = ExitReason.TRIGGER_FALSE
                    )
                }
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            // Bond-loss diagnostics + immediate connect-condition cleanup
            // (key missing / unpair while the device is unreachable).
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
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
    internal suspend fun rearmFromLedger() {
        val automations = repository.getAutomations().first().associateBy { it.id }
        // The durable occurrence ledger is authoritative: a session that
        // survived a process restart re-arms only its exit side, so the
        // configured end behavior still runs exactly once on the opposite
        // event. Legacy ActiveTriggerStore keys are honoured for pre-existing
        // entries but no longer create exits on their own.
        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = automations[state.automationId]
                when {
                    automation == null -> {
                        // Missing immutable definition is not a successful
                        // cleanup signal. Preserve durable ownership for
                        // recovery review and clear only volatile mirrors.
                        activeConnections.remove(state.automationId)
                        activeStore.clearAutomation(SOURCE, state.automationId)
                    }
                    automation.enabled && automation.triggers.any { isBluetoothTrigger(it.type) } -> {
                        activeConnections[state.automationId] = state.sourceKey
                            .substringAfter('|', state.sourceKey)
                        activeStore.markActive(SOURCE, state.sourceKey)
                    }
                    else -> requestExit(
                        automation = automation,
                        reason = ExitReason.AUTOMATION_DISABLED,
                        occurrenceId = state.occurrenceId
                    )
                }
            }
        activeStore.activeKeys(SOURCE).forEach { key ->
            val id = key.substringBefore('|')
            val automation = automations[id]
            if (automation?.enabled == true && automation.triggers.any { isBluetoothTrigger(it.type) }) {
                if (runtimeStore.current(id)?.source == SOURCE) {
                    activeConnections[id] = key.substringAfter('|', "")
                }
            } else {
                activeConnections.remove(id)
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
                                val occurrenceId = "bluetooth:${automation.id}:${UUID.randomUUID()}"
                                val sourceKey = "${automation.id}|$address"
                                executionEngine.runAutomation(
                                    automation = automation,
                                    lifecycleContext = AutomationLifecycleContext(
                                        occurrenceId = occurrenceId,
                                        source = SOURCE,
                                        sourceKey = sourceKey
                                    )
                                )
                                val accepted = runtimeStore.current(automation.id)?.let { state ->
                                    state.source == SOURCE && state.occurrenceId == occurrenceId
                                } == true
                                if (accepted) {
                                    activeConnections[automation.id] = address
                                    activeStore.markActive(SOURCE, sourceKey)
                                } else {
                                    lastRunAt.remove(automation.id)
                                }
                            }
                        } else if (firesOnDisconnect && activeConnections[automation.id] == address) {
                            // The device reconnected: the disconnect condition ended.
                            requestExit(
                                automation = automation,
                                reason = ExitReason.TRIGGER_FALSE
                            )
                        }
                    } else {
                        if (firesOnDisconnect) {
                            val last = lastRunAt[automation.id] ?: 0L
                            if (now - last > automation.cooldownMillis) {
                                lastRunAt[automation.id] = now
                                val occurrenceId = "bluetooth:${automation.id}:${UUID.randomUUID()}"
                                val sourceKey = "${automation.id}|$address"
                                executionEngine.runAutomation(
                                    automation = automation,
                                    lifecycleContext = AutomationLifecycleContext(
                                        occurrenceId = occurrenceId,
                                        source = SOURCE,
                                        sourceKey = sourceKey
                                    )
                                )
                                val accepted = runtimeStore.current(automation.id)?.let { state ->
                                    state.source == SOURCE && state.occurrenceId == occurrenceId
                                } == true
                                if (accepted) {
                                    activeConnections[automation.id] = address
                                    activeStore.markActive(SOURCE, sourceKey)
                                } else {
                                    lastRunAt.remove(automation.id)
                                }
                            }
                        } else if (firesOnConnect && activeConnections[automation.id] == address) {
                            // The device disconnected: the connect condition ended.
                            requestExit(
                                automation = automation,
                                reason = ExitReason.TRIGGER_FALSE
                            )
                        }
                    }
                }
        }
    }

    private suspend fun requestExit(
        automation: com.nexaflow.domain.models.Automation,
        reason: ExitReason,
        occurrenceId: String? = runtimeStore.current(automation.id)
            ?.takeIf { it.source == SOURCE }
            ?.occurrenceId
    ) {
        when (
            exitCoordinator.requestExit(
                automation = automation,
                reason = reason,
                occurrenceId = occurrenceId
            )
        ) {
            is ExitCoordinatorResult.Executed,
            ExitCoordinatorResult.NotActive,
            ExitCoordinatorResult.StaleOccurrence -> {
                activeConnections.remove(automation.id)
                activeStore.clearAutomation(SOURCE, automation.id)
            }
            ExitCoordinatorResult.AlreadyInProgress,
            is ExitCoordinatorResult.RecoveryRequired -> {
                runtimeStore.current(automation.id)
                    ?.takeIf { it.source == SOURCE }
                    ?.let { state ->
                        activeConnections[automation.id] =
                            state.sourceKey.substringAfter('|', state.sourceKey)
                        activeStore.markActive(SOURCE, state.sourceKey)
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
        // Professional ANY support: empty, "*" or "__ANY__" means any device
        if (configuredName.isEmpty() || configuredName == "__ANY__" || configuredName == "*" || configuredName.equals("ANY", ignoreCase = true)) {
            return true
        }
        // Match by name, or by the address stored together with the name.
        val storedAddress = config["deviceAddress"].orEmpty()
        return deviceName.equals(configuredName, ignoreCase = true) ||
            (storedAddress.isNotEmpty() && storedAddress.equals(address, ignoreCase = true))
    }

    private companion object {
        const val SOURCE = "bluetooth"
        const val TAG = "BluetoothMonitor"

        /** Hidden platform extra (android.bluetooth.device.extra.REASON). */
        const val EXTRA_BOND_LOSS_REASON = "android.bluetooth.device.extra.REASON"
    }
}
