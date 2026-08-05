package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
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
                        if (now - last > COOLDOWN_MS) {
                            lastRunAt[automation.id] = now
                            activeModes[automation.id] = mode
                            executionEngine.runAutomation(automation)
                        }
                    } else if (activeModes.remove(automation.id) != null) {
                        // The sound mode changed away: the condition ended, fire exit.
                        executionEngine.runExit(automation)
                    }
                }
        }
    }

    companion object {
        private const val COOLDOWN_MS = 5_000L
    }
}
