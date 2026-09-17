package com.nexaflow.data.repository

import androidx.paging.PagingSource
import com.nexaflow.core.database.AutomationDao
import com.nexaflow.core.database.AutomationEntity
import com.nexaflow.core.database.ExecutionDao
import com.nexaflow.core.database.ExecutionRecordEntity
import com.nexaflow.core.database.GlobalVariableEntity
import com.nexaflow.core.database.VariableDao
import com.nexaflow.core.security.SecureStorage
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionHistoryOutcome
import com.nexaflow.domain.models.GlobalVariable
import com.nexaflow.domain.variables.RuntimeValue
import com.nexaflow.domain.variables.RuntimeValueCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises every repository facade against in-memory DAO fakes: persistence
 * round-trips, sensitive-variable encryption routing through SecureStorage,
 * the outcome-filter paging branches, and delete-side secret cleanup.
 */
class RepositoryImplTest {

    // region fakes

    private class FakeVariableDao : VariableDao {
        val rows = MutableStateFlow<List<GlobalVariableEntity>>(emptyList())

        override fun getAllVariables(): Flow<List<GlobalVariableEntity>> = rows
        override fun getAllVariablesPaged(): PagingSource<Int, GlobalVariableEntity> =
            error("paging source covered by MappedPagingSourceTest")

        override suspend fun getVariablesOnce(): List<GlobalVariableEntity> = rows.value
        override suspend fun upsert(variable: GlobalVariableEntity) {
            rows.value = rows.value.filterNot { it.id == variable.id } + variable
        }

        override suspend fun deleteById(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
        }
    }

    private class RecordingSecureStorage : SecureStorage {
        val map = HashMap<String, String>()
        val removed = mutableListOf<String>()
        override suspend fun get(key: String): String? = map[key]
        override suspend fun put(key: String, value: String) {
            map[key] = value
        }

        override suspend fun remove(key: String) {
            map.remove(key)
            removed.add(key)
        }

        override suspend fun clear() = map.clear()
    }

    private class FakeAutomationDao : AutomationDao {
        val rows = MutableStateFlow<List<AutomationEntity>>(emptyList())
        var statusUpdates = mutableListOf<Pair<String, Boolean>>()

        override fun getAllAutomations(): Flow<List<AutomationEntity>> = rows
        override suspend fun getAutomationById(id: String): AutomationEntity? =
            rows.value.firstOrNull { it.id == id }

        override suspend fun insertAutomation(automation: AutomationEntity) {
            rows.value = rows.value.filterNot { it.id == automation.id } + automation
        }

        override suspend fun updateAutomation(automation: AutomationEntity) {
            insertAutomation(automation)
        }

        override suspend fun deleteAutomation(automation: AutomationEntity) {
            rows.value = rows.value.filterNot { it.id == automation.id }
        }

        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) {
            statusUpdates.add(id to enabled)
            rows.value = rows.value.map {
                if (it.id == id) it.copy(enabled = enabled) else it
            }
        }
    }

    private open class FakeExecutionDao : ExecutionDao {
        val rows = MutableStateFlow<List<ExecutionRecordEntity>>(emptyList())
        var inserted: ExecutionRecordEntity? = null
        var pruneOlderCalls = 0
        var pruneExcessCalls = 0

        override fun getAllExecutions(): Flow<List<ExecutionRecordEntity>> = rows
        override fun getLatestExecutions(): Flow<List<ExecutionRecordEntity>> = flowOf(
            rows.value.groupBy { it.automationId }
                .map { (_, group) -> group.maxBy { it.executedAt } }
        )

        override fun getExecutionsPaged(): PagingSource<Int, ExecutionRecordEntity> =
            error("paging source covered by MappedPagingSourceTest")

        override fun getExecutionsPagedForAutomation(
            automationId: String
        ): PagingSource<Int, ExecutionRecordEntity> = error("paging source covered by MappedPagingSourceTest")

        override fun getExecutionsPagedFiltered(
            automationId: String?,
            success: Boolean?
        ): PagingSource<Int, ExecutionRecordEntity> = error("paging source covered by MappedPagingSourceTest")

        override fun getExecutionsPagedSkipped(
            automationId: String?,
            skipMessageLike: String
        ): PagingSource<Int, ExecutionRecordEntity> = error("paging source covered by MappedPagingSourceTest")

        override suspend fun getLatestExecution(): ExecutionRecordEntity? =
            rows.value.maxByOrNull { it.executedAt }

        override suspend fun getExecutionById(id: String): ExecutionRecordEntity? =
            rows.value.firstOrNull { it.id == id }

        override suspend fun insertExecution(record: ExecutionRecordEntity) {
            inserted = record
            rows.value = rows.value.filterNot { it.id == record.id } + record
        }

        override suspend fun pruneOlderThan(cutoffTimestamp: Long): Int {
            pruneOlderCalls++
            return 0
        }

        override suspend fun pruneExcess(keepCount: Int): Int {
            pruneExcessCalls++
            return 0
        }

        override suspend fun clearHistory() {
            rows.value = emptyList()
        }
    }

    // endregion

    // region VariableRepositoryImpl

    @Test
    fun `variable round trip persists plain values and re-reads them`() = runTest {
        val dao = FakeVariableDao()
        val storage = RecordingSecureStorage()
        val repository = VariableRepositoryImpl(dao, storage)

        repository.saveVariable(variable("v1", value = "hello", sensitive = false))

        val loaded = repository.getVariables().first().single()
        assertEquals("hello", loaded.value)
        assertNull(loaded.serializedValue)
        assertFalse(loaded.sensitive)
        assertTrue("secret store must stay empty for plain values", storage.map.isEmpty())
    }

    @Test
    fun `sensitive variable bypasses the database and encrypts through SecureStorage`() = runTest {
        val dao = FakeVariableDao()
        val storage = RecordingSecureStorage()
        val repository = VariableRepositoryImpl(dao, storage)

        repository.saveVariable(variable("s1", value = "hunter2", sensitive = true))

        val row = dao.rows.value.single()
        assertEquals("db never contains the plaintext", "*encrypted*", row.value)
        assertEquals("hunter2", storage.map.getValue("variable_s1"))

        val loaded = repository.getVariables().first().single()
        assertEquals("hunter2", loaded.value)
        assertTrue(loaded.sensitive)
    }

    @Test
    fun `sensitive typed variable keeps its typed json only in SecureStorage`() = runTest {
        val dao = FakeVariableDao()
        val storage = RecordingSecureStorage()
        val repository = VariableRepositoryImpl(dao, storage)
        val typed = RuntimeValueCodec.encode(RuntimeValue.IntValue(42))

        repository.saveVariable(
            GlobalVariable(
                id = "t1",
                name = "answer",
                value = "display",
                updatedAt = 5L,
                version = 1L,
                serializedValue = typed,
                sensitive = true
            )
        )

        val row = dao.rows.value.single()
        assertEquals("*encrypted*", row.value)
        assertEquals("*encrypted*", row.serializedValue)
        assertEquals(typed, storage.map.getValue("variable_t1"))

        val loaded = repository.getVariablesOnce().single()
        assertEquals("42", loaded.value)
        assertEquals(typed, loaded.serializedValue)
    }

    @Test
    fun `legacy sensitive row without marker decrypts to legacy text`() = runTest {
        val dao = FakeVariableDao()
        val storage = RecordingSecureStorage()
        storage.map["variable_l1"] = "old secret"
        dao.rows.value = listOf(
            GlobalVariableEntity(
                id = "l1",
                name = "legacy",
                value = "*encrypted*",
                updatedAt = 1L,
                serializedValue = null,
                sensitive = true
            )
        )

        val loaded = VariableRepositoryImpl(dao, storage).getVariablesOnce().single()
        assertEquals("old secret", loaded.value)
        assertNull(loaded.serializedValue)
    }

    @Test
    fun `missing sensitive secret degrades to empty display value`() = runTest {
        val dao = FakeVariableDao()
        dao.rows.value = listOf(
            GlobalVariableEntity(
                id = "g1",
                name = "ghost",
                value = "*encrypted*",
                updatedAt = 1L,
                sensitive = true
            )
        )

        val loaded = VariableRepositoryImpl(dao, RecordingSecureStorage()).getVariables().first().single()
        assertEquals("", loaded.value)
    }

    @Test
    fun `deleteVariable removes the row and the stored secret`() = runTest {
        val dao = FakeVariableDao()
        val storage = RecordingSecureStorage()
        val repository = VariableRepositoryImpl(dao, storage)
        repository.saveVariable(variable("d1", value = "s3cret", sensitive = true))
        assertTrue(storage.map.containsKey("variable_d1"))

        repository.deleteVariable("d1")

        assertTrue(dao.rows.value.isEmpty())
        assertFalse(storage.map.containsKey("variable_d1"))
        assertEquals(listOf("variable_d1"), storage.removed)
    }

    @Test
    fun `variables once returns the dao snapshot mapped to domain`() = runTest {
        val dao = FakeVariableDao()
        dao.rows.value = listOf(
            GlobalVariableEntity(id = "o1", name = "once", value = "v", updatedAt = 2L)
        )

        val loaded = VariableRepositoryImpl(dao, RecordingSecureStorage()).getVariablesOnce()
        assertEquals(1, loaded.size)
        assertEquals("v", loaded.single().value)
    }

    // endregion

    // region AutomationRepositoryImpl

    @Test
    fun `automation save get and delete round trip through the entity mapper`() = runTest {
        val dao = FakeAutomationDao()
        val repository = AutomationRepositoryImpl(dao)
        val automation = Automation(
            id = "a1",
            name = "Task",
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

        repository.saveAutomation(automation)
        assertEquals("Task", repository.getAutomationById("a1")?.name)
        assertEquals(1, repository.getAutomations().first().size)

        repository.deleteAutomation(automation)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `getAutomationById returns null for unknown ids`() = runTest {
        val repository = AutomationRepositoryImpl(FakeAutomationDao())
        assertNull(repository.getAutomationById("missing"))
    }

    @Test
    fun `updateAutomationStatus forwards to the dao`() = runTest {
        val dao = FakeAutomationDao()
        val repository = AutomationRepositoryImpl(dao)

        repository.updateAutomationStatus("a9", enabled = false)

        assertEquals(listOf("a9" to false), dao.statusUpdates)
    }

    // endregion

    // region HistoryRepositoryImpl

    @Test
    fun `history record round trip preserves action results`() = runTest {
        val dao = FakeExecutionDao()
        val repository = HistoryRepositoryImpl(dao)

        repository.recordExecution(
            com.nexaflow.domain.models.ExecutionRecord(
                id = "r1",
                automationId = "a1",
                automationName = "A",
                success = true,
                message = "ok",
                executedAt = 10L
            )
        )

        assertEquals("r1", dao.inserted?.id)
        assertEquals("r1", repository.getExecutionById("r1")?.id)
        assertNull(repository.getExecutionById("nope"))
    }

    @Test
    fun `history streams map entities to domain records`() = runTest {
        val dao = FakeExecutionDao()
        dao.rows.value = listOf(entity("r1", 10L), entity("r2", 20L))
        val repository = HistoryRepositoryImpl(dao)

        assertEquals(setOf("r1", "r2"), repository.getExecutionHistory().first().map { it.id }.toSet())
        assertEquals("r2", repository.getLatestExecutions().first().single().id)
    }

    @Test
    fun `skipped outcome paging routes to the skip query`() = runTest {
        val dao = SkippedQueryCapturingDao()
        val repository = HistoryRepositoryImpl(dao)

        runCatching { repository.getExecutionPaging("a1", ExecutionHistoryOutcome.SKIPPED) }

        assertEquals("a1", dao.capturedAutomationId)
        assertEquals(
            com.nexaflow.domain.models.ExecutionOutcomeClassifier.SKIPPED_MESSAGE_PREFIX + "%",
            dao.capturedSkipPattern
        )
    }

    @Test
    fun `failed outcome paging routes to the success filter`() = runTest {
        val dao = FilterQueryCapturingDao()
        val repository = HistoryRepositoryImpl(dao)

        runCatching { repository.getExecutionPaging("a2", ExecutionHistoryOutcome.FAILED) }
        assertEquals("a2" to false, dao.captured)

        dao.captured = null
        runCatching { repository.getExecutionPaging("a3", null as Boolean?) }
        assertEquals("a3" to null, dao.captured)
    }

    private open class SkippedQueryCapturingDao : FakeExecutionDao() {
        var capturedAutomationId: String? = null
        var capturedSkipPattern: String? = null

        override fun getExecutionsPagedSkipped(
            automationId: String?,
            skipMessageLike: String
        ): PagingSource<Int, ExecutionRecordEntity> {
            capturedAutomationId = automationId
            capturedSkipPattern = skipMessageLike
            throw ClassCastException("not materialized")
        }
    }

    private open class FilterQueryCapturingDao : FakeExecutionDao() {
        var captured: Pair<String?, Boolean?>? = null

        override fun getExecutionsPagedFiltered(
            automationId: String?,
            success: Boolean?
        ): PagingSource<Int, ExecutionRecordEntity> {
            captured = automationId to success
            throw IllegalArgumentException("not materialized")
        }
    }

    // endregion

    private fun variable(id: String, value: String, sensitive: Boolean) = GlobalVariable(
        id = id,
        name = id,
        value = value,
        updatedAt = 1L,
        version = 1L,
        serializedValue = null,
        sensitive = sensitive
    )

    private fun entity(id: String, at: Long) = ExecutionRecordEntity(
        id = id,
        automationId = "a",
        automationName = "A",
        success = true,
        message = "ok",
        executedAt = at
    )
}
