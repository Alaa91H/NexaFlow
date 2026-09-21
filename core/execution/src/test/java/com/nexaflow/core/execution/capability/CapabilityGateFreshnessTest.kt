package com.nexaflow.core.execution.capability

import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityAvailabilityReport
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression suite for the reported "many tasks skipped" defect.
 *
 * The whole-run capability gate used to block on ANY inadmissible snapshot —
 * including one observed hours earlier while the process sat in the
 * background, and one not yet observed at all (startup race, before the
 * explicit neverObserved admit rule existed here these tests pin the
 * behavior at the engine level too). Each test names the exact user-visible
 * symptom it forbids.
 */
@RunWith(RobolectricTestRunner::class)
class CapabilityGateFreshnessTest {

    private lateinit var context: Context

    private class RecordingHistoryRepository : HistoryRepository {
        val records = mutableListOf<ExecutionRecord>()
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(emptyList())
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> = TestPaging.empty()
        override suspend fun getExecutionById(id: String): ExecutionRecord? = null
        override suspend fun recordExecution(record: ExecutionRecord) {
            records += record
        }
    }

    private class ActionCapturingHandler : com.nexaflow.core.execution.handler.ActionHandler {
        override val supportedTypes = setOf(ActionType.SYSTEM_OPEN_URL)
        val executed = mutableListOf<Action>()
        override suspend fun execute(
            action: Action,
            ctx: com.nexaflow.core.execution.handler.ActionExecutionContext
        ) = com.nexaflow.core.rom.model.SystemControlResult.ok("opened").also { executed += action }
    }

    private fun automation() = Automation(
        id = "cap-gate-test",
        name = "Gate task",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = emptyList(),
        actions = listOf(Action(type = ActionType.SYSTEM_OPEN_URL, config = mapOf("url" to "https://example.com"))),
        createdAt = 0L,
        updatedAt = 0L
    )

    /** INTENT_LAUNCH available → the workflow requirement resolves admissible. */
    private fun freshAdmissibleSnapshot(observedAt: Long) = CapabilitySnapshot(
        reports = mapOf(
            CapabilityId.INTENT_LAUNCH to CapabilityAvailabilityReport(
                capability = CapabilityId.INTENT_LAUNCH,
                availability = CapabilityAvailability.AVAILABLE,
                backends = emptyList()
            )
        ),
        observedAtMs = observedAt
    )

    /** INTENT_LAUNCH unavailable → inadmissible, but only fatal when fresh. */
    private fun snapshotWithIntentUnavailable(observedAt: Long) = CapabilitySnapshot(
        reports = mapOf(
            CapabilityId.INTENT_LAUNCH to CapabilityAvailabilityReport(
                capability = CapabilityId.INTENT_LAUNCH,
                availability = CapabilityAvailability.UNAVAILABLE,
                backends = emptyList()
            )
        ),
        observedAtMs = observedAt
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun engine(
        history: RecordingHistoryRepository,
        snapshot: CapabilitySnapshot?,
        invalidator: (() -> Unit)? = null
    ): ExecutionEngine = ExecutionEngine(
        context = context,
        historyRepository = history,
        notificationPreferences = NotificationPreferences(context),
        actionRegistry = com.nexaflow.core.execution.handler.ActionRegistry.from(listOf(ActionCapturingHandler())),
        capabilitySnapshotProvider = snapshot?.let { s -> ({ s }) },
        capabilitySnapshotInvalidator = invalidator
    )

    @Test
    fun `stale inadmissible snapshot admits the run instead of blocking it`() = runBlocking {
        val history = RecordingHistoryRepository()
        val handler = ActionCapturingHandler()
        // Observed an hour ago — far beyond the freshness window.
        val stale = snapshotWithIntentUnavailable(observedAt = System.currentTimeMillis() - 3_600_000L)
        val engine = engine(history, stale)

        val record = engine.runAutomation(automation())

        assertTrue(
            "a stale snapshot must never block the run; the per-action live check decides",
            record.success
        )
        assertEquals("record=${record.message}", listOf("opened"), record.actionResults.map { it.message })
        assertTrue(history.records.none { it.message.startsWith("Blocked:") })
    }

    @Test
    fun `never-observed snapshot admits the run (startup race)`() = runBlocking {
        val history = RecordingHistoryRepository()
        val handler = ActionCapturingHandler()
        val engine = engine(history, CapabilitySnapshot())

        val record = engine.runAutomation(automation())

        assertTrue(record.success)
        assertEquals("record=${record.message}", listOf("opened"), record.actionResults.map { it.message })
    }

    @Test
    fun `fresh inadmissible snapshot still blocks with an explicit record`() = runBlocking {
        val history = RecordingHistoryRepository()
        val fresh = snapshotWithIntentUnavailable(observedAt = System.currentTimeMillis())
        var invalidated = 0
        val engine = engine(history, fresh, { invalidated++ })

        val record = engine.runAutomation(automation())

        assertFalse(
            "a genuinely observed, fresh unavailability remains an honest block",
            record.success
        )
        assertTrue(record.message.startsWith("Blocked:"))
        assertEquals(
            "a fresh block must schedule a targeted refresh so the next run sees a new grant",
            1,
            invalidated
        )
    }

    @Test
    fun `fresh admissible snapshot runs normally`() = runBlocking {
        val history = RecordingHistoryRepository()
        val handler = ActionCapturingHandler()
        val engine = engine(history, freshAdmissibleSnapshot(System.currentTimeMillis()))

        val record = engine.runAutomation(automation())

        assertTrue(record.success)
        assertEquals("record=${record.message}", listOf("opened"), record.actionResults.map { it.message })
    }
}

/** Always-empty paging source (Paging 3.4+ requires an explicit instance). */
private object TestPaging {
    fun empty(): PagingSource<Int, ExecutionRecord> =
        object : PagingSource<Int, ExecutionRecord>() {
            override fun getRefreshKey(state: androidx.paging.PagingState<Int, ExecutionRecord>): Int? = null
            override suspend fun load(
                params: PagingSource.LoadParams<Int>
            ): PagingSource.LoadResult<Int, ExecutionRecord> =
                PagingSource.LoadResult.Page(emptyList(), null, null)
        }
}
