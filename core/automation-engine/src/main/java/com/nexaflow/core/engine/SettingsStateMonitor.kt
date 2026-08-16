package com.nexaflow.core.engine

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.nexaflow.core.datastore.ActiveTriggerStore
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
 * Consolidated monitor for the device-state triggers that are backed by a
 * live settings key or radio state:
 *
 *  - POWER_SAVER        (global `low_power`)
 *  - BLUETOOTH_STATE    (adapter state broadcast)
 *  - BRIGHTNESS_LEVEL   (system `screen_brightness` — threshold crossing)
 *  - STORAGE_LOW        (free-space threshold crossing)
 *  - AUTO_ROTATE        (system `accelerometer_rotation`)
 *  - DATA_SAVER_STATE   (global `data_saver`)
 *  - DEVICE_LOCKED      (keyguard / screen state)
 *  - WIFI_STATE         (WifiManager state broadcast)
 *  - NFC_STATE          (NfcAdapter state broadcast)
 *  - LOCATION_STATE     (secure `location_mode`)
 *  - SCREEN_ROTATION_STATE (current display rotation)
 *
 * Each fires the task once per transition into the configured state and runs
 * the exit behavior when the configured side ends, exactly like the other
 * standalone monitors.
 */
@Singleton
class SettingsStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    /** Automations currently in their triggered state (to fire exit on the opposite event). */
    private val activeStates = mutableMapOf<String, Boolean>()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val observer = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            evaluateAll()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            evaluateAll()
        }
    }

    fun initialize() {
        if (registered) return
        registered = true

        val resolver = context.contentResolver
        // Settings keys watched for the state triggers.
        listOf(
            Settings.Global.getUriFor("low_power"),
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            Settings.Global.getUriFor("data_saver"),
            Settings.Secure.getUriFor(Settings.Secure.LOCATION_MODE),
            Settings.System.getUriFor(Settings.System.USER_ROTATION),
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION)
        ).forEach { resolver.registerContentObserver(it, false, observer) }

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
            addAction(Intent.ACTION_DEVICE_STORAGE_OK)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        context.registerReceiver(receiver, filter)

        scope.launch {
            // Fire the missed exit NOW for any task whose triggered condition
            // already ended while the process was down.
            reconcileWithDeviceState()
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        try {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        } catch (_: Throwable) {
            // ignore
        }
    }

    /** Re-reads every state trigger and fires run/exit on transitions. */
    private fun evaluateAll() {
        scope.launch {
            val automations = repository.getAutomations().first()
            automations
                .filter { it.enabled && it.triggers.any { t -> t.type in WATCHED_TRIGGERS } }
                .forEach { automation ->
                    val trigger = automation.triggers.first { it.type in WATCHED_TRIGGERS }
                    val satisfied = runCatching {
                        isSatisfied(trigger.type, trigger.config)
                    }.getOrDefault(false)
                    if (satisfied) {
                        if (activeStates.put(automation.id, true) == null) {
                            activeStore.markActive(SOURCE, automation.id)
                            executionEngine.runAutomation(automation)
                        }
                    } else if (activeStates.remove(automation.id) != null) {
                        activeStore.clearAutomation(SOURCE, automation.id)
                        executionEngine.runExit(automation)
                    }
                }
        }
    }

    private suspend fun reconcileWithDeviceState() {
        val automations = repository.getAutomations().first()
        automations
            .filter { it.enabled && it.triggers.any { t -> t.type in WATCHED_TRIGGERS } }
            .forEach { automation ->
                val trigger = automation.triggers.first { it.type in WATCHED_TRIGGERS }
                val satisfied = isSatisfied(trigger.type, trigger.config)
                val wasActive = activeStates[automation.id] ?: return@forEach
                if (wasActive != satisfied) {
                    activeStates.remove(automation.id)
                    activeStore.clearAutomation(SOURCE, automation.id)
                    executionEngine.runExit(automation)
                }
            }
    }

    /** Evaluates a single state trigger against the live device state. */
    private fun isSatisfied(type: TriggerType, config: Map<String, String>): Boolean {
        val wantOn = (config["state"] ?: "ON") == "ON"
        return when (type) {
            TriggerType.POWER_SAVER -> {
                val on = Settings.Global.getInt(
                    context.contentResolver, "low_power", 0
                ) == 1
                on == wantOn
            }
            TriggerType.BLUETOOTH_STATE -> {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) return false
                val on = adapter.state == BluetoothAdapter.STATE_ON
                on == wantOn
            }
            TriggerType.BRIGHTNESS_LEVEL -> {
                val brightness = Settings.System.getInt(
                    context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128
                )
                val threshold = (config["threshold"] ?: "128").toIntOrNull() ?: 128
                if ((config["direction"] ?: "ABOVE") == "BELOW") {
                    brightness <= threshold
                } else {
                    brightness >= threshold
                }
            }
            TriggerType.STORAGE_LOW -> {
                val thresholdMb = (config["threshold"] ?: "1024").toLongOrNull() ?: 1024L
                val freeMb = freeStorageMb()
                if (freeMb < 0) return false
                if ((config["direction"] ?: "BELOW") == "ABOVE") {
                    freeMb >= thresholdMb
                } else {
                    freeMb <= thresholdMb
                }
            }
            TriggerType.AUTO_ROTATE -> {
                val on = Settings.System.getInt(
                    context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0
                ) == 1
                on == wantOn
            }
            TriggerType.DATA_SAVER_STATE -> {
                val on = Settings.Global.getInt(
                    context.contentResolver, "data_saver", 0
                ) == 1
                on == wantOn
            }
            TriggerType.DEVICE_LOCKED -> {
                val wantLocked = (config["state"] ?: "LOCKED") == "LOCKED"
                val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val screenOn = power?.isInteractive == true
                val locked = !screenOn
                locked == wantLocked
            }
            TriggerType.WIFI_STATE -> {
                val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val on = wifi?.isWifiEnabled == true
                on == wantOn
            }
            TriggerType.NFC_STATE -> {
                val nfc = NfcAdapter.getDefaultAdapter(context)
                val on = nfc?.isEnabled == true
                on == wantOn
            }
            TriggerType.LOCATION_STATE -> {
                val mode = Settings.Secure.getInt(
                    context.contentResolver, Settings.Secure.LOCATION_MODE, 0
                )
                val wantMode = when ((config["mode"] ?: "HIGH").uppercase()) {
                    "OFF" -> 0
                    "SENSORS" -> 1
                    "BATTERY" -> 2
                    else -> 3
                }
                mode == wantMode
            }
            TriggerType.SCREEN_ROTATION_STATE -> {
                val wantPortrait = (config["state"] ?: "PORTRAIT") == "PORTRAIT"
                val rotation = context.resources.configuration.orientation
                val portrait = rotation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                portrait == wantPortrait
            }
            else -> false
        }
    }

    private fun freeStorageMb(): Long {
        return try {
            val stat = android.os.StatFs(context.filesDir.path)
            val freeBytes = stat.availableBytes
            freeBytes / (1024L * 1024L)
        } catch (_: Throwable) {
            -1L
        }
    }

    private companion object {
        const val SOURCE = "settings-state"
        val WATCHED_TRIGGERS = setOf(
            TriggerType.POWER_SAVER,
            TriggerType.BLUETOOTH_STATE,
            TriggerType.BRIGHTNESS_LEVEL,
            TriggerType.STORAGE_LOW,
            TriggerType.AUTO_ROTATE,
            TriggerType.DATA_SAVER_STATE,
            TriggerType.DEVICE_LOCKED,
            TriggerType.WIFI_STATE,
            TriggerType.NFC_STATE,
            TriggerType.LOCATION_STATE,
            TriggerType.SCREEN_ROTATION_STATE
        )
    }
}
