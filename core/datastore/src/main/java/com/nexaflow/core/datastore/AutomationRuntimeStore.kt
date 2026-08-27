package com.nexaflow.core.datastore

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.automationRuntimeDataStore by preferencesDataStore(
    name = "nexaflow_automation_runtime",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Bounded persistent source of truth for stateful automation lifecycles.
 *
 * All read-modify-write transitions execute in one DataStore transaction. A
 * caller must claim an occurrence before any exit side effect; callers that
 * race with that claim receive a non-owning result and cannot execute a second
 * logical exit. A failed exit remains durable and observable instead of being
 * deleted as though cleanup succeeded.
 */
class AutomationRuntimeStore(private val context: Context) {

    private val dataStore = context.automationRuntimeDataStore
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }

    /**
     * Reserves a lifecycle before the automation's main actions run. Existing
     * active/exiting/failed state is intentionally never overwritten: an old
     * occurrence must first finish or remain visible for recovery.
     */
    suspend fun activate(state: AutomationRuntimeState): Boolean {
        var accepted = false
        dataStore.edit { preferences ->
            val states = runtimeStates(preferences)
            if (state.automationId !in states && states.size < MAX_RUNTIME_STATES) {
                states[state.automationId] = state
                writeRuntimeStates(preferences, states)
                accepted = true
            }
        }
        return accepted
    }

    suspend fun current(automationId: String): AutomationRuntimeState? =
        runtimeStates(dataStore.data.first())[automationId]

    suspend fun activeStates(): List<AutomationRuntimeState> =
        runtimeStates(dataStore.data.first()).values.sortedBy { it.activatedAt }

    /**
     * Atomically moves a matching active occurrence into EXITING. An omitted
     * occurrence id means the caller only owns whichever active occurrence is
     * currently recorded for the automation (used by trigger monitors).
     */
    suspend fun claimExit(
        automationId: String,
        occurrenceId: String? = null,
        reason: ExitReason,
        now: Long
    ): ExitClaim {
        var claim: ExitClaim = ExitClaim.NoActiveOccurrence
        dataStore.edit { preferences ->
            val states = runtimeStates(preferences)
            val current = states[automationId]
            claim = when {
                current == null -> ExitClaim.NoActiveOccurrence
                occurrenceId != null && current.occurrenceId != occurrenceId -> ExitClaim.OccurrenceMismatch
                current.lifecycleState == AutomationRuntimeLifecycleState.ACTIVE -> {
                    val claimed = current.copy(
                        lifecycleState = AutomationRuntimeLifecycleState.EXITING,
                        exitStartedAt = now,
                        exitAttempt = current.exitAttempt + 1,
                        exitReason = reason,
                        lastError = null
                    )
                    states[automationId] = claimed
                    writeRuntimeStates(preferences, states)
                    ExitClaim.Claimed(claimed)
                }
                current.lifecycleState == AutomationRuntimeLifecycleState.EXITING -> ExitClaim.AlreadyExiting
                current.lifecycleState == AutomationRuntimeLifecycleState.EXIT_FAILED ->
                    ExitClaim.RecoveryRequired(current)
            }
        }
        return claim
    }

    /**
     * Reserves a visible failed exit for one explicit reconciliation attempt.
     * Automatic trigger callbacks deliberately cannot retry a failure repeatedly.
     */
    suspend fun claimFailedExitForRecovery(
        automationId: String,
        occurrenceId: String,
        reason: ExitReason,
        now: Long
    ): ExitClaim {
        var claim: ExitClaim = ExitClaim.NoActiveOccurrence
        dataStore.edit { preferences ->
            val states = runtimeStates(preferences)
            val current = states[automationId]
            claim = when {
                current == null -> ExitClaim.NoActiveOccurrence
                current.occurrenceId != occurrenceId -> ExitClaim.OccurrenceMismatch
                current.lifecycleState != AutomationRuntimeLifecycleState.EXIT_FAILED -> ExitClaim.AlreadyExiting
                else -> {
                    val claimed = current.copy(
                        lifecycleState = AutomationRuntimeLifecycleState.EXITING,
                        exitStartedAt = now,
                        exitAttempt = current.exitAttempt + 1,
                        exitReason = reason,
                        lastError = null
                    )
                    states[automationId] = claimed
                    writeRuntimeStates(preferences, states)
                    ExitClaim.Claimed(claimed)
                }
            }
        }
        return claim
    }

    /** Removes only the matching successfully-exited occurrence. */
    suspend fun completeExit(automationId: String, occurrenceId: String): Boolean {
        var removed = false
        dataStore.edit { preferences ->
            val states = runtimeStates(preferences)
            val current = states[automationId]
            if (current?.occurrenceId == occurrenceId &&
                current.lifecycleState == AutomationRuntimeLifecycleState.EXITING
            ) {
                states.remove(automationId)
                writeRuntimeStates(preferences, states)
                removed = true
            }
        }
        return removed
    }

    /** Persists failure before returning to the caller; it must not be discarded. */
    suspend fun failExit(
        automationId: String,
        occurrenceId: String,
        reason: ExitReason,
        error: String,
        now: Long
    ): Boolean {
        var changed = false
        dataStore.edit { preferences ->
            val states = runtimeStates(preferences)
            val current = states[automationId]
            if (current?.occurrenceId == occurrenceId &&
                current.lifecycleState == AutomationRuntimeLifecycleState.EXITING
            ) {
                states[automationId] = current.copy(
                    lifecycleState = AutomationRuntimeLifecycleState.EXIT_FAILED,
                    exitReason = reason,
                    exitStartedAt = now,
                    lastError = error.take(AutomationRuntimeState.MAX_ERROR_LENGTH)
                )
                writeRuntimeStates(preferences, states)
                changed = true
            }
        }
        return changed
    }

    /** Explicit cleanup is allowed only for a matching occurrence after policy resolution. */
    suspend fun clear(automationId: String, occurrenceId: String? = null): Boolean {
        var removed = false
        dataStore.edit { preferences ->
            val states = runtimeStates(preferences)
            val current = states[automationId]
            if (current != null && (occurrenceId == null || current.occurrenceId == occurrenceId)) {
                states.remove(automationId)
                writeRuntimeStates(preferences, states)
                removed = true
            }
        }
        return removed
    }

    /**
     * Registers a pending schedule before its AlarmManager side effect. The
     * ledger remains bounded; callers must not arm an alarm when this returns
     * false because an untracked alarm cannot be safely validated on delivery.
     */
    suspend fun registerSchedule(occurrence: ScheduledAutomationOccurrence): Boolean {
        var registered = false
        dataStore.edit { preferences ->
            val schedules = schedules(preferences)
            val key = scheduleKey(occurrence.automationId, occurrence.occurrenceId)
            if (key in schedules || schedules.size < MAX_SCHEDULES) {
                schedules[key] = occurrence
                writeSchedules(preferences, schedules)
                registered = true
            }
        }
        return registered
    }

    suspend fun schedulesFor(automationId: String): List<ScheduledAutomationOccurrence> =
        schedules(dataStore.data.first()).values
            .filter { it.automationId == automationId }
            .sortedBy { it.windowStartAt }

    /** Validates that an alarm intent still identifies the current stored occurrence. */
    suspend fun matchesSchedule(
        automationId: String,
        occurrenceId: String,
        generation: String,
        expectedEndAt: Long? = null
    ): Boolean {
        return schedules(dataStore.data.first()).values.any { current ->
            current.automationId == automationId &&
                current.occurrenceId == occurrenceId &&
                current.generation == generation &&
                (expectedEndAt == null || current.windowEndAt == expectedEndAt)
        }
    }

    /** Removes one completed occurrence without disturbing a following scheduled window. */
    suspend fun clearScheduleOccurrence(automationId: String, occurrenceId: String) {
        dataStore.edit { preferences ->
            val schedules = schedules(preferences)
            if (schedules.remove(scheduleKey(automationId, occurrenceId)) != null) {
                writeSchedules(preferences, schedules)
            }
        }
    }

    /**
     * Clears obsolete scheduled occurrences while preserving the supplied
     * currently-active time window. Configuration edits must not make that
     * window's already-earned exit impossible to validate.
     */
    suspend fun clearSchedulesExcept(automationId: String, occurrenceId: String?) {
        dataStore.edit { preferences ->
            val schedules = schedules(preferences)
            val removed = schedules.keys.removeAll { key ->
                key.startsWith("$automationId$SCHEDULE_KEY_SEPARATOR") &&
                    (occurrenceId == null || key != scheduleKey(automationId, occurrenceId))
            }
            if (removed) writeSchedules(preferences, schedules)
        }
    }

    /** Removes all schedule identities after an automation is disabled/deleted. */
    suspend fun clearSchedule(automationId: String) {
        dataStore.edit { preferences ->
            val schedules = schedules(preferences)
            val removed = schedules.keys.removeAll { key ->
                key.startsWith("$automationId$SCHEDULE_KEY_SEPARATOR")
            }
            if (removed) writeSchedules(preferences, schedules)
        }
    }

    internal suspend fun statesForTest(): List<AutomationRuntimeState> = activeStates()

    private fun runtimeStates(preferences: Preferences): LinkedHashMap<String, AutomationRuntimeState> {
        val states = LinkedHashMap<String, AutomationRuntimeState>()
        preferences[KEY_RUNTIME_STATES].orEmpty().forEach { serialized ->
            runCatching { json.decodeFromString(AutomationRuntimeState.serializer(), serialized) }
                .getOrNull()
                ?.let { state -> states.putIfAbsent(state.automationId, state) }
        }
        return states
    }

    private fun schedules(preferences: Preferences): LinkedHashMap<String, ScheduledAutomationOccurrence> {
        val schedules = LinkedHashMap<String, ScheduledAutomationOccurrence>()
        preferences[KEY_SCHEDULES].orEmpty().forEach { serialized ->
            runCatching { json.decodeFromString(ScheduledAutomationOccurrence.serializer(), serialized) }
                .getOrNull()
                ?.let { schedule -> schedules.putIfAbsent(scheduleKey(schedule.automationId, schedule.occurrenceId), schedule) }
        }
        return schedules
    }

    private fun scheduleKey(automationId: String, occurrenceId: String): String =
        "$automationId$SCHEDULE_KEY_SEPARATOR$occurrenceId"

    private fun writeRuntimeStates(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        states: Map<String, AutomationRuntimeState>
    ) {
        preferences[KEY_RUNTIME_STATES] = states.values.mapTo(LinkedHashSet()) {
            json.encodeToString(AutomationRuntimeState.serializer(), it)
        }
    }

    private fun writeSchedules(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        schedules: Map<String, ScheduledAutomationOccurrence>
    ) {
        preferences[KEY_SCHEDULES] = schedules.values.mapTo(LinkedHashSet()) {
            json.encodeToString(ScheduledAutomationOccurrence.serializer(), it)
        }
    }

    private companion object {
        val KEY_RUNTIME_STATES = stringSetPreferencesKey("automation_runtime_states")
        val KEY_SCHEDULES = stringSetPreferencesKey("automation_runtime_schedules")
        const val MAX_RUNTIME_STATES = 128
        const val MAX_SCHEDULES = 512
        const val SCHEDULE_KEY_SEPARATOR = "\u0001"
    }
}
