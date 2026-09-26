package com.nexaflow.core.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.TriggerOccurrence
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires automations with a CALENDAR trigger when a matching calendar event is
 * created, starts, or ends. The trigger config supports:
 *  - "calendar": display name of the calendar (blank = any calendar)
 *  - "contains": text matched against the event title/location/description
 *  - "event": "EVENT_START" (default), "EVENT_END" or "EVENT_CREATED"
 *  - "beforeMinutes": how long before the event start to fire (EVENT_START only)
 *
 * Recurring events: [CalendarContract.Instances] expands them into one row per
 * occurrence, so the monitor keys its state on the *occurrence* (event id +
 * start time) rather than the shared event id. Otherwise a daily/weekly event
 * would only ever fire once.
 *
 * The monitor watches [CalendarContract.Events.CONTENT_URI] for changes and
 * also re-scans periodically so tasks fire at the right moment even when the
 * calendar does not change (recurring events, "before X minutes" lead time).
 */
@Singleton
class CalendarMonitor @Inject constructor(
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
    /** automationId -> occurrence key (eventId, start) of the event that activated the task. */
    private val activeStates = ConcurrentHashMap<String, Occurrence>()
    /** automationId -> occurrence keys already reported as started/ended (thread-safe). */
    private val processedEvents = ConcurrentHashMap<String, MutableSet<Occurrence>>()
    /** automationId -> event ids already reported as created (creation is per event, not per occurrence). */
    private val processedCreated = ConcurrentHashMap<String, MutableSet<Long>>()

    /** Serializes observer, periodic and automation-change rescans. */
    private val rescanMutex = Mutex()

    private val handler = Handler(Looper.getMainLooper())

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = rescan()
        override fun onChange(selfChange: Boolean, uri: Uri?) = rescan()
    }

    private val periodicScan = object : Runnable {
        override fun run() {
            if (!registered) return
            rescan()
            handler.postDelayed(this, RESCAN_INTERVAL_MS)
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        val observerRegistered = runCatching {
            context.contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                true,
                observer
            )
            true
        }.getOrDefault(false)
        if (!observerRegistered) {
            registered = false
            return
        }
        handler.postDelayed(periodicScan, RESCAN_INTERVAL_MS)
        // The first pass restores durable ownership before it interprets the
        // current calendar snapshot.
        rescan()
    }

    fun stop() {
        if (!registered) return
        registered = false
        handler.removeCallbacks(periodicScan)
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    /** Re-evaluate lifecycle ownership after enable/disable/edit operations. */
    fun reconcileAutomations() {
        rescan()
    }

    private fun rescan() {
        scope.launch {
            rescanMutex.withLock {
                val automations = repository.getAutomations().first()
                val byId = automations.associateBy { it.id }

                // Disabled/edited lifecycle cleanup does not require calendar
                // permission, so reconcile durable ownership before the query.
                rearmFromLedger(byId)

                if (
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    return@withLock
                }

                val calendarTriggers = automations.filter {
                    it.enabled && it.triggers.any { trigger ->
                        trigger.type == TriggerType.CALENDAR
                    }
                }
                if (calendarTriggers.isEmpty()) return@withLock

                val events = queryUpcomingEvents()
                val now = System.currentTimeMillis()
                calendarTriggers.forEach { automation ->
                    handleAutomation(automation, events, now)
                }
            }
        }
    }

    /**
     * Restores authoritative runtime-ledger ownership and promotes old calendar
     * compatibility markers exactly once. Deleted definitions never authorize
     * an invented exit; their durable evidence is left visible for recovery.
     */
    private suspend fun rearmFromLedger(automations: Map<String, Automation>) {
        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = automations[state.automationId]
                when {
                    automation == null -> {
                        activeStates.remove(state.automationId)
                        activeStore.clearAutomation(SOURCE, state.automationId)
                    }
                    !automation.enabled ||
                        automation.triggers.none { it.type == TriggerType.CALENDAR } -> {
                        requestExit(
                            automation = automation,
                            reason = ExitReason.AUTOMATION_DISABLED,
                            occurrenceId = state.occurrenceId
                        )
                    }
                    else -> {
                        val occurrence = decodeOccurrence(state.sourceKey)
                        if (occurrence != null) {
                            activeStates[state.automationId] = occurrence
                            activeStore.markActive(SOURCE, state.sourceKey)
                        } else {
                            // Malformed durable evidence must remain visible;
                            // only its legacy mirror is unsafe to trust.
                            activeStates.remove(state.automationId)
                            activeStore.clearAutomation(SOURCE, state.automationId)
                        }
                    }
                }
            }

        activeStore.activeKeys(SOURCE).forEach { key ->
            val automationId = key.substringBefore('|')
            val automation = automations[automationId]
            val occurrence = decodeOccurrence(key)
            if (automation == null || occurrence == null) {
                activeStates.remove(automationId)
                activeStore.clearAutomation(SOURCE, automationId)
                return@forEach
            }

            if (runtimeStore.current(automationId) == null) {
                runtimeStore.activateStrict(
                    AutomationRuntimeState(
                        automationId = automationId,
                        occurrenceId = "legacy:$SOURCE:$automationId:${occurrence.eventId}:${occurrence.start}:${UUID.randomUUID()}",
                        source = SOURCE,
                        sourceKey = encodeSourceKey(automationId, occurrence),
                        lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                        activatedAt = System.currentTimeMillis()
                    )
                )
            }

            val current = runtimeStore.current(automationId)
            if (current?.source == SOURCE) {
                activeStates[automationId] = decodeOccurrence(current.sourceKey) ?: occurrence
                activeStore.markActive(SOURCE, current.sourceKey)
                if (!automation.enabled ||
                    automation.triggers.none { it.type == TriggerType.CALENDAR }
                ) {
                    requestExit(
                        automation = automation,
                        reason = ExitReason.AUTOMATION_DISABLED,
                        occurrenceId = current.occurrenceId
                    )
                }
            } else {
                // Another stateful source owns this routine. A stale calendar
                // key may not authorize an exit for that foreign occurrence.
                activeStates.remove(automationId)
                activeStore.clearAutomation(SOURCE, automationId)
            }
        }
    }

    private suspend fun handleAutomation(
        automation: Automation,
        events: List<CalendarEvent>,
        now: Long
    ) {
        val triggers = automation.triggers.withIndex()
            .filter { (_, trigger) -> trigger.type == TriggerType.CALENDAR }
        val processed = processedEvents.getOrPut(automation.id) {
            Collections.newSetFromMap(ConcurrentHashMap())
        }
        val processedCreatedIds = processedCreated.getOrPut(automation.id) {
            Collections.newSetFromMap(ConcurrentHashMap())
        }
        var changed = false

        triggers.forEach { indexedTrigger ->
            val trigger = indexedTrigger.value
            val eventType = trigger.config["event"] ?: "EVENT_START"
            val beforeMinutes = trigger.config["beforeMinutes"]?.toLongOrNull() ?: 0L
            val matching = events.filter { event ->
                matchesTrigger(trigger.config, event)
            }

            when (eventType) {
                "EVENT_START" -> {
                    matching.sortedBy { it.start }.forEach { event ->
                        val fireAt = event.start - beforeMinutes * 60_000L
                        val occurrence = Occurrence(event.id, event.start)
                        if (
                            now >= fireAt &&
                            now < event.end &&
                            !processed.contains(occurrence)
                        ) {
                            // One automation owns at most one stateful lifecycle.
                            // Do not consume a second overlapping occurrence while
                            // the first still owns its exit behavior.
                            val current = runtimeStore.current(automation.id)
                            if (current != null) return@forEach

                            val triggerIndices = matchingTriggerIndicesForEvent(
                                automation = automation,
                                event = event,
                                eventType = eventType,
                                now = now,
                            )
                            if (triggerIndices.isEmpty()) return@forEach

                            activateStart(
                                automation = automation,
                                event = event,
                                triggerIndices = triggerIndices,
                                now = now
                            )
                            // Preserve the historical once-per-occurrence policy
                            // even when another admission gate intentionally
                            // skips the main action chain.
                            processed.add(occurrence)
                            changed = true
                        }
                    }
                }

                "EVENT_END" -> {
                    matching.forEach { event ->
                        val occurrence = Occurrence(event.id, event.start)
                        if (now >= event.end && !processed.contains(occurrence)) {
                            processed.add(occurrence)
                            changed = true
                            fireOneShot(
                                automation = automation,
                                triggerIndices = matchingTriggerIndicesForEvent(
                                    automation = automation,
                                    event = event,
                                    eventType = eventType,
                                    now = now,
                                ),
                                occurredAtEpochMs = now,
                                eventIdentity = "calendar:$eventType:${event.id}:${event.start}",
                            )
                        }
                    }
                }

                else -> { // EVENT_CREATED
                    matching.forEach { event ->
                        if (!processedCreatedIds.contains(event.id)) {
                            processedCreatedIds.add(event.id)
                            changed = true
                            fireOneShot(
                                automation = automation,
                                triggerIndices = matchingTriggerIndicesForEvent(
                                    automation = automation,
                                    event = event,
                                    eventType = eventType,
                                    now = now,
                                ),
                                occurredAtEpochMs = now,
                                eventIdentity = "calendar:$eventType:${event.id}:${event.start}",
                            )
                        }
                    }
                }
            }
        }

        // EVENT_START lifecycles end only when their exact activating
        // occurrence is known to have ended or disappeared from the query.
        val activeOccurrence = activeStates[automation.id]
        if (activeOccurrence != null) {
            val stillActive = events.any { event ->
                event.id == activeOccurrence.eventId &&
                    event.start == activeOccurrence.start &&
                    now < event.end
            }
            if (!stillActive) {
                val state = runtimeStore.current(automation.id)
                if (state?.source == SOURCE) {
                    requestExit(
                        automation = automation,
                        reason = ExitReason.TRIGGER_FALSE,
                        occurrenceId = state.occurrenceId
                    )
                } else {
                    activeStates.remove(automation.id)
                    activeStore.clearAutomation(SOURCE, automation.id)
                }
                changed = true
            }
        }

        if (changed) {
            if (processed.size > MAX_PROCESSED) {
                processedEvents[automation.id] =
                    processed.toList().takeLast(MAX_PROCESSED).toMutableSet()
            }
            if (processedCreatedIds.size > MAX_PROCESSED) {
                processedCreated[automation.id] =
                    processedCreatedIds.toList().takeLast(MAX_PROCESSED).toMutableSet()
            }
        }
    }

    private suspend fun activateStart(
        automation: Automation,
        event: CalendarEvent,
        triggerIndices: Set<Int>,
        now: Long
    ) {
        val occurrence = Occurrence(event.id, event.start)
        val occurrenceId = "calendar:${automation.id}:${event.id}:${event.start}"
        executionEngine.runAutomation(
            automation = automation,
            lifecycleContext = AutomationLifecycleContext(
                occurrenceId = occurrenceId,
                source = SOURCE,
                sourceKey = encodeSourceKey(automation.id, occurrence),
                expectedEndAt = event.end
            ),
            triggerOccurrence = TriggerOccurrence(
                matchedTriggerIndices = triggerIndices,
                occurredAtEpochMs = now,
                sourceId = SOURCE,
                eventId = "calendar:EVENT_START:${event.id}:${event.start}",
            ),
        )
        val accepted = runtimeStore.current(automation.id)?.let { state ->
            state.source == SOURCE && state.occurrenceId == occurrenceId
        } == true
        if (accepted) {
            activeStates[automation.id] = occurrence
            activeStore.markActive(SOURCE, encodeSourceKey(automation.id, occurrence))
        }
    }

    private suspend fun fireOneShot(
        automation: Automation,
        triggerIndices: Set<Int>,
        occurredAtEpochMs: Long,
        eventIdentity: String,
    ) {
        if (triggerIndices.isEmpty()) return
        val dispatchAt = System.currentTimeMillis()
        val last = lastRunAt[automation.id] ?: 0L
        if (dispatchAt - last <= automation.cooldownMillis) return
        lastRunAt[automation.id] = dispatchAt
        executionEngine.runAutomation(
            automation = automation,
            completeExitOnFinish = true,
            triggerOccurrence = TriggerOccurrence(
                matchedTriggerIndices = triggerIndices,
                occurredAtEpochMs = occurredAtEpochMs,
                sourceId = SOURCE,
                eventId = eventIdentity,
            ),
        )
    }

    private suspend fun requestExit(
        automation: Automation,
        reason: ExitReason,
        occurrenceId: String
    ) {
        when (
            exitCoordinator.requestExit(
                automation = automation,
                reason = reason,
                occurrenceId = occurrenceId
            )
        ) {
            is ExitCoordinatorResult.Executed,
            ExitCoordinatorResult.NotActive,
            ExitCoordinatorResult.StaleOccurrence -> {
                activeStates.remove(automation.id)
                activeStore.clearAutomation(SOURCE, automation.id)
            }
            ExitCoordinatorResult.AlreadyInProgress,
            is ExitCoordinatorResult.RecoveryRequired -> {
                runtimeStore.current(automation.id)
                    ?.takeIf { it.source == SOURCE }
                    ?.let { state ->
                        decodeOccurrence(state.sourceKey)?.let { occurrence ->
                            activeStates[automation.id] = occurrence
                        }
                        activeStore.markActive(SOURCE, state.sourceKey)
                    }
            }
        }
    }

    private fun encodeSourceKey(automationId: String, occurrence: Occurrence): String =
        "$automationId|${occurrence.eventId}:${occurrence.start}"

    private fun decodeOccurrence(sourceKey: String): Occurrence? {
        val encoded = sourceKey.substringAfter('|', missingDelimiterValue = "")
        val eventId = encoded.substringBefore(':').toLongOrNull() ?: return null
        val start = encoded.substringAfter(':', missingDelimiterValue = "").toLongOrNull()
            ?: return null
        return Occurrence(eventId, start)
    }

    /**
     * Every CALENDAR trigger proven by this exact occurrence at this exact
     * phase. This lets one calendar event satisfy multiple filters in an ALL
     * expression without treating a different calendar event as evidence.
     */
    private fun matchingTriggerIndicesForEvent(
        automation: Automation,
        event: CalendarEvent,
        eventType: String,
        now: Long,
    ): Set<Int> = automation.triggers.mapIndexedNotNull { index, trigger ->
        if (trigger.type != TriggerType.CALENDAR) return@mapIndexedNotNull null
        if ((trigger.config["event"] ?: "EVENT_START") != eventType) {
            return@mapIndexedNotNull null
        }
        if (!matchesTrigger(trigger.config, event)) return@mapIndexedNotNull null

        val eligible = when (eventType) {
            "EVENT_START" -> {
                val beforeMinutes = trigger.config["beforeMinutes"]?.toLongOrNull() ?: 0L
                now >= event.start - beforeMinutes * 60_000L
            }
            "EVENT_END" -> now >= event.end
            else -> true // EVENT_CREATED
        }
        index.takeIf { eligible }
    }.toSet()

    private fun matchesTrigger(config: Map<String, String>, event: CalendarEvent): Boolean {
        val calendarName = config["calendar"].orEmpty().trim()
        if (calendarName.isNotEmpty() && !event.calendarName.equals(calendarName, ignoreCase = true)) {
            return false
        }
        val keyword = config["contains"].orEmpty().trim()
        if (keyword.isEmpty()) return true
        val haystack = listOf(event.title, event.location, event.description)
            .filterNotNull()
            .joinToString(" ")
        return haystack.contains(keyword, ignoreCase = true)
    }

    /** Reads the user's calendar display names once per rescan. */
    private fun calendarNames(): Map<Long, String> {
        val names = HashMap<Long, String>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    names[cursor.getLong(idCol)] = cursor.getString(nameCol) ?: ""
                }
            }
        }
        return names
    }

    /**
     * Queries event instances in a look-ahead window. [CalendarContract.Instances]
     * expands recurring events, so a "daily 8am" event is reported for every
     * occurrence inside the window.
     */
    private fun queryUpcomingEvents(): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        val begin = now - LOOK_BEHIND_MS
        val end = now + LOOK_AHEAD_MS
        val names = calendarNames()
        val events = ArrayList<CalendarEvent>()

        val uri = "content://${CalendarContract.AUTHORITY}/instances/when/$begin/$end".toUri()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION
        )
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val calCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val locCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                val descCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val calId = cursor.getLong(calCol)
                    events.add(
                        CalendarEvent(
                            id = id,
                            calendarName = names[calId] ?: "",
                            title = cursor.getString(titleCol),
                            location = cursor.getString(locCol),
                            description = cursor.getString(descCol),
                            start = cursor.getLong(beginCol),
                            end = cursor.getLong(endCol)
                        )
                    )
                }
            }
        }
        return events
    }

    /** Identifies a single occurrence of an event (recurring events share [eventId]). */
    private data class Occurrence(
        val eventId: Long,
        val start: Long
    )

    private data class CalendarEvent(
        val id: Long,
        val calendarName: String,
        val title: String?,
        val location: String?,
        val description: String?,
        val start: Long,
        val end: Long
    )

    companion object {
        private const val RESCAN_INTERVAL_MS = 60_000L
        private const val LOOK_BEHIND_MS = 2 * 60 * 60 * 1000L
        private const val LOOK_AHEAD_MS = 3 * 24 * 60 * 60 * 1000L
        private const val MAX_PROCESSED = 256
        private const val SOURCE = "calendar"
    }
}
