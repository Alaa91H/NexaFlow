package com.nexaflow.core.engine

import android.content.Intent
import android.os.Bundle
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
import kotlinx.coroutines.delay
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
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PluginEventIngressTest {

    @Test
    fun approvedMatchingEventPublishesOnceAndPreservesBoundedPayload() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val index = TriggerIndex(kotlinx.coroutines.flow.MutableStateFlow(listOf(pluginAutomation())))
        val indexJob = scope.launch { index.start() }
        awaitIndexed(index)
        val bus = InMemoryNexaFlowEventBus(scope)
        val ingress = PluginEventIngress(index, bus, nowMs = { 1_000L })
        val received = CompletableDeferred<NexaFlowEvent>()
        val subscription = bus.subscribe(
            EventFilter(types = setOf(NexaFlowEventType.CUSTOM), sources = setOf("plugin"))
        ) { received.complete(it) }
        val first = ingress.publish(
            senderPackage = "com.example.plugin",
            eventComponent = "com.example.plugin.EditActivity",
            eventId = "changed",
            correlationId = "correlation-1",
            payload = JsonObject(mapOf("state" to JsonPrimitive("on")))
        )
        val event = withTimeout(2_000L) { received.await() }
        val second = ingress.publish(
            senderPackage = "com.example.plugin",
            eventComponent = "com.example.plugin.EditActivity",
            eventId = "changed",
            correlationId = "correlation-1",
            payload = JsonObject(mapOf("state" to JsonPrimitive("on")))
        )

        assertTrue(first.accepted)
        assertEquals(1, first.matchedSubscriptions)
        assertEquals("com.example.plugin", (event.payload["pluginPackage"] as JsonPrimitive).content)
        assertEquals("on", ((event.payload["data"] as JsonObject)["state"] as JsonPrimitive).content)
        assertFalse(second.accepted)
        assertTrue(second.deduplicated)

        bus.unsubscribe(subscription.id)
        bus.close()
        indexJob.cancel()
    }

    @Test
    fun requeryHintIsNotMarkedAsDurableOccurrenceIdentity() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val workflow = pluginAutomation().copy(
            triggers = listOf(
                pluginAutomation().triggers.single().copy(
                    config = pluginAutomation().triggers.single().config - PluginEventIngress.KEY_EVENT_ID,
                ),
            ),
        )
        val index = TriggerIndex(kotlinx.coroutines.flow.MutableStateFlow(listOf(workflow)))
        val indexJob = scope.launch { index.start() }
        awaitIndexed(index)
        val bus = InMemoryNexaFlowEventBus(scope)
        val ingress = PluginEventIngress(index, bus)
        val received = CompletableDeferred<NexaFlowEvent>()
        val subscription = bus.subscribe(
            EventFilter(types = setOf(NexaFlowEventType.CUSTOM), sources = setOf("plugin"))
        ) { received.complete(it) }
        val router = PluginEventRouter(
            scope,
            bus,
            index,
            executionEngine = testEngine(
                org.robolectric.RuntimeEnvironment.getApplication(),
                RecordingHistory(),
            ),
        )
        router.start()

        val result = ingress.publish(
            senderPackage = "com.example.plugin",
            eventComponent = "com.example.plugin.EditActivity",
            eventId = PluginEventIngress.REQUERY_HINT_EVENT_ID,
            eventIdIsStable = false,
            correlationIdIsStable = false,
            correlationId = "com.example.plugin.EditActivity",
            payload = JsonObject(emptyMap()),
        )
        assertTrue(result.accepted)
        val event = withTimeout(2_000L) { received.await() }
        val isStable = (event.payload["pluginEventIdIsStable"] as JsonPrimitive).content.toBoolean()
        assertTrue(result.matchedSubscriptions >= 1)
        assertFalse(isStable)
        router.stop()
        bus.unsubscribe(subscription.id)
        bus.close()
        indexJob.cancel()
    }

    @Test
    fun hundredEventBurstIsBoundedAtIngressWithoutUnboundedQueueGrowth() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val index = TriggerIndex(kotlinx.coroutines.flow.MutableStateFlow(listOf(pluginAutomation())))
        val indexJob = scope.launch { index.start() }
        awaitIndexed(index)
        val bus = InMemoryNexaFlowEventBus(scope)
        val ingress = PluginEventIngress(
            triggerIndex = index,
            eventBus = bus,
            nowMs = { 10_000L },
            maxEventsPerWindow = 30,
        )

        val results = (0 until 100).map { sequence ->
            ingress.publish(
                senderPackage = "com.example.plugin",
                eventComponent = "com.example.plugin.EditActivity",
                eventId = "changed",
                correlationId = "burst-$sequence",
                payload = JsonObject(mapOf("sequence" to JsonPrimitive(sequence))),
            )
        }

        assertEquals(30, results.count { it.accepted })
        assertEquals(70, results.count { !it.accepted })
        assertTrue(
            results.drop(30).all {
                it.reason.orEmpty().contains("rate limit", ignoreCase = true)
            },
        )

        bus.close()
        indexJob.cancel()
    }

    @Test
    fun unapprovedOrMismatchedEventDoesNotReachTheBus() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val index = TriggerIndex(kotlinx.coroutines.flow.MutableStateFlow(listOf(pluginAutomation())))
        val indexJob = scope.launch { index.start() }
        awaitIndexed(index)
        val bus = InMemoryNexaFlowEventBus(scope)
        val ingress = PluginEventIngress(index, bus)

        val result = ingress.publish(
            senderPackage = "com.attacker.plugin",
            eventComponent = "com.example.plugin.EditActivity",
            eventId = "changed",
            correlationId = "correlation-2",
            payload = JsonObject(emptyMap())
        )

        assertFalse(result.accepted)
        assertFalse(result.deduplicated)
        assertTrue(result.reason.orEmpty().contains("No approved workflow trigger"))
        bus.close()
        indexJob.cancel()
    }

    @Test
    fun canonicalPluginIdentityGateRejectsColonPackageBeforePublishing() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val index = TriggerIndex(kotlinx.coroutines.flow.MutableStateFlow(listOf(pluginAutomation())))
        val indexJob = scope.launch { index.start() }
        awaitIndexed(index)
        val bus = InMemoryNexaFlowEventBus(scope)
        val ingress = PluginEventIngress(index, bus)

        // ':' was accepted by the older generic token check, but it is not a
        // valid canonical plugin id and must fail before bus publication.
        val result = ingress.publish(
            senderPackage = "com.example:plugin",
            eventComponent = "com.example.plugin.EditActivity",
            eventId = "changed",
            correlationId = "correlation-3",
            payload = JsonObject(emptyMap()),
        )

        assertFalse(result.accepted)
        assertFalse(result.deduplicated)
        assertTrue(result.reason.orEmpty().contains("identity"))
        bus.close()
        indexJob.cancel()
    }

    @Test
    fun payloadAdapterRejectsAndroidParcelableAndAcceptsPrimitivesOnly() {
        val accepted = PluginEventPayloadAdapter.toJson(Bundle().apply {
            putString("state", "on")
            putLong("sequence", 3L)
        })
        val rejected = PluginEventPayloadAdapter.toJson(Bundle().apply {
            putParcelable("intent", Intent("unsafe"))
        })

        assertTrue(accepted is PluginEventPayloadConversion.Accepted)
        assertTrue(rejected is PluginEventPayloadConversion.Rejected)
    }

    private suspend fun awaitIndexed(index: TriggerIndex) = withTimeout(2_000L) {
        while (index.version == 0L) delay(10L)
    }

    private fun pluginAutomation(): Automation = Automation(
        id = "plugin-event-workflow",
        name = "Plugin event workflow",
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
                    PluginEventIngress.KEY_INSTANCE to "plugin:event-instance",
                    PluginEventIngress.KEY_APPROVAL to PluginEventIngress.APPROVAL_VALUE,
                    PluginEventIngress.KEY_PACKAGE to "com.example.plugin",
                    PluginEventIngress.KEY_COMPONENT to "com.example.plugin.EditActivity",
                    PluginEventIngress.KEY_EVENT_ID to "changed"
                )
            )
        ),
        actions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L
    )
}
