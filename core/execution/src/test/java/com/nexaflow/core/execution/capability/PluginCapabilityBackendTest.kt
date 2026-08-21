package com.nexaflow.core.execution.capability

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.execution.plugin.FakePluginReceiverForTest
import com.nexaflow.core.pluginsdk.LocaleContract
import com.nexaflow.core.pluginsdk.PluginConfigParser
import com.nexaflow.core.pluginsdk.PluginDiscoveryRegistry
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityDeviceState
import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.VerificationMode
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
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
class PluginCapabilityBackendTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        FakePluginReceiverForTest.reset()
        val packageName = context.packageName
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(LocaleContract.ACTION_EDIT_SETTING),
            resolveInfo(packageName, "$packageName.PluginEditActivity")
        )
    }

    @After
    fun tearDown() {
        FakePluginReceiverForTest.reset()
    }

    @Test
    fun invokesPersistedApprovedPluginOnlyThroughOpaqueInstanceReference() = runBlocking {
        val automation = automation(instance = "plugin:approved-instance")
        val backend = PluginCapabilityBackend(
            context = context,
            automationRepository = FakeAutomationRepository(automation),
            discoveryRegistry = PluginDiscoveryRegistry(context)
        )
        val registry = CapabilityRegistry.of(
            descriptors = PluginCapabilityCatalog.descriptors(),
            backends = listOf(backend)
        )
        val service = CapabilityExecutionService(
            resolver = CapabilityResolver(registry),
            deviceStateProvider = { deviceState() }
        )

        val deferred = CoroutineScope(Dispatchers.Default).async {
            service.execute(
                CapabilityRequest(
                    capability = CapabilityId.PLUGIN_ACTION,
                    parameters = mapOf("pluginInstance" to "plugin:approved-instance"),
                    workflowId = automation.id,
                    executionId = "run-123",
                    actionId = ActionType.PLUGIN_FIRE.name,
                    verification = VerificationMode.BEST_EFFORT
                )
            )
        }
        val deadline = System.currentTimeMillis() + 6_000L
        while (!deferred.isCompleted && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        val result = deferred.await()

        assertEquals(com.nexaflow.domain.capability.CapabilityStatus.SUCCESS, result.status)
        assertEquals(CapabilityBackendId.PLUGIN, result.backend)
        assertEquals("approved-instance", FakePluginReceiverForTest.lastConfig?.get("message"))
        assertEquals("run-123", result.metadata["correlationId"])
    }

    @Test
    fun selectsMatchingPluginCardWhenWorkflowContainsMultiplePluginActions() = runBlocking {
        val first = pluginAction(instance = "plugin:first", message = "first")
        val second = pluginAction(instance = "plugin:second", message = "second")
        val automation = automationWithActions(listOf(first, second))
        val backend = PluginCapabilityBackend(
            context = context,
            automationRepository = FakeAutomationRepository(automation),
            discoveryRegistry = PluginDiscoveryRegistry(context)
        )
        val service = CapabilityExecutionService(
            resolver = CapabilityResolver(
                CapabilityRegistry.of(PluginCapabilityCatalog.descriptors(), listOf(backend))
            ),
            deviceStateProvider = { deviceState() }
        )

        val deferred = CoroutineScope(Dispatchers.Default).async {
            service.execute(
                CapabilityRequest(
                    capability = CapabilityId.PLUGIN_ACTION,
                    parameters = mapOf("pluginInstance" to "plugin:second"),
                    workflowId = automation.id,
                    actionId = ActionType.PLUGIN_FIRE.name,
                    verification = VerificationMode.BEST_EFFORT
                )
            )
        }
        val deadline = System.currentTimeMillis() + 6_000L
        while (!deferred.isCompleted && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        val result = deferred.await()

        assertEquals(com.nexaflow.domain.capability.CapabilityStatus.SUCCESS, result.status)
        assertEquals("second", FakePluginReceiverForTest.lastConfig?.get("message"))
    }

    @Test
    fun rejectsHighRiskPluginWithoutExplicitHighRiskApprovalBeforeDiscoveryOrFire() = runBlocking {
        val action = Action(
            type = ActionType.PLUGIN_FIRE,
            config = mapOf(
                "pluginInstance" to "plugin:high-risk",
                "pluginApproval" to "approved",
                "package" to "com.termux.tasker",
                "receiver" to "com.termux.tasker.FireReceiver",
                "bundleJson" to PluginConfigParser.toJson(mapOf("message" to "must-not-run"))
            )
        )
        val automation = automationWithActions(listOf(action))
        val backend = PluginCapabilityBackend(
            context = context,
            automationRepository = FakeAutomationRepository(automation),
            discoveryRegistry = PluginDiscoveryRegistry(context)
        )
        val service = CapabilityExecutionService(
            resolver = CapabilityResolver(
                CapabilityRegistry.of(PluginCapabilityCatalog.descriptors(), listOf(backend))
            ),
            deviceStateProvider = { deviceState() }
        )

        val result = service.execute(
            CapabilityRequest(
                capability = CapabilityId.PLUGIN_ACTION,
                parameters = mapOf("pluginInstance" to "plugin:high-risk"),
                workflowId = automation.id,
                actionId = ActionType.PLUGIN_FIRE.name,
                verification = VerificationMode.BEST_EFFORT
            )
        )

        // CapabilityResolver reports an unavailable backend with its normalized
        // code, while preserving the actionable high-risk approval reason.
        assertEquals(CapabilityErrorCode.BACKEND_UNAVAILABLE, result.errorCode)
        assertTrue(result.message?.contains("High-risk plugin action") == true)
        assertEquals(null, FakePluginReceiverForTest.lastConfig)
    }

    @Test
    fun rejectsReferenceThatDoesNotMatchPersistedActionBeforeFiringPlugin() = runBlocking {
        val automation = automation(instance = "plugin:approved-instance")
        val backend = PluginCapabilityBackend(
            context = context,
            automationRepository = FakeAutomationRepository(automation),
            discoveryRegistry = PluginDiscoveryRegistry(context)
        )
        val service = CapabilityExecutionService(
            resolver = CapabilityResolver(
                CapabilityRegistry.of(PluginCapabilityCatalog.descriptors(), listOf(backend))
            ),
            deviceStateProvider = { deviceState() }
        )

        val result = service.execute(
            CapabilityRequest(
                capability = CapabilityId.PLUGIN_ACTION,
                parameters = mapOf("pluginInstance" to "plugin:other-instance"),
                workflowId = automation.id,
                actionId = ActionType.PLUGIN_FIRE.name,
                verification = VerificationMode.BEST_EFFORT
            )
        )

        assertTrue(result.errorCode != null)
        assertEquals(null, FakePluginReceiverForTest.lastConfig)
    }

    private fun automation(instance: String): Automation = automationWithActions(
        listOf(pluginAction(instance = instance, message = "approved-instance"))
    )

    private fun pluginAction(instance: String, message: String): Action = Action(
        type = ActionType.PLUGIN_FIRE,
        config = mapOf(
            "pluginInstance" to instance,
            "pluginApproval" to "approved",
            "package" to context.packageName,
            "receiver" to FakePluginReceiverForTest::class.java.name,
            "bundleJson" to PluginConfigParser.toJson(mapOf("message" to message))
        )
    )

    private fun automationWithActions(actions: List<Action>): Automation = Automation(
        id = "plugin-workflow",
        name = "Plugin workflow",
        description = "",
        icon = "",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "Test",
        priority = 0,
        enabled = true,
        triggers = emptyList(),
        actions = actions,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun deviceState() = CapabilityDeviceState(
        capturedAt = 1L,
        wifiConnected = true,
        batteryPercent = 100,
        charging = true,
        screenInteractive = false
    )

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
