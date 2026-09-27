package com.nexaflow.core.agentapi

import com.nexaflow.core.agentsecurity.AgentAccessManager
import com.nexaflow.core.agentsecurity.AgentGrantResult
import com.nexaflow.core.agentsecurity.AgentIdentityRequest
import com.nexaflow.core.agentsecurity.AgentRequestAuthorizer
import com.nexaflow.core.agentsecurity.AgentSecurityStateV1
import com.nexaflow.core.agentsecurity.AgentSecurityStore
import com.nexaflow.core.agentsecurity.AgentSessionIssueResult
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentA2AControllerTest {

    @Test
    fun agentCardIsPublicAndListsMcpParitySkills() {
        val controller = AgentA2AController(
            accessManager = manager(),
            authorizer = AgentRequestAuthorizer(manager()),
            skillExecutor = FakeExecutor()
        )
        val response = controller.card("http://127.0.0.1:8766")

        assertEquals(200, response.status)
        val card = Json.parseToJsonElement(
            response.body.toString(StandardCharsets.UTF_8)
        ).jsonObject
        assertEquals("NexaFlow", card["name"]!!.jsonPrimitive.content)
        val skills = card["skills"]!!.jsonArray
        val ids = skills.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertEquals(ids.sorted(), ids)
        assertTrue(ids.contains("nexaflow.create_task"))
        assertTrue(ids.contains("nexaflow.get_capabilities"))
    }

    @Test
    fun skillIdsMatchMcpRegistryExactly() {
        assertEquals(
            AgentMcpToolRegistry.tools.map { it.name }.toSortedSet(),
            AgentA2ACard.skillIds().toSortedSet()
        )
    }

    @Test
    fun messageSendExecutesSkillAndReturnsCompletedTask() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            rpcRequest(
                token = fixture.accessToken,
                method = "message/send",
                id = "a2a-1",
                params = buildJsonObject {
                    putJsonObject("message") {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject {
                                put("kind", "data")
                                putJsonObject("data") {
                                    put("skill", "nexaflow.get_capabilities")
                                    putJsonObject("input") {}
                                }
                            })
                        }
                    }
                }
            )
        )

        assertEquals(200, response.status)
        val result = parse(response)["result"]!!.jsonObject
        assertEquals("completed", result["status"]!!.jsonObject["state"]!!.jsonPrimitive.content)
        assertEquals(1, fixture.executor.calls)
    }

    @Test
    fun unknownSkillFailsClosedWithoutExecution() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            rpcRequest(
                token = fixture.accessToken,
                method = "message/send",
                id = "a2a-2",
                params = buildJsonObject {
                    putJsonObject("message") {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject {
                                put("kind", "data")
                                putJsonObject("data") {
                                    put("skill", "nexaflow.nope")
                                    putJsonObject("input") {}
                                }
                            })
                        }
                    }
                }
            )
        )

        assertEquals(200, response.status)
        val result = parse(response)["result"]!!.jsonObject
        assertEquals("failed", result["status"]!!.jsonObject["state"]!!.jsonPrimitive.content)
        assertEquals(0, fixture.executor.calls)
    }

    @Test
    fun streamingAndCancelAreExplicitlyUnsupported() = runTest {
        val fixture = fixture()
        val stream = fixture.controller.handle(
            rpcRequest(fixture.accessToken, "message/stream", "a2a-3")
        )
        assertEquals(200, stream.status)
        val streamError = parse(stream)["error"]!!.jsonObject
        assertEquals(-32601, streamError["code"]!!.jsonPrimitive.content.toInt())
        assertTrue(streamError["message"]!!.jsonPrimitive.content.contains("message/send"))

        val cancel = fixture.controller.handle(
            rpcRequest(fixture.accessToken, "tasks/cancel", "a2a-4")
        )
        assertEquals(200, cancel.status)
        assertTrue(parse(cancel).containsKey("error"))
    }

    @Test
    fun missingBearerTokenIsRejected() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            rpcRequest(
                token = "",
                method = "message/send",
                id = "a2a-5",
                params = buildJsonObject {
                    putJsonObject("message") {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject {
                                put("kind", "data")
                                putJsonObject("data") {
                                    put("skill", "nexaflow.get_capabilities")
                                    putJsonObject("input") {}
                                }
                            })
                        }
                    }
                }
            )
        )

        assertEquals(401, response.status)
    }

    private suspend fun fixture(): Fixture {
        val manager = manager()
        manager.setAccessEnabled(true)
        val grant = manager.grantPermanentAccess(
            AgentIdentityRequest("test-agent", "Test Agent")
        ) as AgentGrantResult.Granted
        val session = manager.exchangeRefreshToken(
            grant.credential.refreshToken
        ) as AgentSessionIssueResult.Issued
        val executor = FakeExecutor()
        return Fixture(
            controller = AgentA2AController(
                accessManager = manager,
                authorizer = AgentRequestAuthorizer(manager),
                skillExecutor = executor
            ),
            executor = executor,
            accessToken = session.credential.accessToken
        )
    }

    private fun manager() = AgentAccessManager(
        store = InMemoryStore(),
        idGenerator = sequenceGenerator(),
        secretGenerator = sequenceGenerator("secret")
    )

    private fun rpcRequest(
        token: String,
        method: String,
        id: String,
        params: JsonObject = buildJsonObject {}
    ): AgentHttpRequest {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        return AgentHttpRequest(
            method = "POST",
            target = "/a2a",
            headers = mapOf(
                "host" to "127.0.0.1:8766",
                "content-type" to "application/json",
                "authorization" to "Bearer $token"
            ),
            body = body.toString().toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun parse(response: AgentHttpResponse) =
        Json.parseToJsonElement(
            response.body.toString(StandardCharsets.UTF_8)
        ).jsonObject

    private fun sequenceGenerator(prefix: String = "id"): () -> String {
        var value = 0
        return {
            value += 1
            "$prefix-$value-abcdefgh"
        }
    }

    private data class Fixture(
        val controller: AgentA2AController,
        val executor: FakeExecutor,
        val accessToken: String
    )

    private class FakeExecutor : AgentMcpToolExecutor {
        var calls = 0

        override suspend fun execute(
            toolName: String,
            arguments: JsonObject,
            sourceRequest: AgentHttpRequest
        ): AgentHttpResponse {
            calls += 1
            return AgentHttpResponse(
                status = 200,
                body = """{"ok":true}""".toByteArray(StandardCharsets.UTF_8),
                headers = mapOf("Content-Type" to "application/json")
            )
        }
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
}
