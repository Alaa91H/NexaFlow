package com.nexaflow.feature.history

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.nexaflow.domain.models.ExecutionHistoryOutcome
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import com.nexaflow.domain.models.Automation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the parseable blocked-call record format written by
 * NexaCallScreeningService ("Call blocked: <masked>|<category>|<rules>")
 * through the ViewModel that consumes it, including legacy-row fallbacks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlockedCallsParsingTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun blockedRecord(
        id: String,
        message: String,
        automationName: String = "Blocker",
        automationId: String = "a-$id",
        at: Long = 1L
    ) = ExecutionRecord(
        id = id,
        automationId = automationId,
        automationName = automationName,
        success = true,
        message = message,
        executedAt = at
    )

    private open class FakeHistory(
        records: List<ExecutionRecord>
    ) : HistoryRepository {
        private val state = MutableStateFlow(records)
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = state
        override suspend fun getExecutionById(id: String): ExecutionRecord? =
            state.value.firstOrNull { it.id == id }

        override suspend fun recordExecution(record: ExecutionRecord) {
            state.value = state.value + record
        }

        private fun source(): PagingSource<Int, ExecutionRecord> =
            object : PagingSource<Int, ExecutionRecord>() {
                override fun getRefreshKey(state: PagingState<Int, ExecutionRecord>): Int? = null
                override suspend fun load(params: PagingSource.LoadParams<Int>): PagingSource.LoadResult<Int, ExecutionRecord> =
                    PagingSource.LoadResult.Page(state.value, null, null)
            }

        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> = source()
        override fun getExecutionPaging(automationId: String): PagingSource<Int, ExecutionRecord> = source()
        override fun getExecutionPaging(
            automationId: String?,
            outcome: ExecutionHistoryOutcome?
        ): PagingSource<Int, ExecutionRecord> = source()
    }

    private class FakeAutomations(
        private val automations: List<Automation> = emptyList()
    ) : AutomationRepository {
        override fun getAutomations(): Flow<List<Automation>> =
            MutableStateFlow(automations)

        override suspend fun getAutomationById(id: String): Automation? =
            automations.firstOrNull { it.id == id }

        override suspend fun saveAutomation(automation: Automation) = Unit
        override suspend fun deleteAutomation(automation: Automation) = Unit
        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) = Unit
    }

    @Test
    fun `enriched record yields masked number category and rules`() = runTest(dispatcher) {
        val viewModel = BlockedCallsViewModel(
            FakeHistory(
                listOf(
                    blockedRecord(
                        id = "r1",
                        message = "Call blocked: ******7890|UNKNOWN|Spam shield;Night guard",
                        automationName = "Spam shield;Night guard"
                    )
                )
            ),
            FakeAutomations()
        )
        val entry = viewModel.entries.first { it.isNotEmpty() }.single()
        assertEquals("******7890", entry.maskedNumber)
        assertEquals("UNKNOWN", entry.callerCategory)
        assertEquals(listOf("Spam shield", "Night guard"), entry.ruleNames)
    }

    @Test
    fun `legacy record falls back to task name rules and raw number`() = runTest(dispatcher) {
        val viewModel = BlockedCallsViewModel(
            FakeHistory(
                listOf(blockedRecord(id = "r2", message = "Call blocked: ******1234", automationName = "Old rule"))
            ),
            FakeAutomations()
        )
        val entry = viewModel.entries.first { it.isNotEmpty() }.single()
        assertEquals("******1234", entry.maskedNumber)
        assertEquals(null, entry.callerCategory)
        assertEquals(listOf("Old rule"), entry.ruleNames)
    }

    @Test
    fun `rule filter matches enriched multi-rule records`() = runTest(dispatcher) {
        val viewModel = BlockedCallsViewModel(
            FakeHistory(
                listOf(
                    blockedRecord(
                        id = "r3",
                        message = "Call blocked: ******0000|CONTACT|Alpha;Beta",
                        automationName = "Alpha;Beta",
                        at = 3L
                    ),
                    blockedRecord(
                        id = "r4",
                        message = "Call blocked: ******1111|ANY|Gamma",
                        automationName = "Gamma",
                        at = 4L
                    )
                )
            ),
            FakeAutomations()
        )
        viewModel.selectRule("Beta")
        val visible = viewModel.entries.first { rows -> rows.isNotEmpty() && rows.all { it.id == "r3" } }
        assertEquals(listOf("r3"), visible.map { it.id })
    }

    @Test
    fun `non blocked records never appear in the log`() = runTest(dispatcher) {
        val viewModel = BlockedCallsViewModel(
            FakeHistory(
                listOf(
                    blockedRecord(id = "r5", message = "Call blocked: ******2222|ANY|X"),
                    blockedRecord(id = "r6", message = "Ran 3 actions"),
                    blockedRecord(id = "r7", message = "Call answered: ******3333")
                )
            ),
            FakeAutomations()
        )
        val visible = viewModel.entries.first { it.size == 1 }
        assertEquals(listOf("r5"), visible.map { it.id })
    }

    @Test
    fun `latestActionSummaries falls back to the record message without action results`() = runTest(dispatcher) {
        val viewModel = BlockedCallsViewModel(
            FakeHistory(
                listOf(
                    blockedRecord(id = "b1", message = "Call blocked: ******4444|ANY|Spam shield"),
                    ExecutionRecord(
                        id = "run1",
                        automationId = "a-b1",
                        automationName = "Spam shield",
                        success = true,
                        message = "ok",
                        executedAt = 10L
                    )
                )
            ),
            FakeAutomations(listOf(automation("a-b1", "Spam shield")))
        )
        assertEquals(listOf("ok"), viewModel.latestActionSummaries("Spam shield"))
    }

    private fun automation(id: String, name: String) = Automation(
        id = id,
        name = name,
        description = "",
        icon = "",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "",
        priority = 0,
        enabled = true,
        triggers = emptyList(),
        actions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `latestActionSummaries is empty for unknown rules`() = runTest(dispatcher) {
        val viewModel = BlockedCallsViewModel(FakeHistory(emptyList()), FakeAutomations())
        assertTrue(viewModel.latestActionSummaries("Nope").isEmpty())
    }
}
