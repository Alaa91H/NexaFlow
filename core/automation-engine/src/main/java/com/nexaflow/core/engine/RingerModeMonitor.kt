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

    private val lastRunAt = mutableMapOf<String, Long>()
    /** Automations currently in their triggered mode (to fire exit when it ends). */
    private val activeModes = mutableMapOf<String, String>()

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
            // Re-arm the in-memory active set from the durable ledger BEFORE
            // the next mode-change broadcast is evaluated: a task that was
            // triggered before a process/service restart must still fire its
            // exit behavior when the mode changes away.
            rearmFromLedger()
            // Fire the missed exit NOW for any task whose triggered mode has
            // already been left while the process was down (broadcasts only
            // fire on changes, so the exit would otherwise wait until the
            // mode flips again — possibly long after the task ended).
            reconcileWithCurrentMode()
        }
    }

    /**
     * Restores the durable active keys into the in-memory map. Keys carry the
     * triggered mode (`id|VIBRATE`), so the restored entry matches the exit
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
                activeModes[id] = key.substringAfter('|', "NORMAL")
            } else {
                activeStore.clearAutomation(SOURCE, id)
            }
        }
    }

    /**
     * Reads the CURRENT sound mode and fires the exit behavior for any task
     * whose triggered mode no longer matches — the condition already ended
     * while the process was down.
     */
    private suspend fun reconcileWithCurrentMode() {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentMode = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            else -> "NORMAL"
        }
        val automations = repository.getAutomations().first()
        automations
            .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.RINGER_MODE } }
            .forEach { automation ->
                // Only fire the exit when the triggered mode has already been
                // left (mirrors handleModeChange): a task whose mode still
                // matches after the restart stays active.
                val ringerTriggers = automation.triggers.filter { it.type == TriggerType.RINGER_MODE }
                val stillMatches = ringerTriggers.any { (it.config["mode"] ?: "NORMAL") == currentMode }
                if (!stillMatches && activeModes.remove(automation.id) != null) {
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
