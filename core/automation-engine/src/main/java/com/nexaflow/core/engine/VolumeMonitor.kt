package com.nexaflow.core.engine

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
 * Standalone VOLUME_CHANGED trigger: fires a task when a stream volume
 * crosses the configured `threshold` (per `direction` ABOVE/BELOW), once per
 * crossing, and runs the task's exit behavior when the level crosses back.
 */
@Singleton
class VolumeMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    /** Automations currently in their triggered state (to fire exit on the way back). */
    private val activeStates = mutableMapOf<String, Boolean>()

    private val observer = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            handleVolumeChanged()
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI, true, observer
            )
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    private fun handleVolumeChanged() {
        scope.launch {
            val automations = repository.getAutomations().first()
            automations
                .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.VOLUME_CHANGED } }
                .forEach { automation ->
                    val trigger = automation.triggers.first { it.type == TriggerType.VOLUME_CHANGED }
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@forEach
                    val stream = when (trigger.config["stream"] ?: "MUSIC") {
                        "RING" -> AudioManager.STREAM_RING
                        "ALARM" -> AudioManager.STREAM_ALARM
                        "NOTIFICATION" -> AudioManager.STREAM_NOTIFICATION
                        else -> AudioManager.STREAM_MUSIC
                    }
                    val level = audio.getStreamVolume(stream)
                    val threshold = (trigger.config["threshold"] ?: "50").toIntOrNull() ?: 50
                    val above = (trigger.config["direction"] ?: "ABOVE") == "ABOVE"
                    val satisfied = if (above) level >= threshold else level <= threshold
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

    private companion object {
        const val SOURCE = "volume"
    }
}
