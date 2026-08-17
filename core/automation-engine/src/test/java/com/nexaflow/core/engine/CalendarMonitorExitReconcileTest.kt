package com.nexaflow.core.engine

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver

/** One calendar occurrence the [FakeCalendarProvider] serves. */
internal data class FakeCalendarEvent(
    val id: Long,
    val title: String,
    val start: Long,
    val end: Long
)

/**
 * Minimal in-memory calendar provider so [CalendarMonitor]'s rescan sees a
 * deterministic event set. Serves the calendars list (id 1 = "Work") and every
 * configured occurrence from any `instances/when` window.
 */
internal class FakeCalendarProvider(
    private val events: List<FakeCalendarEvent>
) : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val path = uri.path ?: return null
        return when {
            path.startsWith("/calendars") -> calendarsCursor()
            path.contains("/instances/when") -> instancesCursor()
            else -> null
        }
    }

    private fun calendarsCursor(): Cursor = MatrixCursor(
        arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
    ).apply {
        addRow(arrayOf<Any>(1L, "Work"))
    }

    private fun instancesCursor(): Cursor = MatrixCursor(
        arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION
        )
    ).apply {
        events.forEach { event ->
            addRow(
                arrayOf<Any>(event.id, 1L, event.title, event.start, event.end, "", "")
            )
        }
    }
}

/**
 * Calendar exit-reliability contract: a task triggered by a calendar event
 * BEFORE a process/service restart must still fire its exit behavior when the
 * activating occurrence ends. The monitor's in-memory active occurrences die
 * with the process, so it re-arms from the durable [ActiveTriggerStore] ledger
 * on start and then rescans — so an occurrence that already ended (or vanished
 * from the window) while the process was down fires the missed exit on that
 * first rescan instead of waiting for a calendar change.
 *
 * Scenario: a task fired for "Meeting" (occurrence 7 at start), then the
 * process was killed while the event was still running. The event ends during
 * downtime, then the app restarts. The fresh monitor must see the durable
 * mark, find the occurrence no longer active, and run the exit.
 */
@RunWith(RobolectricTestRunner::class)
class CalendarMonitorExitReconcileTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Robolectric shares one Application (and its DataStore cache) across
        // test methods, so reset the calendar source for isolation.
        runBlocking {
            ActiveTriggerStore(context).clearSource("calendar")
            ActiveExecutionStore(context).clear("cal-task")
        }
        // The monitor's rescan gates on READ_CALENDAR; grant it explicitly so
        // the reconcile path is exercised regardless of manifest merging.
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CALENDAR)
    }

    private fun calendarAutomation(id: String): com.nexaflow.domain.models.Automation =
        testAutomation(
            id = id,
            triggers = listOf(
                Trigger(TriggerType.CALENDAR, mapOf("event" to "EVENT_START"))
            )
        )

    private fun registerProvider(events: List<FakeCalendarEvent>) {
        ShadowContentResolver.registerProviderInternal(
            CalendarContract.AUTHORITY,
            FakeCalendarProvider(events)
        )
    }

    private fun monitorFor(
        repository: FakeRepository,
        engine: com.nexaflow.core.execution.ExecutionEngine,
        store: ActiveTriggerStore
    ): CalendarMonitor = CalendarMonitor(
        context = context,
        repository = repository,
        executionEngine = engine,
        activeStore = store,
        scope = CoroutineScope(Dispatchers.Default)
    )

    @Test
    fun `restart with the activating occurrence already over fires the missed exit on init`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(calendarAutomation("cal-task")))
        val store = ActiveTriggerStore(context)
        // The task fired for occurrence (event 7, start 1234567890000), then
        // the process died. The calendar now returns nothing — the occurrence
        // ended during downtime.
        store.markActive("calendar", "cal-task|7:1234567890000")
        ActiveExecutionStore(context).markStarted("cal-task")
        registerProvider(emptyList())

        val monitor = monitorFor(repository, engine, store)
        monitor.initialize()

        // The first rescan finds the restored occurrence gone and fires the
        // missed exit.
        waitUntil { history.exits.any { it == EXIT_NOOP_MARKER } }
        waitUntil { store.activeKeys("calendar").isEmpty() }
        monitor.stop()
    }

    @Test
    fun `restart while the activating occurrence is still running keeps the task active`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(calendarAutomation("cal-task")))
        val store = ActiveTriggerStore(context)
        // The event is still running: starts in a minute, ends an hour later.
        val now = System.currentTimeMillis()
        val start = now + 60_000L
        val end = start + 3_600_000L
        store.markActive("calendar", "cal-task|7:$start")
        registerProvider(listOf(FakeCalendarEvent(7L, "Meeting", start, end)))

        val monitor = monitorFor(repository, engine, store)
        monitor.initialize()

        // Give the async re-arm + rescan a moment, then assert no exit ran and
        // the mark survives.
        Thread.sleep(300)
        assertTrue(
            "no exit while the activating occurrence is still active",
            history.exits.none { it == EXIT_NOOP_MARKER }
        )
        assertTrue(
            "active mark survives while the occurrence is active",
            store.activeKeys("calendar").isNotEmpty()
        )
        monitor.stop()
    }

    @Test
    fun `stale mark for a disabled automation is pruned on restart`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(
            listOf(calendarAutomation("cal-task").copy(enabled = false))
        )
        val store = ActiveTriggerStore(context)
        store.markActive("calendar", "cal-task|7:1234567890000")
        registerProvider(emptyList())

        val monitor = monitorFor(repository, engine, store)
        monitor.initialize()

        // Give the async prune a moment, then assert nothing fired and the
        // stale mark is gone.
        Thread.sleep(300)
        assertTrue(
            "disabled task must not fire a stale exit",
            history.exits.none { it == EXIT_NOOP_MARKER }
        )
        waitUntil { store.activeKeys("calendar").isEmpty() }
        monitor.stop()
    }
}
