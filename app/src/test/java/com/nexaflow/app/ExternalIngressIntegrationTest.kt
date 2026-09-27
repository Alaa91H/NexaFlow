package com.nexaflow.app

import android.net.Uri
import com.nexaflow.core.automationcontrol.AutomationBatchResult
import com.nexaflow.core.automationcontrol.AutomationCommandService
import com.nexaflow.core.automationcontrol.AutomationDryRunInspector
import com.nexaflow.core.automationcontrol.AutomationMutationCommitRequest
import com.nexaflow.core.automationcontrol.AutomationMutationPersistence
import com.nexaflow.core.automationcontrol.AutomationPersistenceResult
import com.nexaflow.core.execution.dryrun.WorkflowDryRunReport
import com.nexaflow.data.backup.*
import com.nexaflow.domain.models.*
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.workflow.WorkflowValidationResult
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = android.app.Application::class)
class ExternalIngressIntegrationTest {
    @Test fun importerAcceptsOnlyViewableFileOrContentJsonAndNexaFlowDocuments() {
        fun accepted(uri: String, mime: String? = null, action: String = android.content.Intent.ACTION_VIEW): Boolean =
            isTaskImportIntent(android.content.Intent(action).setDataAndType(Uri.parse(uri), mime))
        assertTrue(accepted("content://provider/export", "application/json"))
        assertTrue(accepted("content://provider/task.NEXAFLOW"))
        assertTrue(accepted("file:///sdcard/task.json"))
        assertFalse(accepted("nexaflow://run-task/a?token=secret", "application/json"))
        assertFalse(accepted("https://example.com/task.json", "application/json"))
        assertFalse(accepted("content://provider/task.txt", "text/plain"))
        assertFalse(accepted("content://provider/task.json", action = android.content.Intent.ACTION_SEND))
    }

    private class Store : AutomationRepository {
        var task = Automation("a", "Task", "", "bolt", 0, 0, "general", 1, true,
            triggers = emptyList(), actions = emptyList(), createdAt = 0, updatedAt = 0,
            deepLinkToken = "secret")
        val saved = mutableListOf<Automation>()
        override fun getAutomations() = flowOf(listOf(task))
        override suspend fun getAutomationById(id: String) = task.takeIf { it.id == id }
        override suspend fun saveAutomation(automation: Automation) { saved += automation }
        override suspend fun deleteAutomation(automation: Automation) = Unit
        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) = Unit
    }
    @Test fun linkRequiresConfirmationAndRevalidatesAfterRotationOrRevocation() = runBlocking {
        val store = Store()
        val dispatcher = DeepLinkDispatcher(store)
        var reviews = 0
        var confirmations = 0
        var executions = 0
        var pending: RunTaskDeepLink? = null
        suspend fun open(value: String) = dispatcher.open(Uri.parse(value), { reviews++ }) { _, link ->
            confirmations++; pending = link
        }
        open("nexaflow://run-task/a")
        open("nexaflow://run-task/a?force=1&token=wrong")
        assertEquals(2, reviews); assertEquals(0, confirmations)
        open("nexaflow://run-task/a?token=secret")
        assertEquals(1, confirmations); assertEquals(0, executions)
        dispatcher.runConfirmed(requireNotNull(pending)) { executions++ }
        assertEquals(1, executions)
        store.task = store.task.copy(deepLinkToken = "rotated")
        dispatcher.runConfirmed(requireNotNull(pending)) { executions++ }
        assertEquals(1, executions)
        store.task = store.task.copy(deepLinkToken = null)
        open("nexaflow://run-task/a?force=1&token=secret")
        assertEquals(1, confirmations)
        store.task = store.task.copy(deepLinkToken = "fresh")
        open("nexaflow://run-task/a?force=1&token=fresh")
        assertEquals(2, confirmations)
        val forcedLink = requireNotNull(pending)
        assertTrue(forcedLink.force)
        assertEquals(1, executions)
        dispatcher.runConfirmed(forcedLink) { executions++ }
        assertEquals(2, executions)
        open("content://backup/a.json")
        assertEquals(5, reviews)
    }
    @Test fun api26StreamImportIsBoundedDisabledAndDropsCapabilities() = runBlocking {
        val store = Store()
        val manager = BackupManager(store, commandService(store))
        val text = manager.toJson(BackupFile(1, 0, listOf(store.task)))
        assertFalse(text.contains("deepLinkToken"))
        assertEquals(ImportResult.Success(1, 1), manager.import(BackupLimits.read(text.byteInputStream())))
        assertFalse(store.saved.single().enabled)
        assertNull(store.saved.single().deepLinkToken)
        assertTrue(runCatching { BackupLimits.read(ByteArray(BackupLimits.MAX_BYTES + 1).inputStream()) }.isFailure)
    }

    private fun commandService(store: Store): AutomationCommandService =
        AutomationCommandService(
            repository = store,
            dryRunInspector = AutomationDryRunInspector {
                WorkflowDryRunReport(
                    workflowValidation = WorkflowValidationResult(emptyList()),
                    capabilityResolutions = emptyList(),
                    executable = true,
                    summary = "ok"
                )
            },
            mutationPersistence = object : AutomationMutationPersistence {
                override suspend fun commit(
                    request: AutomationMutationCommitRequest
                ): AutomationPersistenceResult =
                    error("import path commits through commitBatch")

                override suspend fun commitBatch(
                    requests: List<AutomationMutationCommitRequest>
                ): AutomationBatchResult {
                    requests.forEach { request ->
                        store.saved.removeAll { it.id == request.automation.id }
                        store.saved += request.automation
                    }
                    return AutomationBatchResult.AllCommitted(
                        requests.map {
                            AutomationPersistenceResult.Committed(it.automation.id, 1L)
                        }
                    )
                }
            }
        )
}
