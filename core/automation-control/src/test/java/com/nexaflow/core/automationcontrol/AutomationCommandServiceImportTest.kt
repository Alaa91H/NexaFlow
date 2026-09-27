package com.nexaflow.core.automationcontrol

import com.nexaflow.core.automationcontrol.api.AgentTaskMapper
import com.nexaflow.core.execution.dryrun.WorkflowDryRunReport
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.MaintenanceKind
import com.nexaflow.domain.models.MaintenanceProfile
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.workflow.WorkflowValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationCommandServiceImportTest {

    @Test
    fun emptyBatchSucceedsWithoutTouchingPersistence() = runTest {
        val fixture = fixture()
        val result = fixture.service.importAll(emptyList(), fixture.context("k"))

        assertEquals(AutomationImportResult.Success(emptyList()), result)
        assertEquals(0, fixture.persistence.batchCalls)
    }

    @Test
    fun nonImportOriginIsRejectedUpFront() = runTest {
        val fixture = fixture()

        val failure = runCatching {
            fixture.service.importAll(
                listOf(automation("a")),
                AutomationMutationContext(
                    actorId = "human:import",
                    origin = AutomationMutationOrigin.AGENT,
                    idempotencyKey = "k"
                )
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(0, fixture.persistence.batchCalls)
    }

    @Test
    fun invalidDefinitionsRejectTheWholeBatchBeforeAnyWrite() = runTest {
        val fixture = fixture()
        val result = fixture.service.importAll(
            listOf(automation("good"), automation("bad", name = "")),
            fixture.context("k")
        )

        assertTrue(result is AutomationImportResult.Rejected)
        assertEquals(listOf("bad"), (result as AutomationImportResult.Rejected).failures.map { it.automationId })
        assertEquals(0, fixture.persistence.batchCalls)
        assertTrue(fixture.observer.events.isEmpty())
    }

    @Test
    fun intraBatchDuplicateIdsAreRejected() = runTest {
        val fixture = fixture()
        val result = fixture.service.importAll(
            listOf(automation("dup"), automation("dup")),
            fixture.context("k")
        )

        assertTrue(result is AutomationImportResult.Rejected)
        assertEquals(0, fixture.persistence.batchCalls)
    }

    @Test
    fun collidingIdsAreRejectedInsteadOfReplacingLocalTasks() = runTest {
        val fixture = fixture(initial = listOf(automation("local")))
        val result = fixture.service.importAll(
            listOf(automation("local")),
            fixture.context("k")
        )

        assertTrue(result is AutomationImportResult.Rejected)
        assertEquals(0, fixture.persistence.batchCalls)
    }

    @Test
    fun validBatchCommitsAtomicallyAndNotifiesPerItem() = runTest {
        val fixture = fixture()
        val result = fixture.service.importAll(
            listOf(automation("a"), automation("b")),
            fixture.context("k")
        )

        assertTrue(result is AutomationImportResult.Success)
        assertEquals(listOf("a", "b"), (result as AutomationImportResult.Success).automations.map { it.id })
        assertEquals(1, fixture.persistence.batchCalls)
        assertEquals(2, fixture.persistence.committed.size)
        // Timestamps ride through verbatim: import preserves history, not rewrite it.
        assertEquals(11L, fixture.persistence.committed.single { it.id == "a" }.createdAt)
        assertEquals(
            listOf("a", "b"),
            fixture.observer.events.map { it.automation.id }
        )
        assertTrue(fixture.observer.events.all { it.kind == AutomationMutationKind.CREATE })
    }

    @Test
    fun intraBatchDependenciesCommitInDependencyOrder() = runTest {
        val fixture = fixture()
        val dependent = automation("dependent").copy(
            maintenanceProfile = MaintenanceProfile(
                kind = MaintenanceKind.AUTOMATION,
                dependencyAutomationIds = listOf("dependency")
            )
        )
        // File order is hostile: the dependent comes first.
        val result = fixture.service.importAll(
            listOf(dependent, automation("dependency")),
            fixture.context("k")
        )

        assertTrue(result is AutomationImportResult.Success)
        assertEquals(
            listOf("dependency", "dependent"),
            fixture.persistence.commitOrder
        )
    }

    @Test
    fun persistenceAbortRejectsWithoutPartialState() = runTest {
        val fixture = fixture()
        fixture.persistence.abortWithDependencyConflict = true
        val result = fixture.service.importAll(
            listOf(automation("a")),
            fixture.context("k")
        )

        assertTrue(result is AutomationImportResult.Rejected)
        assertTrue(fixture.observer.events.isEmpty())
    }

    @Test
    fun idempotentReplayReturnsStoredRowsWithoutWriting() = runTest {
        val existing = automation("a")
        val fixture = fixture(initial = listOf(existing))
        fixture.persistence.replayFor("import-key#a") { existing }

        val result = fixture.service.importAll(
            listOf(automation("a").copy(name = "Changed name")),
            fixture.context("import-key")
        )

        // The fingerprint differs from the stored one, so this is a genuine
        // conflict rather than a replay: same key, different request.
        assertTrue(result is AutomationImportResult.IdempotencyConflict)
        assertEquals(0, fixture.persistence.batchCalls)
    }

    @Test
    fun identicalRetryReplaysWithoutDuplicating() = runTest {
        val stored = automation("a")
        val fixture = fixture(initial = listOf(stored))
        val draft = AgentTaskMapper.fromAutomation(automation("a"))
        val fingerprint = AutomationMutationFingerprint.draft(
            AutomationMutationKind.CREATE,
            automationId = "a",
            draft = draft
        )
        fixture.persistence.replayFor("import-key#a") { stored }
        fixture.persistence.replayFingerprint = fingerprint

        val result = fixture.service.importAll(
            listOf(automation("a")),
            fixture.context("import-key")
        )

        assertTrue(result is AutomationImportResult.Success)
        assertEquals(listOf("a"), (result as AutomationImportResult.Success).automations.map { it.id })
        assertEquals(0, fixture.persistence.batchCalls)
        assertTrue(fixture.observer.events.isEmpty())
    }

    private fun automation(id: String, name: String = "Task $id") = Automation(
        id = id,
        name = name,
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "custom",
        priority = 1,
        enabled = false,
        triggers = emptyList(),
        actions = emptyList(),
        cooldownSeconds = 0,
        createdAt = 11L,
        updatedAt = 12L
    )

    private fun fixture(initial: List<Automation> = emptyList()): Fixture {
        val repository = FakeImportRepository(initial)
        val persistence = FakeImportPersistence()
        val observer = RecordingObserver()
        val service = AutomationCommandService(
            repository = repository,
            dryRunInspector = AutomationDryRunInspector {
                WorkflowDryRunReport(
                    workflowValidation = WorkflowValidationResult(emptyList()),
                    capabilityResolutions = emptyList(),
                    executable = true,
                    summary = "ok"
                )
            },
            mutationPersistence = persistence,
            mutationObserver = observer
        )
        return Fixture(service, repository, persistence, observer)
    }

    private data class Fixture(
        val service: AutomationCommandService,
        val repository: FakeImportRepository,
        val persistence: FakeImportPersistence,
        val observer: RecordingObserver
    ) {
        fun context(key: String) = AutomationMutationContext(
            actorId = "human:import",
            origin = AutomationMutationOrigin.IMPORT,
            transport = "TEST",
            idempotencyKey = key
        )
    }

    private data class ObservedCommit(
        val kind: AutomationMutationKind,
        val automation: Automation,
        val revision: Long
    )

    private class RecordingObserver : AutomationMutationObserver {
        val events = mutableListOf<ObservedCommit>()

        override suspend fun onCommitted(
            kind: AutomationMutationKind,
            automation: Automation,
            revision: Long,
            context: AutomationMutationContext
        ) {
            events += ObservedCommit(kind, automation, revision)
        }
    }

    private class FakeImportPersistence : AutomationMutationPersistence {
        var batchCalls = 0
        val committed = mutableListOf<Automation>()
        val commitOrder = mutableListOf<String>()
        var abortWithDependencyConflict = false
        var replayFingerprint: String? = null
        private val replays = mutableMapOf<String, () -> Automation?>()

        fun replayFor(derivedKey: String, lookup: () -> Automation?) {
            replays[derivedKey] = lookup
        }

        override suspend fun commit(
            request: AutomationMutationCommitRequest
        ): AutomationPersistenceResult =
            error("import path commits through commitBatch")

        override suspend fun resolveStoredIdempotency(
            context: AutomationMutationContext,
            kind: AutomationMutationKind,
            automationId: String?,
            requestFingerprint: String,
            occurredAt: Long
        ): AutomationPersistenceResult? {
            val lookup = replays[context.idempotencyKey] ?: return null
            val stored = lookup() ?: return null
            val expected = replayFingerprint
            return if (expected != null && expected == requestFingerprint) {
                AutomationPersistenceResult.IdempotentReplay(stored.id, 7L)
            } else {
                AutomationPersistenceResult.IdempotencyConflict
            }
        }

        override suspend fun commitBatch(
            requests: List<AutomationMutationCommitRequest>
        ): AutomationBatchResult {
            batchCalls++
            if (abortWithDependencyConflict) {
                return AutomationBatchResult.Aborted(
                    requests.map {
                        AutomationBatchItemFailure(
                            index = 0,
                            automationId = it.automation.id,
                            result = AutomationPersistenceResult.DependencyConflict(emptyList())
                        )
                    }
                )
            }
            requests.forEach {
                committed += it.automation
                commitOrder += it.automation.id
            }
            return AutomationBatchResult.AllCommitted(
                requests.map { AutomationPersistenceResult.Committed(it.automation.id, 1L) }
            )
        }
    }

    private class FakeImportRepository(
        initial: List<Automation>
    ) : AutomationRepository {
        private val state = MutableStateFlow(initial)

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
            state.value = state.value.map { automation ->
                if (automation.id == id) automation.copy(enabled = enabled) else automation
            }
        }
    }
}
