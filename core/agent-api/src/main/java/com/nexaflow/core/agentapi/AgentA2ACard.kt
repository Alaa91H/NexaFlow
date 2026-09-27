package com.nexaflow.core.agentapi

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Phase 14 - A2A agent card.
 *
 * The card is provider-neutral discovery metadata. It never grants access by
 * itself; every A2A skill execution reuses the permanent-agent bearer
 * authorization, scopes, rate limits and the single
 * AutomationCommandService mutation boundary shared with REST/MCP/Binder.
 */
object AgentA2ACard {

    const val PROTOCOL_VERSION = "0.3.0"
    const val AGENT_VERSION = "1.0.0"
    const val AGENT_NAME = "NexaFlow"
    const val CARD_PATH = "/.well-known/agent-card.json"
    const val RPC_PATH = "/a2a"

    fun build(baseUrl: String): JsonObject = buildJsonObject {
        put("name", AGENT_NAME)
        put("description", "Provider-neutral Android automation management: inspect, validate, simulate and manage automation tasks.")
        put("version", AGENT_VERSION)
        put("protocolVersion", PROTOCOL_VERSION)
        put("url", baseUrl.trimEnd('/') + RPC_PATH)
        putJsonArray("capabilities") {
            add(buildJsonObject {
                put("kind", "automation.lifecycle")
                put("description", "List, inspect, validate, simulate, create, update, clone, enable, disable, delete and run automation tasks atomically.")
            })
            add(buildJsonObject {
                put("kind", "automation.diagnostics")
                put("description", "Read capability inventory, node catalog, execution history and redacted agent audit.")
            })
        }
        putJsonArray("skills") {
            AgentMcpToolRegistry.tools.forEach { tool ->
                add(buildJsonObject {
                    put("id", tool.name)
                    put("name", tool.name)
                    put("description", tool.description)
                    put("inputSchema", tool.inputSchema)
                    putJsonArray("tags") {
                        if (tool.readOnly) add(JsonPrimitive("readOnly"))
                        if (tool.destructive) add(JsonPrimitive("destructive"))
                        if (tool.idempotent) add(JsonPrimitive("idempotent"))
                    }
                })
            }
        }
        put("authentication", buildJsonObject {
            put("schemes", buildJsonArray { add(JsonPrimitive("Bearer")) })
            put("description", "Permanent-agent refresh credential exchanged for a short-lived Bearer access session. Agent card itself requires no authentication.")
        })
        put("provider", buildJsonObject {
            put("organization", "NexaFlow")
            put("url", "https://github.com/Alaa91H/NexaFlow")
        })
    }

    fun skillIds(): Set<String> =
        AgentMcpToolRegistry.tools.mapTo(LinkedHashSet()) { it.name }
}
