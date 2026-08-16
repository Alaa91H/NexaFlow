package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
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
 * Consolidated v3.28 monitor covering the second wave of device-state and
 * one-shot triggers:
 *
 *  State triggers (run/exit on transitions):
 *   - DND_STATE              (global `zen_mode`)
 *   - STAY_AWAKE_STATE       (global `stay_on_while_plugged_in`)
 *   - AUTO_BRIGHTNESS_STATE  (system `screen_brightness_mode`)
 *   - DATA_ROAMING_STATE     (global `data_roaming`)
 *   - WIFI_SIGNAL_STRENGTH   (WifiManager RSSI threshold crossing)
 *   - CELL_SIGNAL_STRENGTH   (TelephonyManager level threshold crossing)
 *   - BATTERY_TEMPERATURE    (BatteryManager temperature threshold crossing)
 *   - USB_CONNECTED          (USB host/charger plug state)
 *   - HDMI_CONNECTED         (HDMI plug state)
 *   - ETHERNET_CONNECTED     (active ethernet transport)
 *   - VPN_CONNECTED          (active VPN transport)
 *
 *  One-shot event triggers (fire on every matching event):
 *   - TIMEZONE_CHANGED       (ACTION_TIMEZONE_CHANGED)
 *   - BOOT_COMPLETED         (ACTION_BOOT_COMPLETED)
 *   - NFC_TAG_SCANNED        (NFC tag discovery intents)
 *   - CLIPBOARD_CHANGED      (primary clipboard change)
 *   - SCREEN_TIMEOUT_CHANGED (system `screen_off_timeout` change)
 *   - ALARM_SET_CHANGED      (system `next_alarm_formatted` change)
 *
 * Everything is best-effort and ROM-agnostic: every read goes through
 * public Settings / service APIs, and every failure is swallowed.
 */
@Singleton
class DeviceStateMonitor28 @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    /** Automations currently in their triggered state (to fire exit on the opposite side). */
    private val activeStates = mutableMapOf<String, Boolean>()

    @Volatile
    private var lastHdmiPlugged = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val observer = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            evaluateAll()
            fireOneShot(TriggerType.SCREEN_TIMEOUT_CHANGED)
            fireOneShot(TriggerType.ALARM_SET_CHANGED)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_TIMEZONE_CHANGED -> fireOneShot(TriggerType.TIMEZONE_CHANGED)
                Intent.ACTION_BOOT_COMPLETED -> fireOneShot(TriggerType.BOOT_COMPLETED)
                NfcAdapter.ACTION_NDEF_DISCOVERED,
                NfcAdapter.ACTION_TAG_DISCOVERED,
                NfcAdapter.ACTION_TECH_DISCOVERED -> fireOneShot(TriggerType.NFC_TAG_SCANNED)
                "android.intent.action.HDMI_PLUGGED" -> {
                    lastHdmiPlugged = intent.getBooleanExtra("state", false)
                    evaluateAll()
                }
                else -> evaluateAll()
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = evaluateAll()
        override fun onLost(network: Network) = evaluateAll()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = evaluateAll()
    }

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            evaluateAll()
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        fireOneShot(TriggerType.CLIPBOARD_CHANGED)
    }

    fun initialize() {
        if (registered) return
        registered = true

        val resolver = context.contentResolver
        listOf(
            Settings.Global.getUriFor("zen_mode"),
            Settings.Global.getUriFor("stay_on_while_plugged_in"),
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            Settings.Global.getUriFor("data_roaming"),
            Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT),
            Settings.System.getUriFor("next_alarm_formatted")
        ).forEach { resolver.registerContentObserver(it, false, observer) }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BOOT_COMPLETED)
            addAction("android.intent.action.HDMI_PLUGGED")
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            runCatching {
                addAction(NfcAdapter.ACTION_NDEF_DISCOVERED)
                addAction(NfcAdapter.ACTION_TAG_DISCOVERED)
                addAction(NfcAdapter.ACTION_TECH_DISCOVERED)
            }
        }
        runCatching { context.registerReceiver(receiver, filter) }

        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.registerDefaultNetworkCallback(networkCallback)
        }

        runCatching {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            telephony?.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        }

        runCatching {
            mainHandler.post {
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clip?.addPrimaryClipChangedListener(clipListener)
            }
        }

        scope.launch {
            reconcileWithDeviceState()
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(networkCallback)
        }
        runCatching {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            telephony?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        }
        runCatching {
            mainHandler.post {
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clip?.removePrimaryClipChangedListener(clipListener)
            }
        }
    }

    /** Re-reads every state trigger and fires run/exit on transitions. */
    private fun evaluateAll() {
        scope.launch {
            val automations = repository.getAutomations().first()
            automations
                .filter { it.enabled && it.triggers.any { t -> t.type in STATE_TRIGGERS } }
                .forEach { automation ->
                    val trigger = automation.triggers.first { it.type in STATE_TRIGGERS }
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

    /** Fires automations whose trigger is the given one-shot event type. */
    private fun fireOneShot(type: TriggerType) {
        scope.launch {
            val automations = repository.getAutomations().first()
            automations
                .filter { it.enabled && it.triggers.any { t -> t.type == type } }
                .forEach { automation ->
                    executionEngine.runAutomation(automation)
                }
        }
    }

    private suspend fun reconcileWithDeviceState() {
        val automations = repository.getAutomations().first()
        automations
            .filter { it.enabled && it.triggers.any { t -> t.type in STATE_TRIGGERS } }
            .forEach { automation ->
                val trigger = automation.triggers.first { it.type in STATE_TRIGGERS }
                val satisfied = runCatching {
                    isSatisfied(trigger.type, trigger.config)
                }.getOrDefault(false)
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
            TriggerType.DND_STATE -> {
                val zen = Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
                (zen != 0) == wantOn
            }
            TriggerType.STAY_AWAKE_STATE -> {
                val stay = Settings.Global.getInt(
                    context.contentResolver, "stay_on_while_plugged_in", 0
                )
                (stay != 0) == wantOn
            }
            TriggerType.AUTO_BRIGHTNESS_STATE -> {
                val mode = Settings.System.getInt(
                    context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0
                )
                (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) == wantOn
            }
            TriggerType.DATA_ROAMING_STATE -> {
                val roaming = Settings.Global.getInt(
                    context.contentResolver, "data_roaming", 0
                ) != 0
                roaming == wantOn
            }
            TriggerType.WIFI_SIGNAL_STRENGTH -> {
                val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
                val info = wifi.connectionInfo ?: return false
                val level = WifiManager.calculateSignalLevel(info.rssi, 5)
                val threshold = (config["threshold"] ?: "3").toIntOrNull() ?: 3
                if ((config["direction"] ?: "ABOVE") == "BELOW") level <= threshold else level >= threshold
            }
            TriggerType.CELL_SIGNAL_STRENGTH -> {
                val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    ?: return false
                val level = runCatching {
                    telephony.signalStrength?.level ?: 0
                }.getOrDefault(0)
                val threshold = (config["threshold"] ?: "3").toIntOrNull() ?: 3
                if ((config["direction"] ?: "ABOVE") == "BELOW") level <= threshold else level >= threshold
            }
            TriggerType.BATTERY_TEMPERATURE -> {
                // BATTERY_PROPERTY_TEMPERATURE is @SystemApi; the sticky
                // battery intent's EXTRA_TEMPERATURE is the public equivalent
                // (tenths of a degree Celsius).
                val intent = context.registerReceiver(
                    null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                ) ?: return false
                val celsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f
                if (celsius < 0) return false
                val threshold = (config["threshold"] ?: "40").toFloatOrNull() ?: 40f
                if ((config["direction"] ?: "ABOVE") == "BELOW") {
                    celsius <= threshold
                } else {
                    celsius >= threshold
                }
            }
            TriggerType.USB_CONNECTED -> {
                val plugged = pluggedType()
                (plugged == BatteryManager.BATTERY_PLUGGED_USB) == wantOn
            }
            TriggerType.HDMI_CONNECTED -> {
                lastHdmiPlugged == wantOn
            }
            TriggerType.ETHERNET_CONNECTED -> {
                hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == wantOn
            }
            TriggerType.VPN_CONNECTED -> {
                hasTransport(NetworkCapabilities.TRANSPORT_VPN) == wantOn
            }
            else -> false
        }
    }

    private fun pluggedType(): Int {
        val intent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return 0
        return intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    }

    private fun hasTransport(transport: Int): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return runCatching {
            cm.allNetworks.any { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@any false
                caps.hasTransport(transport)
            }
        }.getOrDefault(false)
    }

    private companion object {
        const val SOURCE = "device-state-28"
        val STATE_TRIGGERS = setOf(
            TriggerType.DND_STATE,
            TriggerType.STAY_AWAKE_STATE,
            TriggerType.AUTO_BRIGHTNESS_STATE,
            TriggerType.DATA_ROAMING_STATE,
            TriggerType.WIFI_SIGNAL_STRENGTH,
            TriggerType.CELL_SIGNAL_STRENGTH,
            TriggerType.BATTERY_TEMPERATURE,
            TriggerType.USB_CONNECTED,
            TriggerType.HDMI_CONNECTED,
            TriggerType.ETHERNET_CONNECTED,
            TriggerType.VPN_CONNECTED
        )
    }
}
