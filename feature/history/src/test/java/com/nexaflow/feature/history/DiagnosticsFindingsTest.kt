package com.nexaflow.feature.history

import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.ExecutionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.nexaflow.domain.models.ExecutionHistoryOutcome
import com.nexaflow.domain.repositories.HistoryRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Diagnostics derivation contract: failed `*_END` / `STATE_RESTORE` results
 * surface as end-behavior findings, failed actions with config-problem
 * markers surface as config findings, and ordinary runtime errors stay out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsFindingsTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeHistory(records: List<ExecutionRecord>) : HistoryRepository {
        private val state = MutableStateFlow(records)
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = state
        override suspend fun getExecutionById(id: String): ExecutionRecord? =
            state.value.firstOrNull { it.id == id }

        override suspend fun recordExecution(record: ExecutionRecord) {
            state.value = state.value + record
        }

        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> = source()
        override fun getExecutionPaging(automationId: String): PagingSource<Int, ExecutionRecord> = source()
        override fun getExecutionPaging(
            automationId: String?,
            outcome: ExecutionHistoryOutcome?
        ): PagingSource<Int, ExecutionRecord> = source()

        private fun source(): PagingSource<Int, ExecutionRecord> =
            object : PagingSource<Int, ExecutionRecord>() {
                override fun getRefreshKey(state: PagingState<Int, ExecutionRecord>): Int? = null
                override suspend fun load(params: PagingSource.LoadParams<Int>): PagingSource.LoadResult<Int, ExecutionRecord> =
                    PagingSource.LoadResult.Page(state.value, null, null)
            }
    }

    private fun record(
        id: String,
        results: List<ActionExecutionResult>,
        automationId: String = "a1",
        automationName: String = "Task A"
    ) = ExecutionRecord(
        id = id,
        automationId = automationId,
        automationName = automationName,
        success = results.all { it.success },
        message = "run",
        executedAt = 1L,
        actionResults = results
    )

    private suspend fun findingsFor(records: List<ExecutionRecord>): List<ConfigFailureFinding> {
        val viewModel = DiagnosticsViewModel(FakeHistory(records))
        viewModel.findings.first { it.isNotEmpty() }
        return viewModel.findings.value
    }

    @Test
    fun `failed end-behavior result surfaces as end-behavior finding`() = runTest(dispatcher) {
        val findings = findingsFor(
            listOf(
                record(
                    id = "r1",
                    results = listOf(
                        ActionExecutionResult(
                            actionType = "SYSTEM_BRIGHTNESS_END",
                            success = false,
                            message = "No captured state to restore for SYSTEM_BRIGHTNESS",
                            durationMs = 5
                        )
                    )
                )
            )
        )
        val finding = findings.single()
        assertEquals(listOf("SYSTEM_BRIGHTNESS_END: No captured state to restore for SYSTEM_BRIGHTNESS"), finding.endBehaviorFailures)
        assertTrue(finding.failures.isEmpty())
        assertTrue(finding.hasProblems)
    }

    @Test
    fun `failed state restore surfaces as end-behavior finding`() = runTest(dispatcher) {
        val findings = findingsFor(
            listOf(
                record(
                    id = "r2",
                    results = listOf(
                        ActionExecutionResult(
                            actionType = "STATE_RESTORE",
                            success = false,
                            message = "restore failed",
                            durationMs = 5
                        )
                    )
                )
            )
        )
        val finding = findings.single()
        assertEquals(listOf("STATE_RESTORE: restore failed"), finding.endBehaviorFailures)
    }

    @Test
    fun `config failure markers surface as config findings`() = runTest(dispatcher) {
        val findings = findingsFor(
            listOf(
                record(
                    id = "r3",
                    results = listOf(
                        ActionExecutionResult(
                            actionType = "SYSTEM_SCREEN_TIMEOUT",
                            success = false,
                            message = "Invalid value: abc",
                            durationMs = 5
                        )
                    )
                )
            )
        )
        val finding = findings.single()
        assertEquals(listOf("SYSTEM_SCREEN_TIMEOUT: Invalid value: abc"), finding.failures)
        assertTrue(finding.endBehaviorFailures.isEmpty())
    }

    @Test
    fun `ordinary runtime failures are not mislabelled as config problems`() = runTest(dispatcher) {
        val viewModel = DiagnosticsViewModel(
            FakeHistory(
                listOf(
                    record(
                        id = "r4",
                        results = listOf(
                            ActionExecutionResult(
                                actionType = "SYSTEM_FLASHLIGHT",
                                success = false,
                                message = "camera in use",
                                durationMs = 5
                            )
                        )
                    )
                )
            )
        )
        // Give the collector a chance; findings must stay empty.
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.findings.value.isEmpty())
    }

    @Test
    fun `a task can carry both buckets in one finding`() = runTest(dispatcher) {
        val findings = findingsFor(
            listOf(
                record(
                    id = "r5",
                    results = listOf(
                        ActionExecutionResult(
                            actionType = "SYSTEM_VOLUME_END",
                            success = false,
                            message = "No captured state to restore for SYSTEM_VOLUME",
                            durationMs = 5
                        ),
                        ActionExecutionResult(
                            actionType = "SYSTEM_BRIGHTNESS",
                            success = false,
                            message = "Unknown value for mode",
                            durationMs = 5
                        )
                    )
                )
            )
        )
        val finding = findings.single()
        assertEquals(1, finding.endBehaviorFailures.size)
        assertEquals(1, finding.failures.size)
    }
}
