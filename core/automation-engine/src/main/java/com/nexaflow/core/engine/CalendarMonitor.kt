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
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Collections
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
        runCatching {
            context.contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                true,
                observer
            )
        }
        handler.postDelayed(periodicScan, RESCAN_INTERVAL_MS)
        rescan()
    }

    fun stop() {
        if (!registered) return
        registered = false
        handler.removeCallbacks(periodicScan)
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    private fun rescan() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        scope.launch {
            val automations = repository.getAutomations().first()
            val calendarTriggers = automations
                .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.CALENDAR } }
            if (calendarTriggers.isEmpty()) return@launch
            val events = queryUpcomingEvents()
            val now = System.currentTimeMillis()
            calendarTriggers.forEach { automation ->
                handleAutomation(automation, events, now)
            }
        }
    }

    private fun handleAutomation(
        automation: Automation,
        events: List<CalendarEvent>,
        now: Long
    ) {
        val triggers = automation.triggers.filter { it.type == TriggerType.CALENDAR }
        val processed = processedEvents.getOrPut(automation.id) {
            Collections.newSetFromMap(ConcurrentHashMap())
        }
        val processedCreatedIds = processedCreated.getOrPut(automation.id) {
            Collections.newSetFromMap(ConcurrentHashMap())
        }
        var changed = false

        triggers.forEach { trigger ->
            val eventType = trigger.config["event"] ?: "EVENT_START"
            val beforeMinutes = trigger.config["beforeMinutes"]?.toLongOrNull() ?: 0L
            val matching = events.filter { event ->
                matchesTrigger(trigger.config, event)
            }

            when (eventType) {
                "EVENT_START" -> {
                    matching.forEach { event ->
                        val fireAt = event.start - beforeMinutes * 60_000L
                        val occurrence = Occurrence(event.id, event.start)
                        if (now >= fireAt && !processed.contains(occurrence)) {
                            processed.add(occurrence)
                            changed = true
                            activeStates[automation.id] = occurrence
                            fire(automation)
                        }
                    }
                }
                "EVENT_END" -> {
                    matching.forEach { event ->
                        val occurrence = Occurrence(event.id, event.start)
                        if (now >= event.end && !processed.contains(occurrence)) {
                            processed.add(occurrence)
                            changed = true
                            fire(automation)
                        }
                    }
                }
                else -> { // EVENT_CREATED
                    matching.forEach { event ->
                        if (!processedCreatedIds.contains(event.id)) {
                            processedCreatedIds.add(event.id)
                            changed = true
                            fire(automation)
                        }
                    }
                }
            }
        }

        // Exit behavior: EVENT_START tasks end when the occurrence that
        // activated them has finished (or disappeared from the queried window).
        val activeOccurrence = activeStates[automation.id]
        if (activeOccurrence != null) {
            val stillActive = events.any { event ->
                event.id == activeOccurrence.eventId &&
                    event.start == activeOccurrence.start &&
                    now < event.end
            }
            if (!stillActive) {
                activeStates.remove(automation.id)
                changed = true
                scope.launch { executionEngine.runExit(automation) }
            }
        }

        if (changed) {
            // Keep the processed sets bounded to the most recent entries.
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

    private fun fire(automation: Automation) {
        val now = System.currentTimeMillis()
        val last = lastRunAt[automation.id] ?: 0L
        if (now - last <= automation.cooldownMillis) return
        lastRunAt[automation.id] = now
        scope.launch { executionEngine.runAutomation(automation) }
    }

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
    }
}
