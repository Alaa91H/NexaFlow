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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMcpControllerTest {

    @Test
    fun modernDiscoveryUsesStatelessProtocol() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            modernRequest(
                token = fixture.accessToken,
                method = "server/discover",
                id = "discover-1"
            )
        )

        assertEquals(200, response.status)
        assertEquals(
            AgentMcpController.MODERN_VERSION,
            response.headers["MCP-Protocol-Version"]
        )
        val result = parse(response)["result"]!!.jsonObject
        assertEquals("complete", result["resultType"]!!.jsonPrimitive.content)
        assertTrue(
            result["supportedVersions"]!!.jsonArray.any {
                it.jsonPrimitive.content == AgentMcpController.MODERN_VERSION
            }
        )
    }

    @Test
    fun modernHeaderMismatchFailsBeforeToolExecution() = runTest {
        val fixture = fixture()
        val request = modernRequest(
            token = fixture.accessToken,
            method = "tools/list",
            id = "1"
        ).copy(
            headers = modernHeaders(
                token = fixture.accessToken,
                method = "tools/call"
            )
        )

        val response = fixture.controller.handle(request)

        assertEquals(400, response.status)
        assertEquals(0, fixture.executor.calls)
        assertEquals(
            "-32020",
            parse(response)["error"]!!.jsonObject["code"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun legacyToolsListIsDeterministicAndAuthenticated() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            legacyRequest(
                token = fixture.accessToken,
                method = "tools/list",
                id = "2"
            )
        )

        assertEquals(200, response.status)
        val tools = parse(response)["result"]!!.jsonObject["tools"]!!.jsonArray
        val names = tools.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun toolCallForwardsExactlyOnce() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            legacyRequest(
                token = fixture.accessToken,
                method = "tools/call",
                id = "3",
                params = buildJsonObject {
                    put("name", "nexaflow.get_capabilities")
                    putJsonObject("arguments") {}
                }
            )
        )

        assertEquals(200, response.status)
        assertEquals(1, fixture.executor.calls)
        assertEquals(
            "false",
            parse(response)["result"]!!.jsonObject["isError"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun missingBearerTokenIsRejectedAtHttpBoundary() = runTest {
        val fixture = fixture()
        val response = fixture.controller.handle(
            legacyRequest(
                token = "",
                method = "tools/list",
                id = "4"
            )
        )

        assertEquals(401, response.status)
        assertTrue(
            response.headers["WWW-Authenticate"].orEmpty().startsWith("Bearer")
        )
    }

    private suspend fun fixture(): Fixture {
        val manager = AgentAccessManager(
            store = InMemoryStore(),
            idGenerator = sequenceGenerator(),
            secretGenerator = sequenceGenerator("secret")
        )
        manager.setAccessEnabled(true)
        val grant = manager.grantPermanentAccess(
            AgentIdentityRequest("test-agent", "Test Agent")
        ) as AgentGrantResult.Granted
        val session = manager.exchangeRefreshToken(
            grant.credential.refreshToken
        ) as AgentSessionIssueResult.Issued
        val executor = FakeExecutor()
        return Fixture(
            controller = AgentMcpController(
                accessManager = manager,
                authorizer = AgentRequestAuthorizer(manager),
                toolExecutor = executor
            ),
            executor = executor,
            accessToken = session.credential.accessToken
        )
    }

    private fun modernRequest(
        token: String,
        method: String,
        id: String
    ): AgentHttpRequest {
        val params = buildJsonObject {
            putJsonObject("_meta") {
                put(
                    "io.modelcontextprotocol/protocolVersion",
                    AgentMcpController.MODERN_VERSION
                )
                putJsonObject("io.modelcontextprotocol/clientCapabilities") {}
                putJsonObject("io.modelcontextprotocol/clientInfo") {
                    put("name", "test")
                    put("version", "1")
                }
            }
        }
        return request(
            token = token,
            method = method,
            id = id,
            params = params,
            headers = modernHeaders(token, method)
        )
    }

    private fun legacyRequest(
        token: String,
        method: String,
        id: String,
        params: JsonObject = buildJsonObject {}
    ) = request(
        token = token,
        method = method,
        id = id,
        params = params,
        headers = mapOf(
            "host" to "127.0.0.1:8766",
            "content-type" to "application/json",
            "authorization" to "Bearer $token"
        )
    )

    private fun request(
        token: String,
        method: String,
        id: String,
        params: JsonObject,
        headers: Map<String, String>
    ): AgentHttpRequest {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        return AgentHttpRequest(
            method = "POST",
            target = "/mcp",
            headers = headers,
            body = body.toString().toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun modernHeaders(
        token: String,
        method: String
    ) = mapOf(
        "host" to "127.0.0.1:8766",
        "content-type" to "application/json",
        "authorization" to "Bearer $token",
        "mcp-protocol-version" to AgentMcpController.MODERN_VERSION,
        "mcp-method" to method
    )

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
        val controller: AgentMcpController,
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
