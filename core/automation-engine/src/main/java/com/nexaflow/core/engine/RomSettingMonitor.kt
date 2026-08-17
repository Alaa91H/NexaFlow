package com.nexaflow.core.engine

import android.content.Context
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.rom.EvolutionXSettingsBridge
import com.nexaflow.core.rom.EvolutionXSettingsBridge.Namespace
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires automations with a ROM_SETTING trigger when a real Evolution X /
 * LineageOS custom setting reaches the configured target value.
 *
 * Unlike broadcast-based monitors, ROM settings have no change broadcast, so
 * this monitor polls the actual value from the device's Settings provider
 * through [EvolutionXSettingsBridge] every [POLL_INTERVAL_MS]. Reading is free
 * (no root needed); the ROM is the source of truth, exactly as the user asked:
 * the task fires when the *actual* ROM state matches the configured key/value.
 *
 * Config keys (see [TriggerType.ROM_SETTING]):
 *  - `namespace`: SYSTEM / SECURE / GLOBAL
 *  - `key`: the real ROM key, e.g. `evo_disable_animation`
 *  - `operator`: EQUALS / NOT_EQUALS
 *  - `value`: the target value to compare against
 *
 * When the setting moves away from the target, the task's exit behavior runs.
 */
@Singleton
class RomSettingMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var pollingJob: Job? = null
    private val lastRunAt = mutableMapOf<String, Long>()
    /** Automations currently in their triggered state (to fire exit when it ends). */
    private val activeAutomations = mutableSetOf<String>()

    @Synchronized
    fun initialize() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            // Re-arm the durable active set BEFORE the first poll: the poll
            // re-reads the real ROM value, so a task whose setting already
            // moved away while the process was down fires its missed exit on
            // the first iteration instead of waiting for a future change.
            rearmFromLedger()
            while (isActive) {
                poll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Restores the durable active ids into the in-memory set. Stale keys for
     * deleted/disabled automations are pruned so a stale mark can never fire
     * a stale exit.
     */
    private suspend fun rearmFromLedger() {
        val enabledIds = repository.getAutomations().first()
            .filter { it.enabled }
            .map { it.id }
            .toSet()
        activeStore.activeKeys(SOURCE).forEach { key ->
            val id = key.substringBefore('|')
            if (id in enabledIds) {
                activeAutomations.add(id)
            } else {
                activeStore.clearAutomation(SOURCE, id)
            }
        }
    }

    @Synchronized
    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /** Test seam for verifying that service shutdown cancels the polling loop. */
    internal fun isPollingForTest(): Boolean = pollingJob?.isActive == true

    private suspend fun poll() {
        // Fast-path: nothing to watch when the ROM isn't Evolution X / LineageOS.
        if (!EvolutionXSettingsBridge.isEvolutionX(context)) return
        val automations = repository.getAutomations().first()
        val watchers = automations.filter { automation ->
            automation.enabled && automation.triggers.any { it.type == TriggerType.ROM_SETTING }
        }
        if (watchers.isEmpty()) {
            // Clear any lingering active states so exit fires once when the
            // task is re-enabled or the settings change back.
            activeAutomations.clear()
            return
        }
        val now = System.currentTimeMillis()
        watchers.forEach { automation ->
            automation.triggers.filter { it.type == TriggerType.ROM_SETTING }.forEach { trigger ->
                val namespace = romSettingNamespaceOf(trigger.config)
                val key = trigger.config["key"]?.takeIf { it.isNotEmpty() } ?: return@forEach
                val actual = EvolutionXSettingsBridge.read(context, namespace, key)
                if (romSettingMatches(trigger, actual)) {
                    val last = lastRunAt[automation.id] ?: 0L
                    if (now - last > automation.cooldownMillis) {
                        lastRunAt[automation.id] = now
                        activeAutomations.add(automation.id)
                        activeStore.markActive(SOURCE, automation.id)
                        executionEngine.runAutomation(automation)
                    }
                } else if (activeAutomations.remove(automation.id)) {
                    // The ROM setting left the target state: fire the exit.
                    activeStore.clearAutomation(SOURCE, automation.id)
                    executionEngine.runExit(automation)
                }
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
        const val SOURCE = "rom_setting"
    }
}

/** Parses the configured namespace; missing/bad values default to SYSTEM. */
internal fun romSettingNamespaceOf(config: Map<String, String>): Namespace =
    Namespace.entries.firstOrNull { it.name == (config["namespace"] ?: "SYSTEM") } ?: Namespace.SYSTEM

/** The target value the ROM setting must reach for the trigger to fire. */
internal fun romSettingTargetOf(config: Map<String, String>): String? =
    config["value"]?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Pure matcher: does the actual ROM value satisfy this trigger's operator?
 * Missing key/value config never matches (the trigger stays inert).
 */
internal fun romSettingMatches(trigger: Trigger, actual: String?): Boolean {
    val target = romSettingTargetOf(trigger.config) ?: return false
    return when (trigger.config["operator"] ?: "EQUALS") {
        "NOT_EQUALS" -> actual != target
        else -> actual == target
    }
}
