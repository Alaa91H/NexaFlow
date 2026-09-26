package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
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
    private val runtimeStore: AutomationRuntimeStore,
    private val exitCoordinator: ExitCoordinator,
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
     *  - a task disabled while its condition still holds closes its durable
     *    occurrence through ExitCoordinator before compatibility state clears;
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

            rearmFromLedger(byId)

            // Preserve a durable lifecycle for disabled/edited routines until
            // their configured end behavior has either succeeded or been
            // retained as EXIT_FAILED by ExitCoordinator.
            automations
                .filter { automation ->
                    val state = runtimeStore.current(automation.id)
                    state?.source == SOURCE &&
                        (!automation.enabled || automation.triggers.none { it.type == TriggerType.RINGER_MODE })
                }
                .forEach { automation -> requestExit(automation, ExitReason.AUTOMATION_DISABLED) }

            automations
                .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.RINGER_MODE } }
                .forEach { automation ->
                    val matchesNow = automation.triggers
                        .filter { it.type == TriggerType.RINGER_MODE }
                        .any { (it.config["mode"] ?: "NORMAL") == currentMode }
                    if (matchesNow) {
                        activateIfNeeded(automation, currentMode)
                    } else {
                        requestExit(automation, ExitReason.TRIGGER_FALSE)
                    }
                }
        }
    }

    /**
     * Restores durable ringer ownership before evaluating the current mode and
     * upgrades pre-runtime-ledger ActiveTriggerStore entries in place. A legacy
     * key is promoted only when no newer lifecycle already owns the routine.
     */
    private suspend fun rearmFromLedger(
        automations: Map<String, com.nexaflow.domain.models.Automation>
    ) {
        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = automations[state.automationId]
                if (automation != null) {
                    activeModes[state.automationId] = state.sourceKey.substringAfter('|', "")
                    activeStore.markActive(SOURCE, state.sourceKey)
                }
            }

        activeStore.activeKeys(SOURCE).forEach { key ->
            val automationId = key.substringBefore('|')
            val automation = automations[automationId]
            if (automation == null) {
                // The immutable definition is gone, so do not invent an exit;
                // only remove the compatibility mirror.
                activeModes.remove(automationId)
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
                activeModes[automationId] = state.sourceKey.substringAfter('|', "")
                activeStore.markActive(SOURCE, state.sourceKey)
            } else {
                // Another stateful source owns the routine; a legacy ringer
                // mirror must not survive and later authorize a foreign exit.
                clearLegacyState(automationId)
            }
        }
    }

    private suspend fun activateIfNeeded(
        automation: com.nexaflow.domain.models.Automation,
        mode: String
    ) {
        val existing = runtimeStore.current(automation.id)
        if (existing?.source == SOURCE) {
            activeModes[automation.id] = existing.sourceKey.substringAfter('|', mode)
            activeStore.markActive(SOURCE, existing.sourceKey)
            return
        }
        if (existing != null) {
            // Another stateful trigger owns this automation lifecycle.
            activeModes.remove(automation.id)
            activeStore.clearAutomation(SOURCE, automation.id)
            return
        }

        val now = System.currentTimeMillis()
        val last = lastRunAt[automation.id] ?: 0L
        if (now - last <= automation.cooldownMillis) return

        val sourceKey = "${automation.id}|$mode"
        val occurrenceId = "ringer:${automation.id}:${UUID.randomUUID()}"
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
            state.source == SOURCE && state.occurrenceId == occurrenceId
        } == true
        if (admitted) {
            activeModes[automation.id] = mode
            activeStore.markActive(SOURCE, sourceKey)
        }
    }

    private suspend fun requestExit(
        automation: com.nexaflow.domain.models.Automation,
        reason: ExitReason
    ) {
        val state = runtimeStore.current(automation.id)
        if (state?.source != SOURCE) {
            if (state == null) clearLegacyState(automation.id)
            return
        }
        when (
            exitCoordinator.requestExit(
                automation = automation,
                reason = reason,
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
        activeModes.remove(automationId)
        activeStore.clearAutomation(SOURCE, automationId)
    }

    fun stop() {
        if (!registered) return
        registered = false
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Throwable) {
            // ignore
        }
        // Durable lifecycle ownership survives monitor restarts; only volatile
        // callback hints are discarded here.
        activeModes.clear()
    }

    private fun handleModeChange(mode: String) {
        scope.launch {
            val automations = repository.getAutomations().first()
            automations
                .filter { automation ->
                    automation.enabled && automation.triggers.any { it.type == TriggerType.RINGER_MODE }
                }
                .forEach { automation ->
                    val matchesAny = automation.triggers
                        .filter { it.type == TriggerType.RINGER_MODE }
                        .any { (it.config["mode"] ?: "NORMAL") == mode }
                    if (matchesAny) {
                        activateIfNeeded(automation, mode)
                    } else {
                        requestExit(automation, ExitReason.TRIGGER_FALSE)
                    }
                }
        }
    }

    private companion object {
        const val SOURCE = "ringer"
    }

}
