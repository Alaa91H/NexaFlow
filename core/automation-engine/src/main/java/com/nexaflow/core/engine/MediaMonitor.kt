package com.nexaflow.core.engine

import android.content.Context
import android.media.AudioManager
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
    private val exitCoordinator: ExitExecutionCoordinator,
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
        runCatching {
            audio.registerAudioPlaybackCallback(playbackCallback, null)
            lastPlaying = audio.isMusicActive
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
        scope.launch {
            val automations = repository.getAutomations().first()
            automations
                .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.MEDIA_PLAYING } }
                .forEach { automation ->
                    val wantStart = (automation.triggers.first { it.type == TriggerType.MEDIA_PLAYING }
                        .config["event"] ?: "STARTED") == "STARTED"
                    if (playing == wantStart) {
                        if (activeStates.put(automation.id, playing) == null) {
                            activeStore.markActive(SOURCE, automation.id)
                            executionEngine.runAutomation(automation)
                        }
                    } else if (activeStates.remove(automation.id) != null) {
                        exitCoordinator.submit(SOURCE, automation)
                    }
                }
        }
    }

    private companion object {
        const val SOURCE = "media"
    }
}
