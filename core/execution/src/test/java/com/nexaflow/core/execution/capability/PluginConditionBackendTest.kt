package com.nexaflow.core.execution.capability

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.execution.plugin.FakePluginConditionReceiverForTest
import com.nexaflow.core.pluginsdk.LocaleContract
import com.nexaflow.core.pluginsdk.PluginConfigParser
import com.nexaflow.core.pluginsdk.PluginDiscoveryRegistry
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilityStatus
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.repositories.AutomationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PluginConditionBackendTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        FakePluginConditionReceiverForTest.reset()
        val packageName = context.packageName
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(LocaleContract.ACTION_EDIT_CONDITION),
            resolveInfo(packageName, "$packageName.PluginConditionEditActivity")
        )
    }

    @After
    fun tearDown() {
        FakePluginConditionReceiverForTest.reset()
    }

    @Test
    fun queryPreservesSatisfiedUnsatisfiedAndUnknown() = runBlocking {
        val cases = listOf(
            LocaleContract.RESULT_CONDITION_SATISFIED to ConditionResult.Satisfied,
            LocaleContract.RESULT_CONDITION_UNSATISFIED to ConditionResult.Unsatisfied,
            LocaleContract.RESULT_CONDITION_UNKNOWN to ConditionResult.Unknown
        )
        cases.forEach { (resultCode, expected) ->
            FakePluginConditionReceiverForTest.nextResultCode = resultCode

            val result = queryApprovedCondition()

            assertEquals("backend message: ${result.message}", expected, result.conditionResult)
            assertEquals(
                if (expected == ConditionResult.Unknown) CapabilityStatus.PARTIAL else CapabilityStatus.SUCCESS,
                result.status
            )
            assertEquals(LocaleContract.ACTION_QUERY_CONDITION, FakePluginConditionReceiverForTest.lastAction)
        }
    }

    @Test
    fun queryRejectsAReferenceThatIsNotBoundToPersistedConstraint() = runBlocking {
        val backend = PluginConditionBackend(
            context = context,
            automationRepository = FakeAutomationRepository(automation()),
            discoveryRegistry = PluginDiscoveryRegistry(context)
        )

        val result = backend.query(
            CapabilityRequest(
                capability = CapabilityId.PLUGIN_CONDITION_READ,
                parameters = mapOf("pluginInstance" to "plugin:unbound"),
                workflowId = "plugin-condition-workflow",
                actionId = "PLUGIN_CONDITION"
            )
        )

        assertEquals(ConditionResult.Unavailable, result.conditionResult)
        assertTrue(result.errorCode != null)
        assertEquals(null, FakePluginConditionReceiverForTest.lastAction)
    }

    private suspend fun queryApprovedCondition() = dispatchUntilComplete {
        val backend = PluginConditionBackend(
            context = context,
            automationRepository = FakeAutomationRepository(automation()),
            discoveryRegistry = PluginDiscoveryRegistry(context)
        )
        backend.query(
            CapabilityRequest(
                capability = CapabilityId.PLUGIN_CONDITION_READ,
                parameters = mapOf("pluginInstance" to "plugin:condition-instance"),
                workflowId = "plugin-condition-workflow",
                executionId = "run-condition",
                actionId = "PLUGIN_CONDITION"
            )
        )
    }

    private suspend fun <T> dispatchUntilComplete(block: suspend () -> T): T {
        val deferred = CoroutineScope(Dispatchers.Default).async { block() }
        val deadline = System.currentTimeMillis() + 6_000L
        while (!deferred.isCompleted && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        return deferred.await()
    }

    private fun automation(): Automation {
        val packageName = context.packageName
        return Automation(
            id = "plugin-condition-workflow",
            name = "Plugin condition workflow",
            description = "",
            icon = "",
            iconColor = 0L,
            backgroundColor = 0L,
            category = "Test",
            priority = 0,
            enabled = true,
            triggers = emptyList(),
            actions = emptyList(),
            constraints = listOf(
                Constraint(
                    type = ConstraintType.PLUGIN,
                    config = mapOf(
                        "pluginInstance" to "plugin:condition-instance",
                        "pluginApproval" to "approved",
                        "package" to packageName,
                        "receiver" to FakePluginConditionReceiverForTest::class.java.name,
                        "bundleJson" to PluginConfigParser.toJson(mapOf("expected" to "on"))
                    )
                )
            ),
            createdAt = 0L,
            updatedAt = 0L
        )
    }

    private fun resolveInfo(packageName: String, className: String): ResolveInfo = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = className
            enabled = true
            exported = true
            nonLocalizedLabel = "Test plugin"
            applicationInfo = ApplicationInfo().apply {
                this.packageName = packageName
                enabled = true
                flags = 0
            }
        }
    }

    private class FakeAutomationRepository(
        private val automation: Automation
    ) : AutomationRepository {
        override fun getAutomations(): Flow<List<Automation>> = flowOf(listOf(automation))
        override suspend fun getAutomationById(id: String): Automation? = automation.takeIf { it.id == id }
        override suspend fun saveAutomation(automation: Automation) = Unit
        override suspend fun deleteAutomation(automation: Automation) = Unit
        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) = Unit
    }
}
