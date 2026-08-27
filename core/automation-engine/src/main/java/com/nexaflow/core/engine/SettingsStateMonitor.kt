package com.nexaflow.core.engine

import android.app.KeyguardManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Consolidated state-trigger monitor for settings and radio state.
 *
 * Platform reads use a tri-state result. In particular, an unavailable service,
 * missing hardware, transient permission loss, or failed settings read is
 * [ConditionState.UNKNOWN], not a confirmed condition end. Stateful ownership
 * lives in [AutomationRuntimeStore]; [ActiveTriggerStore] is retained only as
 * a backward-compatible bookkeeping mirror and is never cleared before a
 * coordinated exit has completed or is proven unnecessary.
 */
@Singleton
class SettingsStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val exitCoordinator: ExitCoordinator,
    private val runtimeStore: AutomationRuntimeStore,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    /** Active source key by automation id, rearmed from the durable lifecycle ledger. */
    private val activeStates = ConcurrentHashMap<String, String>()
    private val lastRunAt = ConcurrentHashMap<String, Long>()
    private val evaluationMutex = Mutex()
    private var lastKnownAutomations: Map<String, Automation> = emptyMap()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val observer = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) = evaluateAll()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) = evaluateAll()
    }

    fun initialize() {
        if (registered) return
        registered = true

        val resolver = context.contentResolver
        listOf(
            Settings.Global.getUriFor("low_power"),
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            Settings.Global.getUriFor("data_saver"),
            Settings.System.getUriFor(Settings.System.USER_ROTATION)
        ).forEach { resolver.registerContentObserver(it, false, observer) }

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(ACTION_DEVICE_STORAGE_LOW)
            addAction(ACTION_DEVICE_STORAGE_OK)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        scope.launch {
            rearmFromLedger()
            if (!registered) return@launch
            repository.getAutomations().collect(::evaluateAutomations)
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        }
        // Durable state survives stop/restart. Only in-memory callback hints are
        // cleared; rearmFromLedger restores them before evaluation resumes.
        activeStates.clear()
        lastRunAt.clear()
    }

    /** Queues a coherent device snapshot after a system callback. */
    private fun evaluateAll() {
        if (!registered) return
        scope.launch {
            val automations = repository.getAutomations().first()
            evaluateAutomations(automations)
        }
    }

    /**
     * Serializes state transitions across ContentObserver, broadcast, and
     * repository-update entry points. Durable activation/exit remains the
     * source of truth across process death; serialization only avoids needless
     * concurrent evaluation in a running process.
     */
    private suspend fun evaluateAutomations(automations: List<Automation>) {
        evaluationMutex.withLock {
            val currentById = automations.associateBy { it.id }
            // A removed definition can be safely exited only while this monitor
            // retains the previous immutable action definition. Never clear the
            // runtime occurrence merely because the current repository list no
            // longer contains it.
            lastKnownAutomations
                .filterKeys { it !in currentById }
                .values
                .forEach { automation -> requestDisableExit(automation) }

            // A disabled task or an edit that removes its watched trigger must
            // release its existing stateful lifecycle before its source mirror
            // is dropped.
            automations
                .filter { automation ->
                    val runtime = runtimeStore.current(automation.id)
                    runtime?.source == SOURCE &&
                        (!automation.enabled || automation.triggers.none { it.type in WATCHED_TRIGGERS })
                }
                .forEach(::requestDisableExit)

            automations
                .filter { automation ->
                    automation.enabled && automation.triggers.any { it.type in WATCHED_TRIGGERS }
                }
                .forEach { automation ->
                    val trigger = automation.triggers.first { it.type in WATCHED_TRIGGERS }
                    evaluateAutomation(automation, trigger)
                }
            lastKnownAutomations = currentById
        }
    }

    private suspend fun evaluateAutomation(automation: Automation, trigger: Trigger) {
        when (evaluate(trigger.type, trigger.config)) {
            ConditionState.SATISFIED -> activateIfNeeded(automation, trigger)
            ConditionState.NOT_SATISFIED -> requestConditionExit(automation)
            ConditionState.UNKNOWN -> Unit
        }
    }

    private suspend fun activateIfNeeded(automation: Automation, trigger: Trigger) {
        val existing = runtimeStore.current(automation.id)
        if (existing?.source == SOURCE) {
            activeStates[automation.id] = existing.sourceKey
            activeStore.markActive(SOURCE, existing.sourceKey)
            return
        }
        if (existing != null) {
            // A different stateful source owns this automation's exit. Do not
            // overwrite it or apply duplicate main actions from this source.
            activeStates.remove(automation.id)
            activeStore.clearAutomation(SOURCE, automation.id)
            return
        }
        val now = System.currentTimeMillis()
        val last = lastRunAt[automation.id] ?: 0L
        if (now - last <= automation.cooldownMillis) return

        val sourceKey = sourceKey(automation.id, trigger)
        val occurrenceId = "settings:${automation.id}:${UUID.randomUUID()}"
        lastRunAt[automation.id] = now
        executionEngine.runAutomation(
            automation = automation,
            lifecycleContext = AutomationLifecycleContext(
                occurrenceId = occurrenceId,
                source = SOURCE,
                sourceKey = sourceKey
            )
        )
        val admitted = runtimeStore.current(automation.id)?.let { state ->
            state.occurrenceId == occurrenceId && state.source == SOURCE
        } == true
        if (admitted) {
            activeStates[automation.id] = sourceKey
            activeStore.markActive(SOURCE, sourceKey)
            // A system setting can flip while the main actions are executing.
            // Re-evaluate through the durable owner, without relying on a later
            // broadcast to deliver the required exit.
            evaluateAutomation(automation, trigger)
        }
    }

    private suspend fun requestConditionExit(automation: Automation) {
        val state = runtimeStore.current(automation.id)
        if (state?.source != SOURCE) {
            if (state == null) clearLegacyState(automation.id)
            return
        }
        when (
            exitCoordinator.requestExit(
                automation = automation,
                reason = ExitReason.SYSTEM_STATE_CHANGED,
                occurrenceId = state.occurrenceId
            )
        ) {
            is ExitCoordinatorResult.Executed,
            ExitCoordinatorResult.NotActive,
            ExitCoordinatorResult.StaleOccurrence -> clearLegacyState(automation.id)
            ExitCoordinatorResult.AlreadyInProgress,
            is ExitCoordinatorResult.RecoveryRequired -> Unit
        }
    }

    private suspend fun requestDisableExit(automation: Automation) {
        val state = runtimeStore.current(automation.id)
        if (state?.source != SOURCE) {
            if (state == null) clearLegacyState(automation.id)
            return
        }
        when (
            exitCoordinator.requestExit(
                automation = automation,
                reason = ExitReason.AUTOMATION_DISABLED,
                occurrenceId = state.occurrenceId
            )
        ) {
            is ExitCoordinatorResult.Executed,
            ExitCoordinatorResult.NotActive,
            ExitCoordinatorResult.StaleOccurrence -> clearLegacyState(automation.id)
            ExitCoordinatorResult.AlreadyInProgress,
            is ExitCoordinatorResult.RecoveryRequired -> Unit
        }
    }

    private suspend fun clearLegacyState(automationId: String) {
        activeStates.remove(automationId)
        activeStore.clearAutomation(SOURCE, automationId)
    }

    /**
     * Rehydrates durable states first, then upgrades only legacy active keys.
     * Upgrade keys have no original-state snapshot, so they can run configured
     * exit actions but never invent a state restoration value.
     */
    private suspend fun rearmFromLedger() {
        val automations = repository.getAutomations().first().associateBy { it.id }
        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = automations[state.automationId]
                if (automation?.enabled == true && automation.triggers.any { it.type in WATCHED_TRIGGERS }) {
                    activeStates[state.automationId] = state.sourceKey
                    activeStore.markActive(SOURCE, state.sourceKey)
                } else if (automation != null) {
                    requestDisableExit(automation)
                }
            }

        activeStore.activeKeys(SOURCE).forEach { key ->
            val automationId = key.substringBefore('|')
            val automation = automations[automationId]
            if (automation == null) {
                activeStore.clearAutomation(SOURCE, automationId)
                return@forEach
            }
            if (runtimeStore.current(automationId) == null) {
                runtimeStore.activate(
                    AutomationRuntimeState(
                        automationId = automationId,
                        occurrenceId = "legacy:$SOURCE:$automationId:${UUID.randomUUID()}",
                        source = SOURCE,
                        sourceKey = key.ifBlank { "$automationId|legacy" },
                        lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                        activatedAt = System.currentTimeMillis()
                    )
                )
            }
            val state = runtimeStore.current(automationId)
            if (state?.source == SOURCE) {
                activeStates[automationId] = state.sourceKey
                if (!automation.enabled || automation.triggers.none { it.type in WATCHED_TRIGGERS }) {
                    requestDisableExit(automation)
                }
            }
        }
    }

    /** Evaluates a trigger without converting an unreadable platform state into false. */
    private fun evaluate(type: TriggerType, config: Map<String, String>): ConditionState {
        val wantOn = (config["state"] ?: "ON") == "ON"
        return when (type) {
            TriggerType.POWER_SAVER -> matched(readBoolean {
                Settings.Global.getInt(context.contentResolver, "low_power") == 1
            }, wantOn)
            TriggerType.BLUETOOTH_STATE -> {
                val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                    ?.adapter ?: return ConditionState.UNKNOWN
                val state = runCatching { adapter.state }.getOrNull()
                when (state) {
                    BluetoothAdapter.STATE_ON -> matched(true, wantOn)
                    BluetoothAdapter.STATE_OFF -> matched(false, wantOn)
                    else -> ConditionState.UNKNOWN
                }
            }
            TriggerType.BRIGHTNESS_LEVEL -> {
                val threshold = (config["threshold"] ?: "128").toIntOrNull() ?: 128
                val brightness = runCatching {
                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                }.getOrNull() ?: return ConditionState.UNKNOWN
                if ((config["direction"] ?: "ABOVE") == "BELOW") {
                    if (brightness <= threshold) ConditionState.SATISFIED else ConditionState.NOT_SATISFIED
                } else {
                    if (brightness >= threshold) ConditionState.SATISFIED else ConditionState.NOT_SATISFIED
                }
            }
            TriggerType.STORAGE_LOW -> {
                val thresholdMb = (config["threshold"] ?: "1024").toLongOrNull() ?: 1024L
                val freeMb = freeStorageMb() ?: return ConditionState.UNKNOWN
                val matched = if ((config["direction"] ?: "BELOW") == "ABOVE") {
                    freeMb >= thresholdMb
                } else {
                    freeMb <= thresholdMb
                }
                if (matched) ConditionState.SATISFIED else ConditionState.NOT_SATISFIED
            }
            TriggerType.AUTO_ROTATE -> matched(readBoolean {
                Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
            }, wantOn)
            TriggerType.DATA_SAVER_STATE -> matched(readBoolean {
                Settings.Global.getInt(context.contentResolver, "data_saver") == 1
            }, wantOn)
            TriggerType.DEVICE_LOCKED -> {
                val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    ?: return ConditionState.UNKNOWN
                val locked = runCatching { keyguard.isKeyguardLocked }.getOrNull()
                    ?: return ConditionState.UNKNOWN
                val wantLocked = (config["state"] ?: "LOCKED") == "LOCKED"
                matched(locked, wantLocked)
            }
            TriggerType.WIFI_STATE -> {
                val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    ?: return ConditionState.UNKNOWN
                matched(runCatching { wifi.isWifiEnabled }.getOrNull(), wantOn)
            }
            TriggerType.NFC_STATE -> {
                val nfc = NfcAdapter.getDefaultAdapter(context) ?: return ConditionState.UNKNOWN
                matched(runCatching { nfc.isEnabled }.getOrNull(), wantOn)
            }
            TriggerType.LOCATION_STATE -> {
                val actualMode = LocationAccess.currentLocationModeOrNull(context)
                    ?: return ConditionState.UNKNOWN
                val wantedMode = when ((config["mode"] ?: "HIGH").uppercase()) {
                    "OFF" -> LocationAccess.MODE_OFF
                    "SENSORS" -> LocationAccess.MODE_SENSORS_ONLY
                    "BATTERY" -> LocationAccess.MODE_BATTERY_SAVING
                    else -> LocationAccess.MODE_HIGH_ACCURACY
                }
                if (actualMode == wantedMode) ConditionState.SATISFIED else ConditionState.NOT_SATISFIED
            }
            TriggerType.SCREEN_ROTATION_STATE -> {
                val wantPortrait = (config["state"] ?: "PORTRAIT") == "PORTRAIT"
                val orientation = context.resources.configuration.orientation
                val portrait = orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                matched(portrait, wantPortrait)
            }
            else -> ConditionState.UNKNOWN
        }
    }

    private fun readBoolean(read: () -> Boolean): Boolean? = runCatching(read).getOrNull()

    private fun matched(actual: Boolean?, wanted: Boolean): ConditionState = when (actual) {
        null -> ConditionState.UNKNOWN
        wanted -> ConditionState.SATISFIED
        else -> ConditionState.NOT_SATISFIED
    }

    private fun freeStorageMb(): Long? = runCatching {
        val stat = android.os.StatFs(context.filesDir.path)
        stat.availableBytes / (1024L * 1024L)
    }.getOrNull()

    private fun sourceKey(automationId: String, trigger: Trigger): String =
        "$automationId|${trigger.type.name}|${trigger.config["state"] ?: trigger.config["mode"] ?: "STATE"}"

    private enum class ConditionState {
        SATISFIED,
        NOT_SATISFIED,
        UNKNOWN
    }

    private companion object {
        const val SOURCE = "settings-state"
        const val ACTION_DEVICE_STORAGE_LOW = "android.intent.action.DEVICE_STORAGE_LOW"
        const val ACTION_DEVICE_STORAGE_OK = "android.intent.action.DEVICE_STORAGE_OK"
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
