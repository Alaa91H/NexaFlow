package com.nexaflow.data.repository

import com.nexaflow.core.database.AutomationDao
import com.nexaflow.core.database.AutomationEntity
import com.nexaflow.data.mapper.toEntity
import com.nexaflow.domain.canonical.CanonicalV3WriteState
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalWorkflowMigrationRunnerTest {

    private class FakeAutomationDao(
        initial: List<AutomationEntity>,
        private val conflictIds: Set<String> = emptySet(),
    ) : AutomationDao {
        val rows = MutableStateFlow(initial)

        override fun getAllAutomations(): Flow<List<AutomationEntity>> = rows
        override suspend fun getAutomationById(id: String): AutomationEntity? =
            rows.value.firstOrNull { it.id == id }
        override suspend fun getAllAutomationsSnapshot(): List<AutomationEntity> = rows.value
        override suspend fun insertAutomation(automation: AutomationEntity) {
            rows.value = rows.value.filterNot { it.id == automation.id } + automation
        }
        override suspend fun insertAutomations(automations: List<AutomationEntity>) {
            var next = rows.value
            automations.forEach { row ->
                next = next.filterNot { it.id == row.id } + row
            }
            rows.value = next
        }
        override suspend fun updateAutomation(automation: AutomationEntity) =
            insertAutomation(automation)

        override suspend fun compareAndSetAutomation(
            automation: AutomationEntity,
            expectedRevision: Long,
        ): Boolean {
            if (automation.id in conflictIds) return false
            val current = getAutomationById(automation.id) ?: return false
            if (current.updatedAt != expectedRevision) return false
            insertAutomation(automation)
            return true
        }

        override suspend fun deleteAutomation(automation: AutomationEntity) {
            rows.value = rows.value.filterNot { it.id == automation.id }
        }
        override suspend fun deleteAutomationIfRevisionMatches(
            id: String,
            expectedRevision: Long,
        ): Int {
            val current = getAutomationById(id) ?: return 0
            if (current.updatedAt != expectedRevision) return 0
            rows.value = rows.value.filterNot { it.id == id }
            return 1
        }
        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) {
            rows.value = rows.value.map {
                if (it.id == id) it.copy(enabled = enabled) else it
            }
        }
    }

    @Test
    fun migrationRunsInDeterministicBoundedBatches() = runTest {
        val dao = FakeAutomationDao(
            listOf(
                legacyRow(automation("c")),
                legacyRow(automation("a")),
                legacyRow(automation("b")),
            ),
        )
        val runner = CanonicalWorkflowMigrationRunner(dao)

        val report = runner.runNextBatch(batchSize = 2)

        assertEquals(2, report.attempted)
        assertEquals(2, report.migrated)
        assertEquals(1, report.remaining)
        assertFalse(report.aborted)
        val states = dao.rows.value.associate { it.id to it.canonicalWriteState }
        assertEquals(CanonicalV3WriteState.V3_READY.name, states["a"])
        assertEquals(CanonicalV3WriteState.V3_READY.name, states["b"])
        assertEquals("LEGACY_ONLY", states["c"])
    }

    @Test
    fun canonicalGraphCanMigrateWhileSecretFallbackStillBlocksLegacyRetirement() = runTest {
        val secretAutomation = automation("secret").copy(
            actions = listOf(
                Action(
                    ActionType.SYSTEM_WIFI_CONNECT,
                    mapOf("ssid" to "Home", "password" to "never-in-v3"),
                ),
            ),
        )
        val dao = FakeAutomationDao(listOf(legacyRow(secretAutomation)))
        val runner = CanonicalWorkflowMigrationRunner(dao)

        val report = runner.runNextBatch()
        val status = runner.fleetStatus()

        assertEquals(1, report.migrated)
        assertEquals(1, report.migratedWithLegacyFallback)
        assertTrue(status.canonicalGraphMigrationComplete)
        assertEquals(1, status.legacyFallbackRequired)
        assertFalse(status.legacyRetirementReady)
        assertEquals(
            CanonicalV3WriteState.V3_WITH_LEGACY_FALLBACK.name,
            dao.rows.value.single().canonicalWriteState,
        )
    }

    @Test
    fun degradedRowsRemainPendingAndCanAbortTheRollout() = runTest {
        val invalid = automation("bad").copy(
            actions = listOf(
                Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("value" to "999")),
            ),
        )
        val dao = FakeAutomationDao(listOf(legacyRow(invalid)))
        val runner = CanonicalWorkflowMigrationRunner(dao)

        val report = runner.runNextBatch(maxFailuresPerRun = 0)
        val status = runner.fleetStatus()

        assertTrue(report.aborted)
        assertEquals(1, report.degraded)
        assertEquals(1, report.remaining)
        assertEquals(1, status.degraded)
        assertFalse(status.canonicalGraphMigrationComplete)
        assertFalse(status.legacyRetirementReady)
        assertEquals(
            CanonicalV3WriteState.LEGACY_ONLY_DEGRADED.name,
            dao.rows.value.single().canonicalWriteState,
        )
    }

    @Test
    fun concurrentEditWinsWithoutConsumingFailureBudget() = runTest {
        val row = legacyRow(automation("race"))
        val dao = FakeAutomationDao(listOf(row), conflictIds = setOf("race"))
        val runner = CanonicalWorkflowMigrationRunner(dao)

        val report = runner.runNextBatch(maxFailuresPerRun = 0)

        assertEquals(1, report.conflicted)
        assertEquals(0, report.degraded)
        assertFalse(report.aborted)
        assertEquals("LEGACY_ONLY", dao.rows.value.single().canonicalWriteState)
        assertEquals(1, report.remaining)
    }

    private fun legacyRow(automation: Automation): AutomationEntity =
        automation.toEntity().copy(
            canonicalWorkflowJson = null,
            canonicalWriteState = "LEGACY_ONLY",
            canonicalWriteErrorCode = null,
        )

    private fun automation(id: String): Automation = Automation(
        id = id,
        name = "Task $id",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "test",
        priority = 0,
        enabled = true,
        triggers = emptyList(),
        actions = listOf(Action(ActionType.SYSTEM_WIFI, mapOf("enabled" to "true"))),
        createdAt = 1L,
        updatedAt = 2L,
    )
}
