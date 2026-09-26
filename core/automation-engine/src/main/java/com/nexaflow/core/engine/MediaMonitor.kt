package com.nexaflow.core.engine

import android.content.Context
import android.media.AudioManager
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Standalone MEDIA_PLAYING trigger: fires a task when media playback starts
 * (per the configured `event`), once per transition, and runs the task's exit
 * behavior when playback stops.
 */
@Singleton
class MediaMonitor @Inject constructor(
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

    /** Automations currently in their triggered state (to fire exit on stop). */
    private val activeStates = mutableMapOf<String, Boolean>()

    private var lastPlaying: Boolean? = null

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>) {
            // A config change does not necessarily mean start/stop, so re-read
            // the coarse "is anything playing" flag and react to transitions.
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val playing = audio.isMusicActive
            if (playing == lastPlaying) return
            lastPlaying = playing
            handleState(playing)
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        scope.launch {
            // Restore durable ownership before callbacks can turn a process
            // restart into a duplicate main run or lose the matching exit.
            rearmFromLedger()
            if (!registered) return@launch
            runCatching {
                audio.registerAudioPlaybackCallback(playbackCallback, null)
                lastPlaying = audio.isMusicActive
            }
        }
    }

    private suspend fun rearmFromLedger() {
        val enabledIds = repository.getAutomations().first()
            .filter { it.enabled && it.triggers.any { trigger -> trigger.type == TriggerType.MEDIA_PLAYING } }
            .map { it.id }
            .toSet()

        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                if (state.automationId in enabledIds) {
                    activeStates[state.automationId] = true
                    activeStore.markActive(SOURCE, state.automationId)
                } else {
                    runtimeStore.clear(state.automationId, state.occurrenceId)
                    activeStore.clearAutomation(SOURCE, state.automationId)
                }
            }

        // Legacy markers are compatibility hints only. Without a durable
        // occurrence they must not authorize an exit side effect after restart.
        activeStore.activeKeys(SOURCE).forEach { key ->
            val id = key.substringBefore('|')
            val owned = runtimeStore.current(id)?.source == SOURCE
            if (id !in enabledIds || !owned) {
                activeStore.clearAutomation(SOURCE, id)
            }
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            audio.unregisterAudioPlaybackCallback(playbackCallback)
        }
    }

    private fun handleState(playing: Boolean) {
        scope.launch { reconcileState(playing) }
    }

    /** Serialized by the monitor scope in production; exposed internally for lifecycle contract tests. */
    internal suspend fun reconcileState(playing: Boolean) {
        val automations = repository.getAutomations().first()
        automations
            .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.MEDIA_PLAYING } }
            .forEach { automation ->
                    val wantStart = (automation.triggers.first { it.type == TriggerType.MEDIA_PLAYING }
                        .config["event"] ?: "STARTED") == "STARTED"
                    val satisfied = playing == wantStart

                    if (satisfied && activeStates[automation.id] != true) {
                        val occurrenceId = "media:${automation.id}:${UUID.randomUUID()}"
                        executionEngine.runAutomation(
                            automation = automation,
                            lifecycleContext = AutomationLifecycleContext(
                                occurrenceId = occurrenceId,
                                source = SOURCE,
                                sourceKey = if (wantStart) "STARTED" else "STOPPED"
                            )
                        )
                        val accepted = runtimeStore.current(automation.id)?.let { state ->
                            state.occurrenceId == occurrenceId && state.source == SOURCE
                        } == true
                        if (accepted) {
                            activeStates[automation.id] = true
                            activeStore.markActive(SOURCE, automation.id)
                        }
                    } else if (!satisfied && activeStates[automation.id] == true) {
                        when (exitCoordinator.requestExit(automation, ExitReason.TRIGGER_FALSE)) {
                            is ExitCoordinatorResult.Executed,
                            ExitCoordinatorResult.NotActive,
                            ExitCoordinatorResult.StaleOccurrence -> {
                                activeStates.remove(automation.id)
                                activeStore.clearAutomation(SOURCE, automation.id)
                            }
                            ExitCoordinatorResult.AlreadyInProgress,
                            is ExitCoordinatorResult.RecoveryRequired -> {
                                // Preserve ownership so a failed/uncertain exit
                                // remains visible and cannot be executed twice.
                                activeStates[automation.id] = true
                            }
                        }
                    }
                }
    }

    private companion object {
        const val SOURCE = "media"
    }
}
