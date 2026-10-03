package com.nexaflow.app.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.agentruntime.AgentBudget
import com.nexaflow.core.agentruntime.AgentDefinition
import com.nexaflow.core.agentruntime.AgentPolicy
import com.nexaflow.core.agentruntime.AgentMemoryEntry
import com.nexaflow.core.agentruntime.AgentRunStatus
import com.nexaflow.core.agentruntime.ManagedAgentRunEvent
import com.nexaflow.core.airuntime.AiModelProvider
import com.nexaflow.core.airuntime.AiProviderCapabilities
import com.nexaflow.core.airuntime.AiProviderEvent
import com.nexaflow.core.airuntime.AiProviderRegistry
import com.nexaflow.core.airuntime.AiProviderRequest
import com.nexaflow.core.airuntime.AiProviderDescriptor
import com.nexaflow.core.airuntime.AiRole
import com.nexaflow.core.airuntime.AiToolCall
import com.nexaflow.core.airuntime.AiToolDefinition
import com.nexaflow.core.airuntime.AiToolExecutor
import com.nexaflow.core.airuntime.AiToolResult
import com.nexaflow.core.database.AppDatabase
import com.nexaflow.core.security.InMemorySecureStorage
import com.nexaflow.data.agents.AgentDefinitionRepository
import com.nexaflow.data.agents.AgentMemoryRepository
import com.nexaflow.data.agents.AgentRunRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManagedAgentRunCoordinatorTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun runUsesScopedToolRequiresApprovalAndNeverPersistsThePromptOrToolInput() = runBlocking {
        val tool = FakeTools()
        val provider = ApprovalProvider()
        val registry = AiProviderRegistry(listOf(provider))
        val definitionRepository = AgentDefinitionRepository(database.agentDefinitionDao())
        val runRepository = AgentRunRepository(database.agentRunDao())
        val coordinator = ManagedAgentRunCoordinator(
            definitions = definitionRepository,
            runs = runRepository,
            memories = AgentMemoryRepository(InMemorySecureStorage()),
            registry = registry,
            tools = tool,
            context = ApplicationProvider.getApplicationContext()
        )
        val definition = AgentDefinition(
            id = "agent-safe",
            name = "Safe agent",
            systemInstructions = "Use the one allowed action only.",
            providerProfileId = "local-profile",
            modelId = "local-model",
            policy = AgentPolicy(
                allowedToolNames = setOf("automation.create"),
                approvalRequiredToolNames = setOf("automation.create"),
                allowCloudData = false
            ),
            budget = AgentBudget(maxTurns = 3),
            enabled = true,
            createdAtMillis = 1,
            updatedAtMillis = 1
        )
        definitionRepository.create(definition)

        val events = mutableListOf<ManagedAgentRunEvent>()
        coordinator.runAgent(definition.id, "private user prompt", "stable-request-key").collect { event ->
            events += event
            if (event is ManagedAgentRunEvent.ApprovalRequired) {
                assertTrue(event.redactedArguments.contains("[REDACTED]"))
                assertTrue(coordinator.resolveApproval(event.approvalId, approved = true))
            }
        }

        val runId = (events.first { it is ManagedAgentRunEvent.Queued } as ManagedAgentRunEvent.Queued).runId
        assertTrue("Expected approval event, got $events", events.any { it is ManagedAgentRunEvent.ApprovalRequired })
        assertTrue("Expected completion event, got $events", events.any { it is ManagedAgentRunEvent.Completed })
        assertEquals(1, tool.executions)
        assertEquals(AgentRunStatus.COMPLETED, runRepository.find(runId)?.status)
        val persistedEvents = runRepository.observeEvents(runId).first()
        assertTrue(persistedEvents.any { it.safeCode == "approval_requested" })
        assertTrue(persistedEvents.none { it.safeCode.contains("private") || it.safeCode.contains("account") })
        assertFalse(persistedEvents.toString().contains("private user prompt"))
        assertEquals(2, provider.requests)
    }

    @Test
    fun configuredCostCapFailsClosedBeforeCallingProviderWithoutUsagePricing() = runBlocking {
        val provider = ApprovalProvider()
        val registry = AiProviderRegistry(listOf(provider))
        val definitions = AgentDefinitionRepository(database.agentDefinitionDao())
        val runs = AgentRunRepository(database.agentRunDao())
        val coordinator = ManagedAgentRunCoordinator(
            definitions = definitions,
            runs = runs,
            memories = AgentMemoryRepository(InMemorySecureStorage()),
            registry = registry,
            tools = FakeTools(),
            context = ApplicationProvider.getApplicationContext()
        )
        definitions.create(
            AgentDefinition(
                id = "agent-cost-capped",
                name = "Cost capped",
                providerProfileId = "local-profile",
                modelId = "local-model",
                budget = AgentBudget(maxCostMicros = 1_000),
                enabled = true,
                createdAtMillis = 1,
                updatedAtMillis = 1
            )
        )

        val events = coordinator.runAgent("agent-cost-capped", "hello", "cost-request").toList()

        val runId = (events.first { it is ManagedAgentRunEvent.Queued } as ManagedAgentRunEvent.Queued).runId
        assertTrue(events.any { it == ManagedAgentRunEvent.Failed(runId, "cost_unknown") })
        assertEquals(0, provider.requests)
        assertEquals(AgentRunStatus.FAILED, runs.find(runId)?.status)
        assertEquals("cost_unknown", runs.find(runId)?.outcomeCode)
    }

    @Test
    fun toolBudgetExhaustionIsPersistedAsFailureEvenIfProviderThenResponds() = runBlocking {
        val provider = ExhaustingProvider()
        val registry = AiProviderRegistry(listOf(provider))
        val definitions = AgentDefinitionRepository(database.agentDefinitionDao())
        val runs = AgentRunRepository(database.agentRunDao())
        val tools = FakeTools()
        val coordinator = ManagedAgentRunCoordinator(
            definitions = definitions,
            runs = runs,
            memories = AgentMemoryRepository(InMemorySecureStorage()),
            registry = registry,
            tools = tools,
            context = ApplicationProvider.getApplicationContext()
        )
        definitions.create(
            AgentDefinition(
                id = "agent-tool-capped",
                name = "Tool capped",
                providerProfileId = "local-profile",
                modelId = "local-model",
                policy = AgentPolicy(
                    allowedToolNames = setOf("automation.create"),
                    approvalOptionalToolNames = setOf("automation.create")
                ),
                budget = AgentBudget(maxTurns = 4, maxToolCalls = 1),
                enabled = true,
                createdAtMillis = 1,
                updatedAtMillis = 1
            )
        )

        val events = coordinator.runAgent("agent-tool-capped", "work", "tool-request").toList()

        val runId = (events.first { it is ManagedAgentRunEvent.Queued } as ManagedAgentRunEvent.Queued).runId
        assertTrue(events.any { it == ManagedAgentRunEvent.Failed(runId, "tool_limit") })
        assertEquals(1, tools.executions)
        assertEquals(AgentRunStatus.FAILED, runs.find(runId)?.status)
        assertEquals("tool_limit", runs.find(runId)?.outcomeCode)
    }

    @Test
    fun onlyTheSelectedAgentsActiveSavedNotesArePassedAsUntrustedContext() = runBlocking {
        val provider = ApprovalProvider()
        val definitions = AgentDefinitionRepository(database.agentDefinitionDao())
        val memories = AgentMemoryRepository(InMemorySecureStorage())
        val coordinator = ManagedAgentRunCoordinator(
            definitions = definitions,
            runs = AgentRunRepository(database.agentRunDao()),
            memories = memories,
            registry = AiProviderRegistry(listOf(provider)),
            tools = FakeTools(),
            context = ApplicationProvider.getApplicationContext(),
        )
        val definition = AgentDefinition(
            id = "agent-memory",
            name = "Memory agent",
            providerProfileId = "local-profile",
            modelId = "local-model",
            policy = AgentPolicy(
                allowedToolNames = setOf("automation.create"),
                approvalOptionalToolNames = setOf("automation.create")
            ),
            enabled = true,
            createdAtMillis = 1,
            updatedAtMillis = 1,
        )
        definitions.create(definition)
        memories.save(AgentMemoryEntry("note-1", definition.id, "Preference", "Likes quiet notifications", 1))
        memories.save(AgentMemoryEntry("note-2", "other-agent", "Private", "must never cross over", 1))

        val events = coordinator.runAgent(definition.id, "Hello", "memory-request").toList()

        assertTrue(events.none { it is ManagedAgentRunEvent.Failed })
        val userMessage = requireNotNull(provider.lastRequest).messages.first { it.role == AiRole.USER }.text
        assertTrue(userMessage.contains("Likes quiet notifications"))
        assertTrue(userMessage.contains("untrusted reference data"))
        assertFalse(userMessage.contains("must never cross over"))
    }

    private class ApprovalProvider : AiModelProvider {
        override val descriptor = MutableStateFlow(
            AiProviderDescriptor(
                id = "local-profile",
                displayName = "Local",
                modelId = "local-model",
                capabilities = AiProviderCapabilities(toolCalling = true, streaming = true, local = true),
                available = true
            )
        )
        var requests = 0
        var lastRequest: AiProviderRequest? = null

        override fun stream(request: AiProviderRequest) = flow {
            requests++
            lastRequest = request
            val hasToolResult = request.messages.any { it.role == AiRole.TOOL }
            if (!hasToolResult) {
                emit(AiProviderEvent.ToolCall(
                    AiToolCall(
                        id = "call-private",
                        name = "automation.create",
                        arguments = buildJsonObject {
                            put("api_key", "secret-account-value")
                            put("name", "private automation title")
                        }
                    )
                ))
            } else {
                emit(AiProviderEvent.TextDelta("The automation was created."))
            }
            emit(AiProviderEvent.Finished())
        }
    }

    private class ExhaustingProvider : AiModelProvider {
        override val descriptor = MutableStateFlow(
            AiProviderDescriptor(
                id = "local-profile",
                displayName = "Local",
                modelId = "local-model",
                capabilities = AiProviderCapabilities(toolCalling = true, streaming = true, local = true),
                available = true
            )
        )
        private var requests = 0

        override fun stream(request: AiProviderRequest) = flow {
            requests++
            when (requests) {
                1, 2 -> emit(AiProviderEvent.ToolCall(
                    AiToolCall("call-$requests", "automation.create", buildJsonObject {})
                ))
                else -> emit(AiProviderEvent.TextDelta("All done."))
            }
            emit(AiProviderEvent.Finished())
        }
    }

    private class FakeTools : AiToolExecutor {
        override val tools = MutableStateFlow(
            listOf(AiToolDefinition("automation.create", "Create an automation", buildJsonObject {
                put("type", "object")
            }, readOnly = true))
        )
        var executions = 0

        override suspend fun execute(call: AiToolCall): AiToolResult {
            executions++
            return AiToolResult(call.id, call.name, buildJsonObject { put("created", true) })
        }
    }
}
