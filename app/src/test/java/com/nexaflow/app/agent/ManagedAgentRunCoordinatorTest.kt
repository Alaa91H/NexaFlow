package com.nexaflow.app.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.agentruntime.AgentBudget
import com.nexaflow.core.agentruntime.AgentDefinition
import com.nexaflow.core.agentruntime.AgentPolicy
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
import com.nexaflow.data.agents.AgentDefinitionRepository
import com.nexaflow.data.agents.AgentRunRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
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

        override fun stream(request: AiProviderRequest) = flow {
            requests++
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

    private class FakeTools : AiToolExecutor {
        override val tools = MutableStateFlow(
            listOf(AiToolDefinition("automation.create", "Create an automation", buildJsonObject {
                put("type", "object")
            }))
        )
        var executions = 0

        override suspend fun execute(call: AiToolCall): AiToolResult {
            executions++
            return AiToolResult(call.id, call.name, buildJsonObject { put("created", true) })
        }
    }
}
