package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
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

@Singleton
class DeviceEventMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    private val lastRunAt = mutableMapOf<String, Long>()
    /** Automations currently in their triggered state (to fire exit on the opposite event). */
    private val activeStates = mutableMapOf<String, String>()

    /** The event that ends the "active" phase of each device event. */
    private val oppositeEvent = mapOf(
        "SCREEN_ON" to "SCREEN_OFF",
        "SCREEN_OFF" to "SCREEN_ON",
        "POWER_CONNECTED" to "POWER_DISCONNECTED",
        "POWER_DISCONNECTED" to "POWER_CONNECTED",
        "HEADSET_CONNECTED" to "HEADSET_DISCONNECTED",
        "HEADSET_DISCONNECTED" to "HEADSET_CONNECTED"
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val event = when (intent.action) {
                Intent.ACTION_SCREEN_ON -> "SCREEN_ON"
                Intent.ACTION_SCREEN_OFF -> "SCREEN_OFF"
                Intent.ACTION_POWER_CONNECTED -> "POWER_CONNECTED"
                Intent.ACTION_POWER_DISCONNECTED -> "POWER_DISCONNECTED"
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    if (state == 1) "HEADSET_CONNECTED" else "HEADSET_DISCONNECTED"
                }
                else -> return
            }
            handleEvent(event)
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        context.registerReceiver(receiver, filter)
        scope.launch {
            // Re-arm the in-memory active set from the durable ledger BEFORE
            // the next broadcast is evaluated: a task that was triggered before
            // a process/service restart must still fire its exit behavior when
            // the opposite event arrives.
            rearmFromLedger()
            // Fire the missed exit NOW for any task whose triggered condition
            // already ended while the process was down (no broadcast will come
            // until the state changes again, which may be long after the end).
            reconcileWithDeviceState()
        }
    }

    /**
     * Restores the durable active keys into the in-memory map. Keys carry the
     * triggered event (`id|SCREEN_ON`), so the restored entry matches the exit
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
                activeStates[id] = key.substringAfter('|', "SCREEN_ON")
            } else {
                activeStore.clearAutomation(SOURCE, id)
            }
        }
    }

    /**
     * Re-reads the CURRENT device state (screen, power, headset) and fires the
     * exit behavior for any task whose triggered condition has already ended
     * while the process was down. Broadcasts only fire on state *changes*, so
     * without this pass a task whose condition ended during downtime would
     * never run its exit until the state flipped again.
     */
    private suspend fun reconcileWithDeviceState() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val automations = repository.getAutomations().first()
        automations
            .filter {
                it.enabled && it.triggers.any { t -> t.type == TriggerType.DEVICE || t.type == TriggerType.HEADPHONE }
            }
            .forEach { automation ->
                val activeEvent = activeStates[automation.id] ?: return@forEach
                // The opposite event has already happened while we were down.
                val ended = when (activeEvent) {
                    "SCREEN_ON" -> !powerManager.isInteractive
                    "SCREEN_OFF" -> powerManager.isInteractive
                    "POWER_CONNECTED" -> !batteryManager.isCharging
                    "POWER_DISCONNECTED" -> batteryManager.isCharging
                    "HEADSET_CONNECTED" -> !audioManager.isWiredHeadsetOn
                    "HEADSET_DISCONNECTED" -> audioManager.isWiredHeadsetOn
                    else -> false
                }
                if (ended) {
                    activeStates.remove(automation.id)
                    activeStore.clearAutomation(SOURCE, automation.id)
                    executionEngine.runExit(automation)
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

    private fun handleEvent(event: String) {
        scope.launch {
            val automations = repository.getAutomations().first()
            val now = System.currentTimeMillis()
            automations
                .filter { automation ->
                    automation.enabled && automation.triggers.any {
                        it.type == TriggerType.DEVICE || it.type == TriggerType.HEADPHONE
                    }
                }
                .forEach { automation ->
                    val trigger = automation.triggers.first {
                        it.type == TriggerType.DEVICE || it.type == TriggerType.HEADPHONE
                    }
                    val triggerEvent = if (trigger.type == TriggerType.HEADPHONE) {
                        when (trigger.config["event"] ?: "CONNECTED") {
                            "DISCONNECTED" -> "HEADSET_DISCONNECTED"
                            else -> "HEADSET_CONNECTED"
                        }
                    } else {
                        trigger.config["event"] ?: "SCREEN_ON"
                    }
                    if (triggerEvent == event) {
                        val last = lastRunAt[automation.id] ?: 0L
                        if (now - last > automation.cooldownMillis) {
                            lastRunAt[automation.id] = now
                            activeStates[automation.id] = event
                            activeStore.markActive(SOURCE, "${automation.id}|$event")
                            executionEngine.runAutomation(automation)
                        }
                    } else if (oppositeEvent[triggerEvent] == event) {
                        // The condition ended: fire the exit behavior once.
                        if (activeStates.remove(automation.id) != null) {
                            activeStore.clearAutomation(SOURCE, automation.id)
                            executionEngine.runExit(automation)
                        }
                    }
                }
        }
    }

    private companion object {
        const val SOURCE = "device"
    }

}
