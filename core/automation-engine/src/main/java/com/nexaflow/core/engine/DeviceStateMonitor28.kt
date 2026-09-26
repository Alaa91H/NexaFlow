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
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.TriggerOccurrence
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val runtimeStore: AutomationRuntimeStore,
    private val exitCoordinator: ExitCoordinator,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    /** Serializes callbacks/observers/edit reconciliation for one durable lifecycle. */
    private val evaluationMutex = Mutex()

    @Volatile
    private var lastHdmiPlugged: Boolean? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val observer = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            evaluateAll()
            when (uri) {
                Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT) -> {
                    val timeoutSeconds = runCatching {
                        Settings.System.getLong(
                            context.contentResolver,
                            Settings.System.SCREEN_OFF_TIMEOUT,
                        ) / 1_000L
                    }.getOrNull()
                    fireOneShot(TriggerType.SCREEN_TIMEOUT_CHANGED) { config ->
                        DeviceOneShotTriggerMatcher.matches(
                            type = TriggerType.SCREEN_TIMEOUT_CHANGED,
                            config = config,
                            numericValue = timeoutSeconds,
                        )
                    }
                }

                Settings.System.getUriFor("next_alarm_formatted") -> {
                    val alarmIsSet = runCatching {
                        !Settings.System.getString(
                            context.contentResolver,
                            "next_alarm_formatted",
                        ).isNullOrBlank()
                    }.getOrNull()
                    fireOneShot(TriggerType.ALARM_SET_CHANGED) { config ->
                        DeviceOneShotTriggerMatcher.matches(
                            type = TriggerType.ALARM_SET_CHANGED,
                            config = config,
                            flagValue = alarmIsSet,
                        )
                    }
                }
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_TIMEZONE_CHANGED -> {
                    val zoneId = java.util.TimeZone.getDefault().id
                    fireOneShot(TriggerType.TIMEZONE_CHANGED) { config ->
                        DeviceOneShotTriggerMatcher.matches(
                            type = TriggerType.TIMEZONE_CHANGED,
                            config = config,
                            textValue = zoneId,
                        )
                    }
                }
                NfcAdapter.ACTION_NDEF_DISCOVERED,
                ACTION_TAG_DISCOVERED,
                NfcAdapter.ACTION_TECH_DISCOVERED -> {
                    val tagId = nfcTagId(intent)
                    fireOneShot(TriggerType.NFC_TAG_SCANNED) { config ->
                        DeviceOneShotTriggerMatcher.matches(
                            type = TriggerType.NFC_TAG_SCANNED,
                            config = config,
                            textValue = tagId,
                        )
                    }
                }
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

    private var signalStrengthCallback: Any? = null

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private val legacyPhoneStateListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = evaluateAll()
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val text = runCatching {
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip ?: return@runCatching null
            if (clip.itemCount <= 0) return@runCatching null
            clip.getItemAt(0).coerceToText(context)?.toString()
        }.getOrNull()

        fireOneShot(TriggerType.CLIPBOARD_CHANGED) { config ->
            DeviceOneShotTriggerMatcher.matches(
                type = TriggerType.CLIPBOARD_CHANGED,
                config = config,
                textValue = text,
            )
        }
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
            addAction("android.intent.action.HDMI_PLUGGED")
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            runCatching {
                addAction(NfcAdapter.ACTION_NDEF_DISCOVERED)
                addAction(ACTION_TAG_DISCOVERED)
                addAction(NfcAdapter.ACTION_TECH_DISCOVERED)
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.registerDefaultNetworkCallback(networkCallback)
        }

        runCatching {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephony?.let(::registerModernSignalStrengthCallback)
            } else {
                @Suppress("DEPRECATION")
                telephony?.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
            }
        }

        runCatching {
            mainHandler.post {
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clip?.addPrimaryClipChangedListener(clipListener)
            }
        }

        scope.launch {
            // Evaluate every enabled task against the CURRENT device state so a
            // freshly enabled task whose state condition already holds fires
            // right away, and a task disabled while its condition still holds
            // runs its exit behavior immediately.
            reconcileAutomations()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephony?.let(::unregisterModernSignalStrengthCallback)
            } else {
                @Suppress("DEPRECATION")
                telephony?.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
        }
        runCatching {
            mainHandler.post {
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clip?.removePrimaryClipChangedListener(clipListener)
            }
        }
    }

    /** Re-reads every state trigger and fires run/exit on transitions. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerModernSignalStrengthCallback(telephony: TelephonyManager) {
        val callback = ModernSignalStrengthCallback(::evaluateAll)
        signalStrengthCallback = callback
        telephony.registerTelephonyCallback(context.mainExecutor, callback)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun unregisterModernSignalStrengthCallback(telephony: TelephonyManager) {
        val callback = signalStrengthCallback as? ModernSignalStrengthCallback ?: return
        telephony.unregisterTelephonyCallback(callback)
        signalStrengthCallback = null
    }

    private fun evaluateAll() {
        scope.launch { reconcileStateTriggers() }
    }

    /**
     * Fires only the configured one-shot conditions that actually match this
     * concrete event payload. A broad event type is never enough evidence for a
     * filtered trigger.
     */
    private fun fireOneShot(
        type: TriggerType,
        matchesConfig: (Map<String, String>) -> Boolean,
    ) {
        scope.launch {
            val automations = repository.getAutomations().first()
            val now = System.currentTimeMillis()
            automations
                .filter { it.enabled && it.triggers.any { trigger ->
                    trigger.type == type && matchesConfig(trigger.config)
                } }
                .forEach { automation ->
                    val matchedTriggerIndices = automation.triggers.mapIndexedNotNull { index, trigger ->
                        index.takeIf {
                            trigger.type == type && matchesConfig(trigger.config)
                        }
                    }.toSet()
                    if (matchedTriggerIndices.isEmpty()) return@forEach

                    // These signals are momentary events, not a durable state
                    // with an opposite callback. Close their lifecycle after the
                    // main chain so per-action end behavior is never stranded.
                    executionEngine.runAutomation(
                        automation = automation,
                        completeExitOnFinish = true,
                        triggerOccurrence = TriggerOccurrence(
                            matchedTriggerIndices = matchedTriggerIndices,
                            occurredAtEpochMs = now,
                            sourceId = "device-event:" + type.name.lowercase(),
                        ),
                    )
                }
        }
    }

    /**
     * Full re-evaluation of every state trigger. The runtime ledger is
     * authoritative; unreadable hardware/provider state is UNKNOWN and never
     * fabricated into an exit.
     */
    fun reconcileAutomations() {
        scope.launch { reconcileStateTriggers() }
    }

    internal suspend fun reconcileStateTriggers() = evaluationMutex.withLock {
        val automations = repository.getAutomations().first()
        val byId = automations.associateBy { it.id }

        rearmFromLedger(byId)

        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = byId[state.automationId]
                when {
                    automation == null -> clearLegacyState(state.automationId)
                    !automation.enabled ||
                        automation.triggers.none { it.type in STATE_TRIGGERS } -> {
                        requestExit(
                            automation = automation,
                            reason = ExitReason.AUTOMATION_DISABLED,
                            occurrenceId = state.occurrenceId
                        )
                    }
                    else -> markLegacyActive(state)
                }
            }

        automations
            .filter { it.enabled && it.triggers.any { trigger -> trigger.type in STATE_TRIGGERS } }
            .forEach { automation ->
                val trigger = automation.triggers.first { it.type in STATE_TRIGGERS }
                val current = runtimeStore.current(automation.id)
                val satisfied = runCatching {
                    isSatisfied(trigger.type, trigger.config)
                }.getOrNull()

                when (satisfied) {
                    true -> when {
                        current?.source == SOURCE -> markLegacyActive(current)
                        current != null -> clearLegacyState(automation.id)
                        else -> activate(automation, trigger.type)
                    }

                    false -> {
                        if (current?.source == SOURCE) {
                            requestExit(
                                automation = automation,
                                reason = ExitReason.TRIGGER_FALSE,
                                occurrenceId = current.occurrenceId
                            )
                        } else if (current == null) {
                            clearLegacyState(automation.id)
                        }
                    }

                    null -> {
                        if (current?.source == SOURCE) markLegacyActive(current)
                        else if (current == null) clearLegacyState(automation.id)
                    }
                }
            }
    }

    private suspend fun rearmFromLedger(automations: Map<String, Automation>) {
        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                if (automations[state.automationId] != null) {
                    markLegacyActive(state)
                } else {
                    // Definition gone: keep durable evidence for recovery and
                    // drop only the old compatibility marker.
                    clearLegacyState(state.automationId)
                }
            }

        activeStore.activeKeys(SOURCE).forEach { key ->
            val automationId = key.substringBefore('|')
            val automation = automations[automationId]
            if (automation == null) {
                clearLegacyState(automationId)
                return@forEach
            }

            if (runtimeStore.current(automationId) == null) {
                val type = automation.triggers.firstOrNull { it.type in STATE_TRIGGERS }?.type
                    ?: return@forEach
                runtimeStore.activateStrict(
                    AutomationRuntimeState(
                        automationId = automationId,
                        occurrenceId = "legacy:$SOURCE:$automationId:${UUID.randomUUID()}",
                        source = SOURCE,
                        sourceKey = "$automationId|${type.name}",
                        lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                        activatedAt = System.currentTimeMillis()
                    )
                )
            }

            val state = runtimeStore.current(automationId)
            if (state?.source == SOURCE) {
                markLegacyActive(state)
                if (!automation.enabled ||
                    automation.triggers.none { it.type in STATE_TRIGGERS }
                ) {
                    requestExit(
                        automation = automation,
                        reason = ExitReason.AUTOMATION_DISABLED,
                        occurrenceId = state.occurrenceId
                    )
                }
            } else {
                clearLegacyState(automationId)
            }
        }
    }

    private suspend fun activate(automation: Automation, type: TriggerType) {
        val occurrenceId = "device-state:${automation.id}:${UUID.randomUUID()}"
        val sourceKey = "${automation.id}|${type.name}"
        val now = System.currentTimeMillis()
        val matchedTriggerIndices = automation.triggers.mapIndexedNotNull { index, trigger ->
            index.takeIf { trigger.type == type }
        }.toSet()

        executionEngine.runAutomation(
            automation = automation,
            lifecycleContext = AutomationLifecycleContext(
                occurrenceId = occurrenceId,
                source = SOURCE,
                sourceKey = sourceKey
            ),
            triggerOccurrence = TriggerOccurrence(
                matchedTriggerIndices = matchedTriggerIndices,
                occurredAtEpochMs = now,
                sourceId = SOURCE,
                eventId = "device-state:${type.name.lowercase()}"
            )
        )

        runtimeStore.current(automation.id)
            ?.takeIf { it.source == SOURCE && it.occurrenceId == occurrenceId }
            ?.let { markLegacyActive(it) }
            ?: clearLegacyState(automation.id)
    }

    private suspend fun requestExit(
        automation: Automation,
        reason: ExitReason,
        occurrenceId: String
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
            ExitCoordinatorResult.StaleOccurrence -> clearLegacyState(automation.id)
            ExitCoordinatorResult.AlreadyInProgress,
            is ExitCoordinatorResult.RecoveryRequired -> {
                runtimeStore.current(automation.id)
                    ?.takeIf { it.source == SOURCE }
                    ?.let { markLegacyActive(it) }
            }
        }
    }

    private suspend fun markLegacyActive(state: AutomationRuntimeState) {
        activeStore.markActive(SOURCE, state.sourceKey)
    }

    private suspend fun clearLegacyState(automationId: String) {
        activeStore.clearAutomation(SOURCE, automationId)
    }

    /** Evaluates a single state trigger against the live device state. */
    internal fun isSatisfied(type: TriggerType, config: Map<String, String>): Boolean? {
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
                val rssi = currentWifiRssi() ?: return null
                val level = wifiSignalLevel(rssi)
                val threshold = (config["threshold"] ?: "3").toIntOrNull() ?: 3
                if ((config["direction"] ?: "ABOVE") == "BELOW") level <= threshold else level >= threshold
            }
            TriggerType.CELL_SIGNAL_STRENGTH -> {
                val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    ?: return null
                val level = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        telephony.signalStrength?.level
                    } else {
                        null
                    }
                }.getOrNull() ?: return null
                val threshold = (config["threshold"] ?: "3").toIntOrNull() ?: 3
                if ((config["direction"] ?: "ABOVE") == "BELOW") level <= threshold else level >= threshold
            }
            TriggerType.BATTERY_TEMPERATURE -> {
                // BATTERY_PROPERTY_TEMPERATURE is @SystemApi; the sticky
                // battery intent's EXTRA_TEMPERATURE is the public equivalent
                // (tenths of a degree Celsius).
                val intent = context.registerReceiver(
                    null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                ) ?: return null
                val rawTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                if (rawTemperature == Int.MIN_VALUE) return null
                val celsius = rawTemperature / 10f
                val threshold = (config["threshold"] ?: "40").toFloatOrNull() ?: 40f
                if ((config["direction"] ?: "ABOVE") == "BELOW") {
                    celsius <= threshold
                } else {
                    celsius >= threshold
                }
            }
            TriggerType.USB_CONNECTED -> {
                val plugged = pluggedType() ?: return null
                (plugged == BatteryManager.BATTERY_PLUGGED_USB) == wantOn
            }
            TriggerType.HDMI_CONNECTED -> {
                lastHdmiPlugged?.let { it == wantOn }
            }
            TriggerType.ETHERNET_CONNECTED -> {
                hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)?.let { it == wantOn }
            }
            TriggerType.VPN_CONNECTED -> {
                hasTransport(NetworkCapabilities.TRANSPORT_VPN)?.let { it == wantOn }
            }
            else -> null
        }
    }

    private fun nfcTagId(intent: Intent): String? {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, android.nfc.Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) as? android.nfc.Tag
        } ?: return null

        return tag.id?.joinToString(separator = "") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        }
    }

    private fun currentWifiRssi(): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val wifiInfo = connectivity
                ?.getNetworkCapabilities(connectivity.activeNetwork)
                ?.transportInfo as? WifiInfo
            if (wifiInfo != null) return wifiInfo.rssi
        }
        @Suppress("DEPRECATION")
        return (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.connectionInfo
            ?.rssi
    }

    /** Mirrors the five-level legacy scale without relying on a deprecated platform helper. */
    private fun wifiSignalLevel(rssi: Int): Int = when {
        rssi <= MIN_WIFI_RSSI -> 0
        rssi >= MAX_WIFI_RSSI -> WIFI_SIGNAL_LEVELS - 1
        else -> ((rssi - MIN_WIFI_RSSI) * (WIFI_SIGNAL_LEVELS - 1)) /
            (MAX_WIFI_RSSI - MIN_WIFI_RSSI)
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
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(transport) == true
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private class ModernSignalStrengthCallback(
        private val onSignalStrengthChanged: () -> Unit
    ) : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = onSignalStrengthChanged()
    }

    private companion object {
        const val SOURCE = "device_state-28"
        const val ACTION_TAG_DISCOVERED = "android.nfc.action.TAG_DISCOVERED"
        const val MIN_WIFI_RSSI = -100
        const val MAX_WIFI_RSSI = -55
        const val WIFI_SIGNAL_LEVELS = 5
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
