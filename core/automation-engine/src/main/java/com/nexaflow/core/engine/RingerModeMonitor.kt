package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires automations with a RINGER_MODE trigger when the phone's sound mode
 * changes to the configured state (NORMAL / VIBRATE / SILENT), e.g. switching
 * to Vibrate at work. The trigger config supports:
 *  - "mode": "NORMAL", "VIBRATE" or "SILENT"
 * When the sound mode changes away from the triggered state, the task's exit
 * behavior runs.
 */
@Singleton
class RingerModeMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    private val lastRunAt = ConcurrentHashMap<String, Long>()
    /** Automations currently in their triggered mode (to fire exit when it ends). */
    private val activeModes = ConcurrentHashMap<String, String>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            // The broadcast carries the new mode in EXTRA_RINGER_MODE; fall back
            // to querying the AudioManager in case the extra is missing.
            val extra = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)
            val mode = if (extra != -1) {
                when (extra) {
                    AudioManager.RINGER_MODE_SILENT -> "SILENT"
                    AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                    else -> "NORMAL"
                }
            } else {
                val audio = receiverContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                when (audio.ringerMode) {
                    AudioManager.RINGER_MODE_SILENT -> "SILENT"
                    AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                    else -> "NORMAL"
                }
            }
            handleModeChange(mode)
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        context.registerReceiver(receiver, filter)
        scope.launch {
            // Evaluate every enabled task against the CURRENT sound mode: a
            // task enabled while its mode already matches fires right away, a
            // task disabled while its condition still holds runs its exit
            // behavior, and a mode that was left while the process was down
            // fires its missed exit now instead of waiting for the next flip.
            reconcileAutomations()
        }
    }

    /**
     * Full re-evaluation of every RINGER_MODE task against the current sound
     * mode. Invoked on initialize and whenever automations change
     * (enable/disable toggles, saves), so:
     *  - a task enabled while its mode already matches fires immediately
     *    instead of waiting for the next mode change;
     *  - a task disabled while its condition still holds stops being tracked
     *    (its durable mark is pruned) instead of leaking until restart;
     *  - a mode that was left while the process was down fires its missed
     *    exit right away.
     */
    fun reconcileAutomations() {
        scope.launch {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentMode = when (audio.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "SILENT"
                AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                else -> "NORMAL"
            }
            val automations = repository.getAutomations().first()
            val byId = automations.associateBy { it.id }
            val now = System.currentTimeMillis()
            // Restore durable active markers first so a task that fired before
            // a process restart is never fired again while its condition still
            // holds; run the end behavior of tasks disabled or deleted while
            // the process was down.
            // Disabling a task is an explicit abandonment of its lifecycle:
            // the durable mark is pruned without firing a stale exit (the exit
            // contract covers the mode ENDING while the task stays enabled,
            // never a deliberate disable).
            activeStore.activeKeys(SOURCE).forEach { key ->
                val id = key.substringBefore('|')
                val automation = byId[id]
                when {
                    automation?.enabled == true -> {
                        activeModes[id] = key.substringAfter('|', "NORMAL")
                    }
                    else -> {
                        activeModes.remove(id)
                        activeStore.clearAutomation(SOURCE, id)
                    }
                }
            }
            automations
                .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.RINGER_MODE } }
                .forEach { automation ->
                    val ringerTriggers = automation.triggers.filter { it.type == TriggerType.RINGER_MODE }
                    val matchesNow = ringerTriggers.any {
                        (it.config["mode"] ?: "NORMAL") == currentMode
                    }
                    if (matchesNow) {
                        val last = lastRunAt[automation.id] ?: 0L
                        if (activeModes[automation.id] == null && now - last > automation.cooldownMillis) {
                            lastRunAt[automation.id] = now
                            activeModes[automation.id] = currentMode
                            activeStore.markActive(SOURCE, "${automation.id}|$currentMode")
                            executionEngine.runAutomation(automation)
                        }
                    } else if (activeModes.remove(automation.id) != null) {
                        // The condition already ended: run the exit behavior.
                        activeStore.clearAutomation(SOURCE, automation.id)
                        executionEngine.runExit(automation)
                    }
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

    private fun handleModeChange(mode: String) {
        scope.launch {
            val automations = repository.getAutomations().first()
            val now = System.currentTimeMillis()
            automations
                .filter { automation ->
                    automation.enabled && automation.triggers.any { it.type == TriggerType.RINGER_MODE }
                }
                .forEach { automation ->
                    val ringerTriggers = automation.triggers.filter { it.type == TriggerType.RINGER_MODE }
                    val matchesAny = ringerTriggers.any { (it.config["mode"] ?: "NORMAL") == mode }
                    if (matchesAny) {
                        val last = lastRunAt[automation.id] ?: 0L
                        if (now - last > automation.cooldownMillis) {
                            lastRunAt[automation.id] = now
                            activeModes[automation.id] = mode
                            activeStore.markActive(SOURCE, "${automation.id}|$mode")
                            executionEngine.runAutomation(automation)
                        }
                    } else if (activeModes.remove(automation.id) != null) {
                        // The sound mode changed away: the condition ended, fire exit.
                        activeStore.clearAutomation(SOURCE, automation.id)
                        executionEngine.runExit(automation)
                    }
                }
        }
    }

    private companion object {
        const val SOURCE = "ringer"
    }

}
