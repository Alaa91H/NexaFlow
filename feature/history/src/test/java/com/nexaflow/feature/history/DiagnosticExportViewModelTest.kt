package com.nexaflow.feature.history

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.logging.ErrorLogEntry
import com.nexaflow.core.logging.ExecutionTimelineEntry
import com.nexaflow.core.logging.LogStore
import com.nexaflow.core.logging.PerformanceMetric
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Device-facing half of NFA-09: the exporter must write one bounded, redacted
 * file into the FileProvider cache directory and report failure instead of
 * throwing when a store is unavailable. The bundle contents themselves are
 * covered by [DiagnosticBundleTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosticExportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun restoreMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun exportWritesARedactedBundleIntoTheCacheDirectory() = runTest(dispatcher) {
        val viewModel = DiagnosticExportViewModel(
            historyRepository = FakeHistory(listOf(record())),
            automationRepository = FakeAutomations(listOf(automation())),
            logStore = FakeLogStore(),
            context = context
        )

        var exported: File? = null
        viewModel.export { exported = it }
        advanceUntilIdle()

        val file = requireNotNull(exported)
        try {
            assertTrue(file.exists())
            assertEquals(
                File(context.cacheDir, DiagnosticExportViewModel.DIAGNOSTICS_DIRECTORY).absolutePath,
                file.parentFile?.absolutePath
            )
            assertTrue(file.name.startsWith("nexaflow-diagnostics-"))
            assertTrue(file.name.endsWith(".json"))

            val bundle = file.readText()
            assertTrue(bundle.contains("nexaflow.diagnostics/1"))
            assertTrue(bundle.contains("\"executionChannel\": \"ROOT\""))
            assertTrue(bundle.contains("[REDACTED]"))
            assertFalse(bundle.contains("xxxx-fixture-xxxx"))
            assertFalse(bundle.contains("run-secret"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun exportReportsFailureWhenAStoreIsUnavailable() = runTest(dispatcher) {
        val failingHistory = object : FakeHistory(emptyList()) {
            override fun getExecutionHistory(): Flow<List<ExecutionRecord>> =
                flow { throw IllegalStateException("history store unavailable") }
        }
        val viewModel = DiagnosticExportViewModel(
            historyRepository = failingHistory,
            automationRepository = FakeAutomations(emptyList()),
            logStore = FakeLogStore(),
            context = context
        )

        var called = false
        var exported: File? = File("sentinel")
        viewModel.export {
            called = true
            exported = it
        }
        advanceUntilIdle()

        assertTrue(called)
        assertNull(exported)
    }

    private fun automation() = Automation(
        id = "task-1",
        name = "token=xxxx-fixture-xxxx",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "SYSTEM",
        priority = 1,
        enabled = true,
        triggers = listOf(Trigger(TriggerType.TIME, emptyMap())),
        actions = emptyList(),
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun record() = ExecutionRecord(
        id = "run-1",
        automationId = "task-1",
        automationName = "Task task-1",
        success = true,
        message = "Authorization: Bearer run-secret-token",
        executedAt = 10L,
        channel = "ROOT",
        actionResults = listOf(
            ActionExecutionResult(
                actionType = "SYSTEM_BRIGHTNESS",
                success = true,
                message = "done",
                durationMs = 5L
            )
        )
    )

    private open class FakeHistory(records: List<ExecutionRecord>) : HistoryRepository {
        private val state = MutableStateFlow(records)

        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = state
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> = emptyPage()
        override suspend fun getExecutionById(id: String): ExecutionRecord? =
            state.value.firstOrNull { it.id == id }
        override suspend fun recordExecution(record: ExecutionRecord) {
            state.value = state.value + record
        }

        private fun emptyPage(): PagingSource<Int, ExecutionRecord> =
            object : PagingSource<Int, ExecutionRecord>() {
                override fun getRefreshKey(state: PagingState<Int, ExecutionRecord>): Int? = null
                override suspend fun load(
                    params: LoadParams<Int>
                ): LoadResult<Int, ExecutionRecord> = LoadResult.Page(emptyList(), null, null)
            }
    }

    private class FakeAutomations(items: List<Automation>) : AutomationRepository {
        private val state = MutableStateFlow(items)

        override fun getAutomations(): Flow<List<Automation>> = state
        override suspend fun getAutomationById(id: String): Automation? =
            state.value.firstOrNull { it.id == id }

        override suspend fun saveAutomation(automation: Automation) {
            state.value = state.value.filterNot { it.id == automation.id } + automation
        }

        override suspend fun deleteAutomation(automation: Automation) {
            state.value = state.value.filterNot { it.id == automation.id }
        }

        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) {
            state.value = state.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        }
    }

    private class FakeLogStore : LogStore {
        private val timeline = MutableStateFlow(
            listOf(
                ExecutionTimelineEntry(
                    id = "t1",
                    automationId = "task-1",
                    automationName = "Task task-1",
                    kind = "RUN",
                    success = true,
                    message = "ran",
                    startedAt = 5L,
                    durationMs = 2L
                )
            )
        )
        private val errors = MutableStateFlow(
            listOf(
                ErrorLogEntry(
                    id = "e1",
                    source = "engine",
                    message = "password=hunter2",
                    stackTrace = null,
                    timestamp = 6L
                )
            )
        )
        private val metrics = MutableStateFlow(emptyList<PerformanceMetric>())

        override fun timeline(): Flow<List<ExecutionTimelineEntry>> = timeline
        override fun errors(): Flow<List<ErrorLogEntry>> = errors
        override fun metrics(): Flow<List<PerformanceMetric>> = metrics
        override suspend fun recordExecution(entry: ExecutionTimelineEntry) = Unit
        override suspend fun recordError(entry: ErrorLogEntry) = Unit
        override suspend fun recordMetric(metric: PerformanceMetric) = Unit
        override suspend fun clear() = Unit
    }
}
