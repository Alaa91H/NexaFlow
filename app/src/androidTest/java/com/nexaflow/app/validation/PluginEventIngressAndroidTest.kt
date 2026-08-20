package com.nexaflow.app.validation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexaflow.core.engine.PluginEventIngress
import com.nexaflow.core.engine.TriggerIndex
import com.nexaflow.core.execution.events.InMemoryNexaFlowEventBus
import com.nexaflow.domain.events.EventFilter
import com.nexaflow.domain.events.NexaFlowEvent
import com.nexaflow.domain.events.NexaFlowEventType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected-device, in-process contract for plugin-event admission and routing primitives.
 *
 * It runs the production TriggerIndex, PluginEventIngress and EventBus implementations in the
 * installed app process. It does not replace required fixture-plugin / sender-identity validation
 * through PluginEventReceiver on API 34+, which remains a separate real-device test.
 */
@RunWith(AndroidJUnit4::class)
class PluginEventIngressAndroidTest {

    @Test
    fun approvedPluginEventPublishesCanonicalPayloadExactlyOnce() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val index = TriggerIndex(MutableStateFlow(listOf(pluginAutomation())))
        val indexJob = scope.launch { index.start() }
        val bus = InMemoryNexaFlowEventBus(scope, nowMs = { 1_000L })
        val ingress = PluginEventIngress(index, bus, nowMs = { 1_000L })
        val received = CompletableDeferred<NexaFlowEvent>()
        val subscription = bus.subscribe(
            EventFilter(types = setOf(NexaFlowEventType.CUSTOM), sources = setOf("plugin"))
        ) { received.complete(it) }

        try {
            awaitIndexed(index)
            val first = ingress.publish(
                senderPackage = "com.nexaflow.testfixture.locale",
                eventComponent = "com.nexaflow.testfixture.locale.LocalePluginEditActivity",
                eventId = "fixture-changed",
                correlationId = "fixture-correlation-1",
                payload = JsonObject(mapOf("state" to JsonPrimitive("on")))
            )
            val event = withTimeout(5_000L) { received.await() }
            val duplicate = ingress.publish(
                senderPackage = "com.nexaflow.testfixture.locale",
                eventComponent = "com.nexaflow.testfixture.locale.LocalePluginEditActivity",
                eventId = "fixture-changed",
                correlationId = "fixture-correlation-1",
                payload = JsonObject(mapOf("state" to JsonPrimitive("on")))
            )

            assertTrue(first.accepted)
            assertEquals(1, first.matchedSubscriptions)
            assertEquals(
                "com.nexaflow.testfixture.locale",
                (event.payload.getValue("pluginPackage") as JsonPrimitive).content
            )
            assertEquals("on", ((event.payload.getValue("data") as JsonObject).getValue("state") as JsonPrimitive).content)
            assertFalse(duplicate.accepted)
            assertTrue(duplicate.deduplicated)
        } finally {
            bus.unsubscribe(subscription.id)
            bus.close()
            indexJob.cancelAndJoin()
        }
    }

    @Test
    fun unapprovedPluginEventIsRejectedBeforeTheBus() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val index = TriggerIndex(MutableStateFlow(listOf(pluginAutomation())))
        val indexJob = scope.launch { index.start() }
        val bus = InMemoryNexaFlowEventBus(scope)
        val ingress = PluginEventIngress(index, bus)

        try {
            awaitIndexed(index)
            val result = ingress.publish(
                senderPackage = "com.attacker.plugin",
                eventComponent = "com.nexaflow.testfixture.locale.LocalePluginEditActivity",
                eventId = "fixture-changed",
                correlationId = "attacker-correlation",
                payload = JsonObject(emptyMap())
            )

            assertFalse(result.accepted)
            assertFalse(result.deduplicated)
            assertTrue(result.reason.orEmpty().contains("No approved workflow trigger"))
        } finally {
            bus.close()
            indexJob.cancelAndJoin()
        }
    }

    private suspend fun awaitIndexed(index: TriggerIndex) = withTimeout(5_000L) {
        while (index.version == 0L) delay(10L)
    }

    private fun pluginAutomation(): Automation = Automation(
        id = "android-plugin-event-workflow",
        name = "Android plugin event workflow",
        description = "",
        icon = "",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "Test",
        priority = 0,
        enabled = true,
        triggers = listOf(
            Trigger(
                type = TriggerType.PLUGIN_EVENT,
                config = mapOf(
                    PluginEventIngress.KEY_INSTANCE to "plugin:fixture-event-instance",
                    PluginEventIngress.KEY_APPROVAL to PluginEventIngress.APPROVAL_VALUE,
                    PluginEventIngress.KEY_PACKAGE to "com.nexaflow.testfixture.locale",
                    PluginEventIngress.KEY_COMPONENT to "com.nexaflow.testfixture.locale.LocalePluginEditActivity",
                    PluginEventIngress.KEY_EVENT_ID to "fixture-changed"
                )
            )
        ),
        actions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L
    )
}
