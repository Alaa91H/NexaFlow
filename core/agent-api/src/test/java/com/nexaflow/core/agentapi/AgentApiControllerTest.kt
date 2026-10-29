package com.nexaflow.core.agentapi

import com.nexaflow.core.agentsecurity.AgentAccessManager
import com.nexaflow.core.agentsecurity.AgentGrantMode
import com.nexaflow.core.agentsecurity.AgentGrantResult
import com.nexaflow.core.agentsecurity.AgentIdentityBinding
import com.nexaflow.core.agentsecurity.AgentIdentityRequest
import com.nexaflow.core.agentsecurity.AgentPairingStartResult
import com.nexaflow.core.agentsecurity.AgentRequestAuthorizer
import com.nexaflow.core.agentsecurity.AgentSecurityStateV1
import com.nexaflow.core.agentsecurity.AgentSecurityStore
import com.nexaflow.core.agentsecurity.AgentSessionIssueResult
import com.nexaflow.core.automationcontrol.AutomationCommandService
import com.nexaflow.core.automationcontrol.AutomationDryRunInspector
import com.nexaflow.core.automationcontrol.AutomationMutationCommitRequest
import com.nexaflow.core.automationcontrol.AutomationMutationKind
import com.nexaflow.core.automationcontrol.AutomationMutationPersistence
import com.nexaflow.core.automationcontrol.AutomationPersistenceResult
import com.nexaflow.core.automationcontrol.schedule.AgentSchedulePreviewService
import com.nexaflow.core.automationcontrol.schema.AutomationSchemaRegistry
import com.nexaflow.core.automationcontrol.simulation.AgentSimulationService
import com.nexaflow.core.execution.dryrun.WorkflowDryRunReport
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.workflow.WorkflowValidationResult
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract and negative-security coverage for the largest local attack surface
 * (NFA-05): every route in [AgentApiController] that does not need a device.
 *
 * The fixture uses the real access manager, authorizer, command service and
 * schema registry; only storage, execution, and persistence are fakes. That
 * keeps the authorization matrix under test instead of mocking it away.
 */
class AgentApiControllerTest {

    // ---- transport and authentication -------------------------------------

    @Test
    fun rejectsMissingBearerToken() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(request("GET", "/api/v1/tasks"))
        assertEquals(401, response.status)
        assertEquals("missing_bearer_token", errorCode(response))
    }

    @Test
    fun rejectsNonBearerAuthorizationScheme() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request("GET", "/api/v1/tasks", headers = mapOf("authorization" to "Basic abc"))
        )
        assertEquals(401, response.status)
        assertEquals("missing_bearer_token", errorCode(response))
    }

    @Test
    fun rejectsUnknownAccessToken() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request("GET", "/api/v1/tasks", token = "not-a-real-token")
        )
        assertEquals(401, response.status)
        assertEquals("invalid_access_token", errorCode(response))
    }

    @Test
    fun rejectsEveryRouteWhenAgentAccessIsDisabled() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(false)
        val response = fixture.controller.handle(
            request("GET", "/api/v1/status", token = fixture.token)
        )
        assertEquals(403, response.status)
        assertEquals("agent_access_disabled", errorCode(response))
    }

    @Test
    fun readOnlyGrantCannotRunATask() = runTest {
        val fixture = fixture()
        fixture.repository.saveAutomation(automation("task-1", revision = 10L))
        fixture.runtime.revisions["task-1"] = 10L
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/tasks/task-1/run",
                token = fixture.readOnlyToken,
                headers = mutationHeaders("run-1", revision = 10L)
            )
        )
        assertEquals(403, response.status)
        assertEquals("scope_denied", errorCode(response))
    }

    @Test
    fun rejectsBrowserOriginatedRequests() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request("GET", "/api/v1/status", headers = mapOf("origin" to "https://evil.example"))
        )
        assertEquals(403, response.status)
        assertEquals("browser_origin_rejected", errorCode(response))
    }

    @Test
    fun rejectsHostOutsideLoopbackPolicy() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request("GET", "/api/v1/status", headers = mapOf("host" to "rebind.evil.example"))
        )
        assertEquals(400, response.status)
        assertEquals("invalid_host", errorCode(response))
    }

    @Test
    fun rejectsBodyLargerThanAuthorizerBudget() = runTest {
        val fixture = fixture(maxPayloadBytes = 8)
        val response = fixture.controller.handle(
            request(
                "GET",
                "/api/v1/status",
                token = fixture.token,
                body = ByteArray(9)
            )
        )
        assertEquals(413, response.status)
        assertEquals("payload_too_large", errorCode(response))
    }

    @Test
    fun enforcesRequestRateBudget() = runTest {
        val fixture = fixture(maxRequestsPerWindow = 1)
        val first = fixture.controller.handle(
            request("GET", "/api/v1/status", token = fixture.token)
        )
        val second = fixture.controller.handle(
            request("GET", "/api/v1/status", token = fixture.token)
        )
        assertEquals(200, first.status)
        assertEquals(429, second.status)
        assertEquals("rate_limited", errorCode(second))
    }

    @Test
    fun rejectsTransportBindingMismatch() = runTest {
        val fixture = fixture(bindingFingerprint = "fp-1")
        val response = fixture.controller.handle(
            request(
                "GET",
                "/api/v1/status",
                token = fixture.token,
                headers = mapOf("x-nexaflow-transport-key" to "fp-2")
            )
        )
        assertEquals(401, response.status)
        assertEquals("binding_mismatch", errorCode(response))
    }

    @Test
    fun rejectsUnparsableTarget() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            AgentHttpRequest(
                method = "GET",
                target = "http://[::1",
                headers = loopbackHeaders(fixture.token),
                body = ByteArray(0)
            )
        )
        assertEquals(400, response.status)
        assertEquals("invalid_target", errorCode(response))
    }

    @Test
    fun rejectsUnknownRouteEvenWithValidToken() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request("POST", "/api/v1/unknown", token = fixture.token, body = "{}".toByteArray())
        )
        assertEquals(404, response.status)
        assertEquals("not_found", errorCode(response))
    }

    // ---- pairing and session ---------------------------------------------

    @Test
    fun completesAValidPairingChallenge() = runTest {
        val fixture = fixture()
        val offer = fixture.manager.beginPairing(
            AgentIdentityRequest("agent-paired", "Paired Agent")
        ) as AgentPairingStartResult.Started
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/auth/pair/complete",
                body = """
                    {"challengeId":"${offer.offer.challengeId}",
                     "challengeSecret":"${offer.offer.challengeSecret}"}
                """.trimIndent().toByteArray()
            )
        )
        assertEquals(200, response.status)
        assertEquals(
            "agent-paired",
            json(response)["agentId"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun rejectsPairingWithUnknownChallenge() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/auth/pair/complete",
                body = """{"challengeId":"missing","challengeSecret":"nope"}""".toByteArray()
            )
        )
        assertEquals(400, response.status)
        assertEquals("invalid_pairing_challenge", errorCode(response))
    }

    @Test
    fun rejectsPairingWithBlankFields() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/auth/pair/complete",
                body = """{"challengeId":"","challengeSecret":""}""".toByteArray()
            )
        )
        assertEquals(400, response.status)
        assertEquals("invalid_pairing_challenge", errorCode(response))
    }

    @Test
    fun rejectsBlankRefreshToken() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/auth/session",
                body = """{"refreshToken":""}""".toByteArray()
            )
        )
        assertEquals(400, response.status)
        assertEquals("invalid_refresh_token", errorCode(response))
    }

    @Test
    fun rejectsUnknownRefreshToken() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/auth/session",
                body = """{"refreshToken":"unknown-refresh"}""".toByteArray()
            )
        )
        assertEquals(401, response.status)
        assertEquals("invalid_refresh_token", errorCode(response))
    }

    @Test
    fun exchangesRefreshTokenForShortLivedAccess() = runTest {
        val fixture = fixture()
        val grant = fixture.manager.grantPermanentAccess(
            AgentIdentityRequest("agent-session", "Session Agent")
        ) as AgentGrantResult.Granted
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/auth/session",
                body = """{"refreshToken":"${grant.credential.refreshToken}"}""".toByteArray()
            )
        )
        assertEquals(200, response.status)
        val body = json(response)
        assertEquals("agent-session", body["agentId"]!!.jsonPrimitive.content)
        assertTrue(body["accessToken"]!!.jsonPrimitive.content.isNotBlank())
    }

    // ---- task mutation ----------------------------------------------------

    @Test
    fun requiresIdempotencyKeyForCreate() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/tasks",
                token = fixture.token,
                body = taskBody("New task")
            )
        )
        assertEquals(428, response.status)
        assertEquals("idempotency_key_required", errorCode(response))
        assertEquals(0, fixture.persistence.commitCount)
    }

    @Test
    fun rejectsUnknownJsonFieldsOnCreate() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/tasks",
                token = fixture.token,
                headers = mapOf("idempotency-key" to "create-bad-json"),
                body = """{"task":{"name":"x"},"unexpected":true}""".toByteArray()
            )
        )
        assertEquals(400, response.status)
        assertEquals("invalid_json", errorCode(response))
        assertEquals(0, fixture.persistence.commitCount)
    }

    @Test
    fun createsTaskWithRevisionEtag() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/tasks",
                token = fixture.token,
                headers = mapOf("idempotency-key" to "create-1"),
                body = taskBody("Created through REST")
            )
        )
        assertEquals(201, response.status)
        assertEquals("\"1\"", response.headers["ETag"])
        assertNull(response.headers["X-Idempotent-Replay"])
        assertEquals(1, fixture.persistence.commitCount)
        val stored = fixture.repository.getAutomationById(
            json(response)["id"]!!.jsonPrimitive.content
        )
        assertNotNull(stored)
        assertEquals("Created through REST", stored!!.name)
    }

    @Test
    fun replaysIdempotentCreateWithoutCommittingAgain() = runTest {
        val fixture = fixture()
        fixture.repository.saveAutomation(automation("task-existing", revision = 10L))
        fixture.runtime.revisions["task-existing"] = 10L
        fixture.persistence.resolveResult = AutomationPersistenceResult.IdempotentReplay(
            automationId = "task-existing",
            revision = 10L
        )
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/tasks",
                token = fixture.token,
                headers = mapOf("idempotency-key" to "create-replay"),
                body = taskBody("Replayed")
            )
        )
        assertEquals(200, response.status)
        assertEquals("true", response.headers["X-Idempotent-Replay"])
        assertEquals(0, fixture.persistence.commitCount)
    }

    @Test
    fun surfacesHumanApprovalRequirement() = runTest {
        val fixture = fixture()
        fixture.approvalRequired = true
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/tasks",
                token = fixture.token,
                headers = mapOf("idempotency-key" to "create-approval"),
                body = taskBody("High risk task")
            )
        )
        assertEquals(428, response.status)
        assertEquals("human_approval_required", errorCode(response))
        assertTrue(response.body.toString(StandardCharsets.UTF_8).contains("\"contentHash\":\"hash-1\""))
    }

    @Test
    fun requiresIfMatchForUpdate() = runTest {
        val fixture = fixture()
        fixture.repository.saveAutomation(automation("task-1", revision = 10L))
        val response = fixture.controller.handle(
            request(
                "PATCH",
                "/api/v1/tasks/task-1",
                token = fixture.token,
                headers = mapOf("idempotency-key" to "update-no-match"),
                body = taskBody("Renamed")
            )
        )
        assertEquals(428, response.status)
        assertEquals("if_match_required", errorCode(response))
    }

    @Test
    fun rejectsStaleRevisionOnUpdate() = runTest {
        val fixture = fixture()
        fixture.repository.saveAutomation(automation("task-1", revision = 10L))
        fixture.runtime.revisions["task-1"] = 10L
        val response = fixture.controller.handle(
            request(
                "PATCH",
                "/api/v1/tasks/task-1",
                token = fixture.token,
                headers = mutationHeaders("update-stale", revision = 9L),
                body = taskBody("Renamed")
            )
        )
        assertEquals(409, response.status)
        assertEquals("revision_conflict", errorCode(response))
    }

    @Test
    fun updatesTaskWhenRevisionMatches() = runTest {
        val fixture = fixture()
        fixture.repository.saveAutomation(automation("task-1", revision = 10L))
        fixture.runtime.revisions["task-1"] = 10L
        val response = fixture.controller.handle(
            request(
                "PATCH",
                "/api/v1/tasks/task-1",
                token = fixture.token,
                headers = mutationHeaders("update-ok", revision = 10L),
                body = taskBody("Renamed")
            )
        )
        assertEquals(200, response.status)
        assertEquals("Renamed", fixture.repository.getAutomationById("task-1")!!.name)
    }

    @Test
    fun requiresIfMatchForDelete() = runTest {
        val fixture = fixture()
        fixture.repository.saveAutomation(automation("task-1", revision = 10L))
        val response = fixture.controller.handle(
            request(
                "DELETE",
                "/api/v1/tasks/task-1",
                token = fixture.token,
                headers = mapOf("idempotency-key" to "delete-no-match")
            )
        )
        assertEquals(428, response.status)
        assertEquals("if_match_required", errorCode(response))
    }

    @Test
    fun deletesExistingTaskThroughTheMutationBoundary() = runTest {
        val fixture = fixture()
        fixture.repository.saveAutomation(automation("task-1", revision = 10L))
        fixture.runtime.revisions["task-1"] = 10L
        val response = fixture.controller.handle(
            request(
                "DELETE",
                "/api/v1/tasks/task-1",
                token = fixture.token,
                headers = mutationHeaders("delete-ok", revision = 10L)
            )
        )
        assertEquals(200, response.status)
        assertNull(fixture.repository.getAutomationById("task-1"))
        assertTrue(fixture.runtime.deletionRequests.contains("task-1"))
    }

    @Test
    fun reportsMissingTaskOnDelete() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request(
                "DELETE",
                "/api/v1/tasks/absent",
                token = fixture.token,
                headers = mutationHeaders("delete-absent", revision = 1L)
            )
        )
        assertEquals(404, response.status)
        assertEquals("task_not_found", errorCode(response))
    }

    @Test
    fun disablesTaskWithoutDroppingIt() = runTest {
        val fixture = fixture()
        fixture.repository.saveAutomation(automation("task-1", revision = 10L, enabled = true))
        fixture.runtime.revisions["task-1"] = 10L
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/tasks/task-1/disable",
                token = fixture.token,
                headers = mutationHeaders("disable-1", revision = 10L)
            )
        )
        assertEquals(200, response.status)
        assertEquals(false, fixture.repository.getAutomationById("task-1")!!.enabled)
    }

    @Test
    fun runsTaskAndReturnsExecutionRecord() = runTest {
        val fixture = fixture()
        fixture.repository.saveAutomation(automation("task-1", revision = 10L))
        fixture.runtime.revisions["task-1"] = 10L
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/tasks/task-1/run",
                token = fixture.token,
                headers = mutationHeaders("run-ok-1", revision = 10L)
            )
        )
        assertEquals(200, response.status)
        assertEquals("run-1", json(response)["id"]!!.jsonPrimitive.content)
        assertEquals(1, fixture.runtime.runCount)
    }

    @Test
    fun rejectsRunWhenRevisionIsStale() = runTest {
        val fixture = fixture()
        fixture.repository.saveAutomation(automation("task-1", revision = 10L))
        fixture.runtime.revisions["task-1"] = 12L
        val response = fixture.controller.handle(
            request(
                "POST",
                "/api/v1/tasks/task-1/run",
                token = fixture.token,
                headers = mutationHeaders("run-stale", revision = 10L)
            )
        )
        assertEquals(409, response.status)
        assertEquals("revision_conflict", errorCode(response))
        assertEquals(0, fixture.runtime.runCount)
    }

    // ---- reads ------------------------------------------------------------

    @Test
    fun reportsLoopbackOnlyStatus() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request("GET", "/api/v1/status", token = fixture.token)
        )
        assertEquals(200, response.status)
        val body = json(response)
        assertEquals("true", body["loopbackOnly"]!!.jsonPrimitive.content)
        assertEquals("true", body["accessEnabled"]!!.jsonPrimitive.content)
    }

    @Test
    fun listsTasksWithEffectiveRevisions() = runTest {
        val fixture = fixture()
        fixture.repository.saveAutomation(automation("task-1", revision = 10L))
        fixture.runtime.revisions["task-1"] = 12L
        val response = fixture.controller.handle(
            request("GET", "/api/v1/tasks", token = fixture.token)
        )
        assertEquals(200, response.status)
        assertTrue(response.body.toString(StandardCharsets.UTF_8).contains("\"revision\":12"))
    }

    @Test
    fun returnsSingleTaskWithEtag() = runTest {
        val fixture = fixture()
        fixture.repository.saveAutomation(automation("task-1", revision = 10L))
        fixture.runtime.revisions["task-1"] = 10L
        val response = fixture.controller.handle(
            request("GET", "/api/v1/tasks/task-1", token = fixture.token)
        )
        assertEquals(200, response.status)
        assertEquals("\"10\"", response.headers["ETag"])
    }

    @Test
    fun returnsNotFoundForUnknownTask() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request("GET", "/api/v1/tasks/absent", token = fixture.token)
        )
        assertEquals(404, response.status)
        assertEquals("task_not_found", errorCode(response))
    }

    @Test
    fun clampsHistoryLimitToMaximum() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request("GET", "/api/v1/history?limit=5000", token = fixture.token)
        )
        assertEquals(200, response.status)
        assertEquals(200, fixture.runtime.lastHistoryLimit)
    }

    @Test
    fun servesAuditTrail() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request("GET", "/api/v1/audit?limit=5", token = fixture.token)
        )
        assertEquals(200, response.status)
        assertEquals(5, fixture.runtime.lastAuditLimit)
    }

    @Test
    fun servesCapabilitiesAndCatalogToAuthorizedCallers() = runTest {
        val fixture = fixture()
        val capabilities = fixture.controller.handle(
            request("GET", "/api/v1/capabilities", token = fixture.token)
        )
        val catalog = fixture.controller.handle(
            request("GET", "/api/v1/catalog", token = fixture.token)
        )
        assertEquals(200, capabilities.status)
        assertEquals(200, catalog.status)
        assertTrue(catalog.body.toString(StandardCharsets.UTF_8).contains("triggers"))
    }

    @Test
    fun servesOpenApiDocumentWithoutAuthentication() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            request("GET", "/api/v1/openapi.json", headers = mapOf("host" to "127.0.0.1:8766"))
        )
        assertEquals(200, response.status)
        assertTrue(response.body.toString(StandardCharsets.UTF_8).contains("\"openapi\""))
    }

    // ---- fixture ----------------------------------------------------------

    private suspend fun fixture(
        maxPayloadBytes: Int = 256 * 1024,
        maxRequestsPerWindow: Int = 120,
        bindingFingerprint: String? = null
    ): Fixture {
        val ids = sequenceGenerator("id")
        val secrets = sequenceGenerator("secret")
        val manager = AgentAccessManager(
            store = InMemoryStore(),
            idGenerator = ids,
            secretGenerator = secrets
        )
        manager.setAccessEnabled(true)
        val binding = AgentIdentityBinding(
            transportKeyFingerprint = bindingFingerprint
        )
        val grant = manager.grantPermanentAccess(
            AgentIdentityRequest("agent-1", "Agent One", binding = binding)
        ) as AgentGrantResult.Granted
        val session = manager.exchangeRefreshToken(
            grant.credential.refreshToken,
            binding
        ) as AgentSessionIssueResult.Issued
        val readerGrant = manager.grantAccess(
            AgentIdentityRequest(
                "agent-reader",
                "Read Only",
                requestedMode = AgentGrantMode.READ_ONLY
            )
        ) as AgentGrantResult.Granted
        val readerSession = manager.exchangeRefreshToken(
            readerGrant.credential.refreshToken
        ) as AgentSessionIssueResult.Issued

        val repository = FakeRepository()
        val persistence = FakePersistence(repository)
        val fixtureRef = Fixture()
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
            approvalRiskGate = { fixtureRef.approvalRequired },
            approvalHash = { "hash-1" },
            clockMillis = { 100L },
            idGenerator = { "generated-1" }
        )
        val schedulePreview = AgentSchedulePreviewService()
        return fixtureRef.apply {
            this.repository = repository
            this.persistence = persistence
            managerRef = manager
            token = session.credential.accessToken
            readOnlyToken = readerSession.credential.accessToken
            controller = AgentApiController(
                repository = repository,
                commandService = service,
                simulationService = AgentSimulationService(service, schedulePreview),
                schedulePreviewService = schedulePreview,
                schemaRegistry = AutomationSchemaRegistry(),
                accessManager = manager,
                authorizer = AgentRequestAuthorizer(
                    accessManager = manager,
                    maxPayloadBytes = maxPayloadBytes,
                    maxRequestsPerWindow = maxRequestsPerWindow
                ),
                runtime = runtime,
                hostPolicy = AgentApiHostPolicy()
            )
        }
    }

    private fun automation(
        id: String,
        revision: Long,
        enabled: Boolean = false
    ) = Automation(
        id = id,
        name = "Task $id",
        description = "",
        icon = "auto_awesome",
        iconColor = 0xFF0B57D0L,
        backgroundColor = 0xFFE3EEFAL,
        category = "custom",
        priority = 1,
        enabled = enabled,
        triggers = emptyList(),
        actions = emptyList(),
        cooldownSeconds = 0,
        createdAt = revision,
        updatedAt = revision
    )

    private fun taskBody(name: String) =
        """{"task":{"name":"$name"}}""".toByteArray(StandardCharsets.UTF_8)

    private fun mutationHeaders(idempotency: String, revision: Long) = mapOf(
        "idempotency-key" to idempotency,
        "if-match" to "\"$revision\""
    )

    private fun loopbackHeaders(token: String? = null): Map<String, String> =
        buildMap {
            put("host", "127.0.0.1:8766")
            token?.let { put("authorization", "Bearer $it") }
        }

    private fun request(
        method: String,
        target: String,
        token: String? = null,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray = ByteArray(0)
    ) = AgentHttpRequest(
        method = method,
        target = target,
        headers = loopbackHeaders(token) + headers,
        body = body
    )

    private fun json(response: AgentHttpResponse) =
        Json.parseToJsonElement(
            response.body.toString(StandardCharsets.UTF_8)
        ).jsonObject.let { root ->
            (root["error"] as? kotlinx.serialization.json.JsonObject) ?: root
        }

    private fun errorCode(response: AgentHttpResponse): String =
        Json.parseToJsonElement(response.body.toString(StandardCharsets.UTF_8))
            .jsonObject["error"]!!
            .jsonObject["code"]!!
            .jsonPrimitive.content

    private fun sequenceGenerator(prefix: String): () -> String {
        var value = 0
        return {
            value += 1
            "$prefix-$value-abcdefgh"
        }
    }

    private class Fixture {
        lateinit var controller: AgentApiController
        lateinit var repository: FakeRepository
        lateinit var persistence: FakePersistence
        lateinit var managerRef: AgentAccessManager
        lateinit var token: String
        lateinit var readOnlyToken: String
        val runtime = FakeRuntime()
        var approvalRequired = false

        val manager: AgentAccessManager get() = managerRef
    }

    private class FakeRepository(
        initial: List<Automation> = emptyList()
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
            state.value = state.value.map {
                if (it.id == id) it.copy(enabled = enabled) else it
            }
        }
    }

    private class FakePersistence(
        private val repository: FakeRepository
    ) : AutomationMutationPersistence {
        private val revisions = mutableMapOf<String, Long>()
        var commitCount = 0
            private set
        var resolveResult: AutomationPersistenceResult? = null
        var forcedResult: AutomationPersistenceResult? = null

        override suspend fun commit(
            request: AutomationMutationCommitRequest
        ): AutomationPersistenceResult {
            commitCount += 1
            forcedResult?.let { return it }
            val automationId = request.automation.id
            val stored = repository.getAutomationById(automationId)
            val currentRevision = revisions[automationId] ?: stored?.updatedAt ?: 0L
            val expectedRevision = request.context.expectedRevision
            if (expectedRevision != null && expectedRevision != currentRevision) {
                return AutomationPersistenceResult.RevisionConflict(currentRevision)
            }
            val newRevision = currentRevision + 1L
            if (request.kind == AutomationMutationKind.DELETE) {
                repository.deleteAutomation(request.automation)
            } else {
                repository.saveAutomation(request.automation)
            }
            revisions[automationId] = newRevision
            return AutomationPersistenceResult.Committed(automationId, newRevision)
        }

        override suspend fun resolveStoredIdempotency(
            context: com.nexaflow.core.automationcontrol.AutomationMutationContext,
            kind: AutomationMutationKind,
            automationId: String?,
            requestFingerprint: String,
            occurredAt: Long
        ): AutomationPersistenceResult? = resolveResult
    }

    private class InMemoryStore : AgentSecurityStore {
        private var state = AgentSecurityStateV1()

        override suspend fun read(): AgentSecurityStateV1 = state

        override suspend fun <T> mutate(
            block: (AgentSecurityStateV1) -> Pair<AgentSecurityStateV1, T>
        ): T {
            val (next, result) = block(state)
            state = next
            return result
        }
    }

    private class FakeRuntime : AgentApiRuntime {
        val revisions = mutableMapOf<String, Long>()
        val deletionRequests = mutableListOf<String>()
        var runCount = 0
            private set
        var lastHistoryLimit = -1
            private set
        var lastAuditLimit = -1
            private set

        override suspend fun effectiveRevision(automationId: String): Long? =
            revisions[automationId]

        override suspend fun onEnabled(automation: Automation) = Unit

        override suspend fun onDisabled(automation: Automation) = Unit

        override suspend fun prepareForDeletion(automation: Automation): Boolean {
            deletionRequests += automation.id
            return true
        }

        override suspend fun reserveRun(
            request: AgentApiRunReservation
        ): AgentApiRunReservationResult = AgentApiRunReservationResult.Acquired

        override suspend fun run(
            automation: Automation,
            request: AgentApiRunContext
        ): ExecutionRecord {
            runCount += 1
            return ExecutionRecord(
                id = "run-1",
                automationId = automation.id,
                automationName = automation.name,
                success = true,
                message = "ok",
                executedAt = 200L
            )
        }

        override suspend fun latestHistory(limit: Int): List<ExecutionRecord> {
            lastHistoryLimit = limit
            return emptyList()
        }

        override suspend fun latestAudit(limit: Int): List<AgentApiAuditEventV1> {
            lastAuditLimit = limit
            return emptyList()
        }

        override suspend fun capabilities(): AgentApiCapabilitiesV1 =
            AgentApiCapabilitiesV1(
                observedAtMillis = 1L,
                neverObserved = false,
                capabilities = emptyList(),
                privilegeObservedAtMillis = 1L,
                privileges = emptyList()
            )
    }
}
