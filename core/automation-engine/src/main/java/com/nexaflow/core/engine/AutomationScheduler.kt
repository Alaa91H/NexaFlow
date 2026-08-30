package com.nexaflow.core.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ScheduledAutomationOccurrence
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.schedule.TimeTriggerCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules user-selected wall-clock automations as immutable occurrences.
 *
 * AlarmManager delivery is only a wake-up/event source. The receiver validates
 * an occurrence id and generation against [runtimeStore] before it can start
 * work or consume a time-window exit, so an alarm left behind by an edit cannot
 * target a newer active lifecycle.
 */
@Singleton
class AutomationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val runtimeStore: AutomationRuntimeStore,
    private val exitCoordinator: ExitCoordinator,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val scheduledIds = mutableSetOf<String>()
    private val retryJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    // Enables best-effort deletion cleanup while this process still owns the
    // immutable definition needed for configured exit actions. Persisted state
    // is never replayed after deletion because its definition is unavailable.
    private var lastKnownAutomations: Map<String, Automation> = emptyMap()

    @Volatile
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        scope.launch {
            repository.getAutomations().collect { automations ->
                rescheduleAll(automations)
            }
        }
    }

    /** Rebuilds all alarms from the persisted user configuration. */
    suspend fun rescheduleAll(automations: List<Automation>) {
        // Disabling a time-triggered automation while its range is active must
        // close the currently owned lifecycle before stale schedules disappear.
        automations
            .filter { !it.enabled }
            .forEach { automation ->
                exitCoordinator.requestExit(automation, ExitReason.AUTOMATION_DISABLED)
            }
        val currentById = automations.associateBy { it.id }
        // A deletion removes the repository definition. While this scheduler
        // process still retains the preceding immutable definition, route a
        // final exit request before that evidence disappears.
        lastKnownAutomations
            .filterKeys { it !in currentById }
            .values
            .forEach { automation ->
                exitCoordinator.requestExit(automation, ExitReason.AUTOMATION_DISABLED)
            }

        val desired = mutableSetOf<String>()
        automations.forEach { automation ->
            if (automation.enabled && automation.triggers.any { it.type == TriggerType.TIME }) {
                // One malformed stored config must never break every schedule.
                // Only retain the id when the occurrence was actually admitted
                // and its alarm was armed. A failed registration must remain
                // retryable and must never look scheduled in memory.
                if (scheduleFresh(automation)) {
                    desired.add(automation.id)
                    retryJobs.remove(automation.id)?.cancel()
                } else {
                    scheduleRetry(automation.id)
                }
            }
        }
        val toCancel = scheduledIds - desired
        toCancel.forEach { cancel(it, preserveActiveWindow = false) }
        scheduledIds.clear()
        scheduledIds.addAll(desired)
        lastKnownAutomations = currentById
    }

    /**
     * Retries a failed admission a bounded number of times. The durable ledger
     * is cleared when arming fails, so each attempt either owns a real alarm or
     * remains explicitly unarmed; no phantom schedule is reported as active.
     */
    private fun scheduleRetry(automationId: String) {
        if (retryJobs[automationId]?.isActive == true) return
        retryJobs[automationId] = scope.launch {
            try {
                RETRY_DELAYS_MS.forEach { waitMs ->
                    delay(waitMs)
                    val automation = repository.getAutomationById(automationId)
                    if (automation == null || !automation.enabled) return@launch
                    if (scheduleFresh(automation)) {
                        Log.i(TAG, "Recovered time schedule for $automationId after alarm admission retry")
                        return@launch
                    }
                }
                Log.e(TAG, "Time schedule remains unarmed after bounded retries for $automationId")
            } finally {
                retryJobs.remove(automationId)
            }
        }
    }

    /**
     * Creates the first future occurrence for a newly saved/changed schedule.
     * Old alarms are canceled where possible and rendered stale by clearing the
     * durable schedule identities before the new generation is written.
     */
    private suspend fun scheduleFresh(automation: Automation): Boolean = try {
        cancel(automation.id, preserveActiveWindow = true)
        // AlarmManager drops alarms over reboot. `cancel(... preserveActiveWindow
        // = true)` intentionally retains the immutable schedule ledger so an
        // already-running cross-midnight range can still end. Re-arm only that
        // exact durable END identity; creating a fresh occurrence here would
        // detach the 06:00 delivery from the ACTIVE lifecycle that owns it.
        rearmRetainedActiveRangeEnd(automation.id)
        val config = automation.timeConfigOrNull()
        val triggerAt = config?.let { TimeTriggerCalculator.nextFireTime(it, System.currentTimeMillis()) }
        if (config == null || triggerAt == null) false
        else registerAndArm(automation.id, config, triggerAt)
    } catch (failure: Throwable) {
        Log.e(TAG, "Failed to schedule ${automation.id}", failure)
        false
    }

    /**
     * Arms the occurrence following [automationId]'s current start. It does not
     * cancel the active window's END alarm; both identities coexist until their
     * own end delivery is consumed or a complete reschedule occurs.
     */
    suspend fun scheduleNext(automationId: String) {
        val automation = repository.getAutomationById(automationId) ?: return
        if (!automation.enabled) return
        val config = automation.timeConfigOrNull() ?: return
        if (config["repeat"] == TimeTriggerCalculator.REPEAT_ONCE ||
            config["repeat"] == TimeTriggerCalculator.REPEAT_SPECIFIC_DATE
        ) return
        val triggerAt = TimeTriggerCalculator.nextFireTime(
            config = config,
            fromMillis = System.currentTimeMillis() + SCHEDULE_GUARD_MS
        ) ?: return
        val occurrenceId = occurrenceId(triggerAt, TimeTriggerCalculator.windowEndMillis(config, triggerAt))
        if (runtimeStore.schedulesFor(automationId).any { it.occurrenceId == occurrenceId }) return
        registerAndArm(automationId, config, triggerAt)
    }

    /** Clears all PendingIntents and all durable schedule identities for the automation. */
    suspend fun cancel(automationId: String, preserveActiveWindow: Boolean = false) {
        val activeOccurrenceId = runtimeStore.current(automationId)
            ?.takeIf { preserveActiveWindow && it.source == SOURCE_TIME_RANGE }
            ?.occurrenceId
        val scheduled = runtimeStore.schedulesFor(automationId)
        // Upgrade compatibility: prior releases used a single id/action identity.
        cancelPendingIntent(buildPendingIntent(automationId, ACTION_RUN_AUTOMATION, null))
        cancelPendingIntent(buildPendingIntent(automationId, ACTION_END_AUTOMATION, null))
        scheduled
            .filter { it.occurrenceId != activeOccurrenceId }
            .forEach { occurrence ->
                cancelPendingIntent(buildPendingIntent(automationId, ACTION_RUN_AUTOMATION, occurrence))
                if (occurrence.windowEndAt != null) {
                    cancelPendingIntent(buildPendingIntent(automationId, ACTION_END_AUTOMATION, occurrence))
                }
            }
        if (preserveActiveWindow) {
            runtimeStore.clearSchedulesExcept(automationId, activeOccurrenceId)
        } else {
            runtimeStore.clearSchedule(automationId)
        }
    }

    /** Re-arms the exact future END alarm retained for an already ACTIVE time range. */
    private suspend fun rearmRetainedActiveRangeEnd(automationId: String) {
        val retained = retainedActiveEndForRearm(
            activeState = runtimeStore.current(automationId),
            occurrences = runtimeStore.schedulesFor(automationId),
            now = System.currentTimeMillis()
        ) ?: return
        val endAt = checkNotNull(retained.windowEndAt)
        if (setAlarm(endAt, buildPendingIntent(automationId, ACTION_END_AUTOMATION, retained))) {
            Log.i(TAG, "Re-armed retained time-range END for $automationId at $endAt")
        } else {
            Log.e(TAG, "Unable to re-arm retained time-range END for $automationId at $endAt")
        }
    }

    /**
     * Re-arms one still-valid occurrence after a transient receiver failure.
     * Retries are bounded by the receiver and reuse the immutable occurrence
     * identity, so this cannot create a new logical execution.
     */
    suspend fun rearmOccurrence(
        automationId: String,
        occurrenceId: String,
        action: String,
        retryCount: Int
    ): Boolean {
        if (!receiverRetryAllowed(retryCount)) return false
        val occurrence = runtimeStore.schedulesFor(automationId)
            .firstOrNull { it.occurrenceId == occurrenceId } ?: return false
        val retryAt = System.currentTimeMillis() + RECEIVER_RETRY_DELAY_MS
        return setAlarm(
            retryAt,
            buildPendingIntent(automationId, action, occurrence, retryCount + 1)
        )
    }

    /** Validates that an incoming alarm still belongs to an armed occurrence. */
    suspend fun matchesIncomingOccurrence(
        automationId: String,
        occurrenceId: String,
        generation: String,
        expectedEndAt: Long? = null
    ): Boolean = runtimeStore.matchesSchedule(automationId, occurrenceId, generation, expectedEndAt)

    /** Removes only the occurrence whose end was processed; a following start remains armed. */
    suspend fun completeOccurrence(automationId: String, occurrenceId: String) {
        runtimeStore.clearScheduleOccurrence(automationId, occurrenceId)
    }

    private suspend fun registerAndArm(
        automationId: String,
        config: Map<String, String>,
        triggerAt: Long
    ): Boolean {
        val endAt = TimeTriggerCalculator.windowEndMillis(config, triggerAt)
        if (config["timeMode"] == TIME_MODE_RANGE && endAt == null) {
            Log.w(TAG, "Ignoring time range without a valid end for $automationId")
            return false
        }
        val occurrence = ScheduledAutomationOccurrence(
            automationId = automationId,
            occurrenceId = occurrenceId(triggerAt, endAt),
            generation = generationOf(automationId, config, triggerAt, endAt),
            windowStartAt = triggerAt,
            windowEndAt = endAt
        )
        // Persistence precedes the side effect: if the process dies after this
        // line, recovery can re-arm from config while a delivered stale intent
        // is still harmless because it must match this immutable token.
        if (!runtimeStore.registerSchedule(occurrence)) {
            Log.w(TAG, "Schedule ledger is full; refusing untracked alarm for $automationId")
            return false
        }
        if (!setAlarm(triggerAt, buildPendingIntent(automationId, ACTION_RUN_AUTOMATION, occurrence))) {
            runtimeStore.clearScheduleOccurrence(automationId, occurrence.occurrenceId)
            Log.e(TAG, "Failed to arm START alarm; occurrence was not admitted for $automationId")
            return false
        }
        val endArmed = endAt?.takeIf { it > System.currentTimeMillis() }?.let { windowEnd ->
            setAlarm(windowEnd, buildPendingIntent(automationId, ACTION_END_AUTOMATION, occurrence))
        } ?: true
        if (!endArmed) {
            cancelPendingIntent(buildPendingIntent(automationId, ACTION_RUN_AUTOMATION, occurrence))
            runtimeStore.clearScheduleOccurrence(automationId, occurrence.occurrenceId)
            Log.e(TAG, "Failed to arm END alarm; occurrence was not admitted for $automationId")
            return false
        }
        return true
    }

    private fun setAlarm(triggerAt: Long, pendingIntent: PendingIntent): Boolean {
        return try {
            if (!exactAlarmAllowed(
                    sdkInt = Build.VERSION.SDK_INT,
                    canScheduleExactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        alarmManager.canScheduleExactAlarms()
                )
            ) {
                Log.w(TAG, "Exact alarm access missing; using inexact idle-safe fallback")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            true
        } catch (security: SecurityException) {
            Log.w(TAG, "Exact alarm rejected; using inexact idle-safe fallback", security)
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }.isSuccess
        } catch (failure: Throwable) {
            Log.e(TAG, "Alarm registration failed at $triggerAt", failure)
            false
        }
    }

    private fun cancelPendingIntent(pendingIntent: PendingIntent) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildPendingIntent(
        automationId: String,
        action: String,
        occurrence: ScheduledAutomationOccurrence?,
        retryCount: Int = 0
    ): PendingIntent {
        val intent = Intent(context, AutomationAlarmReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_AUTOMATION_ID, automationId)
        if (occurrence != null) {
            intent.data = Uri.Builder()
                .scheme("nexaflow")
                .authority("automation")
                .appendPath(automationId)
                .appendPath(action)
                .appendPath(occurrence.occurrenceId)
                .appendPath(occurrence.generation)
                .build()
            intent.putExtra(EXTRA_OCCURRENCE_ID, occurrence.occurrenceId)
            intent.putExtra(EXTRA_SCHEDULE_GENERATION, occurrence.generation)
            intent.putExtra(EXTRA_WINDOW_START_AT, occurrence.windowStartAt)
            occurrence.windowEndAt?.let { intent.putExtra(EXTRA_WINDOW_END_AT, it) }
            intent.putExtra(EXTRA_RETRY_COUNT, retryCount)
        }
        return PendingIntent.getBroadcast(
            context,
            (automationId.hashCode() * 31 + action.hashCode()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun Automation.timeConfigOrNull(): Map<String, String>? {
        val config = triggers.firstOrNull { it.type == TriggerType.TIME }?.config ?: return null
        return config.takeIf { !it["time"].isNullOrBlank() || !it["rangeStart"].isNullOrBlank() }
    }

    companion object {
        internal fun exactAlarmAllowed(sdkInt: Int, canScheduleExactAlarms: Boolean): Boolean =
            sdkInt < Build.VERSION_CODES.S || canScheduleExactAlarms

        /**
         * Selects an immutable END occurrence for reboot/reconciliation re-arm.
         * Every field is intentionally matched: an edited schedule may retain an
         * older ledger entry, but only the occurrence the ACTIVE lifecycle owns
         * can consume its exit behavior.
         */
        internal fun retainedActiveEndForRearm(
            activeState: AutomationRuntimeState?,
            occurrences: List<ScheduledAutomationOccurrence>,
            now: Long
        ): ScheduledAutomationOccurrence? = activeState
            ?.takeIf {
                it.lifecycleState == AutomationRuntimeLifecycleState.ACTIVE &&
                    it.source == SOURCE_TIME_RANGE &&
                    it.expectedEndAt != null
            }
            ?.let { active ->
                val expectedEndAt = active.expectedEndAt ?: return@let null
                occurrences.firstOrNull { occurrence ->
                    occurrence.automationId == active.automationId &&
                        occurrence.occurrenceId == active.occurrenceId &&
                        occurrence.generation == active.scheduleGeneration &&
                        occurrence.windowEndAt == expectedEndAt &&
                        expectedEndAt > now
                }
            }

        internal fun occurrenceId(windowStartAt: Long, windowEndAt: Long?): String =
            "time:$windowStartAt:${windowEndAt ?: 0L}"

        internal fun generationOf(
            automationId: String,
            config: Map<String, String>,
            windowStartAt: Long,
            windowEndAt: Long?
        ): String {
            val material = buildString {
                append(automationId)
                append('|')
                append(windowStartAt)
                append('|')
                append(windowEndAt ?: 0L)
                config.toSortedMap().forEach { (key, value) ->
                    append('|')
                    append(key)
                    append('=')
                    append(value)
                }
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(material.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { "%02x".format(it) }
        }

        internal fun receiverRetryAllowed(retryCount: Int): Boolean =
            retryCount in 0 until MAX_RECEIVER_RETRIES

        private const val TAG = "AutomationScheduler"
        private val RETRY_DELAYS_MS = longArrayOf(10_000L, 60_000L, 300_000L)
        private const val SCHEDULE_GUARD_MS = 60_000L
        private const val RECEIVER_RETRY_DELAY_MS = 15_000L
        private const val MAX_RECEIVER_RETRIES = 2
        private const val SOURCE_TIME_RANGE = "time-range"
        private const val TIME_MODE_RANGE = "RANGE"
        const val ACTION_RUN_AUTOMATION = "com.nexaflow.core.engine.action.RUN_AUTOMATION"
        const val ACTION_END_AUTOMATION = "com.nexaflow.core.engine.action.END_AUTOMATION"
        const val EXTRA_AUTOMATION_ID = "com.nexaflow.core.engine.extra.AUTOMATION_ID"
        const val EXTRA_OCCURRENCE_ID = "com.nexaflow.core.engine.extra.OCCURRENCE_ID"
        const val EXTRA_SCHEDULE_GENERATION = "com.nexaflow.core.engine.extra.SCHEDULE_GENERATION"
        const val EXTRA_WINDOW_START_AT = "com.nexaflow.core.engine.extra.WINDOW_START_AT"
        const val EXTRA_WINDOW_END_AT = "extra_window_end_at"
        const val EXTRA_RETRY_COUNT = "extra_retry_count"
    }
}
