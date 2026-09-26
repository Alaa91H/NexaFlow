package com.nexaflow.core.agentapi

import com.nexaflow.core.agentsecurity.AgentOperation
import com.nexaflow.core.agentsecurity.AgentScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class AgentMcpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val requiredScopes: Set<AgentScope>,
    val readOnly: Boolean = false,
    val destructive: Boolean = false,
    val idempotent: Boolean = false
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        put("inputSchema", inputSchema)
        putJsonObject("annotations") {
            put("readOnlyHint", readOnly)
            put("destructiveHint", destructive)
            put("idempotentHint", idempotent)
            put("openWorldHint", false)
        }
    }
}

object AgentMcpToolRegistry {
    private val taskSchema =
        Json.parseToJsonElement(AgentApiDocuments.taskSchemaJson) as JsonObject

    val tools: List<AgentMcpToolDefinition> = listOf(
        read("nexaflow.get_status", "Read NexaFlow agent API status.", AgentOperation.STATUS, emptySchema()),
        read("nexaflow.get_capabilities", "Read observed device capabilities and privileges.", AgentOperation.CATALOG_READ, emptySchema()),
        read("nexaflow.list_triggers", "List canonical trigger schemas.", AgentOperation.CATALOG_READ, emptySchema()),
        read("nexaflow.list_actions", "List canonical action schemas.", AgentOperation.CATALOG_READ, emptySchema()),
        read("nexaflow.list_constraints", "List canonical constraint schemas.", AgentOperation.CATALOG_READ, emptySchema()),
        read("nexaflow.list_tasks", "List automation tasks and revisions.", AgentOperation.TASK_LIST, emptySchema()),
        read("nexaflow.get_task", "Read one automation task and revision.", AgentOperation.TASK_GET, idSchema()),
        read("nexaflow.validate_task", "Validate a task draft without side effects.", AgentOperation.TASK_CREATE, validateSchema()),
        read("nexaflow.preview_schedule", "Preview future schedule occurrences.", AgentOperation.CATALOG_READ, scheduleSchema()),
        read("nexaflow.dry_run_task", "Simulate validation and execution routes without side effects.", AgentOperation.TASK_CREATE, simulationSchema()),
        mutate("nexaflow.create_task", "Create a validated automation task.", AgentOperation.TASK_CREATE, createSchema()),
        mutate("nexaflow.update_task", "Replace a task using optimistic concurrency.", AgentOperation.TASK_UPDATE, updateSchema()),
        AgentMcpToolDefinition(
            name = "nexaflow.clone_task",
            description = "Clone an existing task through read/create control paths.",
            inputSchema = cloneSchema(),
            requiredScopes = AgentOperation.TASK_GET.requiredScopes +
                AgentOperation.TASK_CREATE.requiredScopes,
            idempotent = true
        ),
        mutate("nexaflow.enable_task", "Enable a task at an exact revision.", AgentOperation.TASK_ENABLE, stateSchema()),
        mutate("nexaflow.disable_task", "Disable a task and run lifecycle cleanup.", AgentOperation.TASK_DISABLE, stateSchema()),
        AgentMcpToolDefinition(
            name = "nexaflow.delete_task",
            description = "Lifecycle-clean and delete a task at an exact revision.",
            inputSchema = stateSchema(),
            requiredScopes = AgentOperation.TASK_DELETE.requiredScopes,
            destructive = true,
            idempotent = true
        ),
        mutate("nexaflow.run_task", "Run a task once for an idempotency key and revision.", AgentOperation.TASK_RUN, stateSchema()),
        read("nexaflow.get_history", "Read bounded automation execution history.", AgentOperation.HISTORY_READ, limitSchema()),
        read("nexaflow.get_audit", "Read bounded redacted agent audit history.", AgentOperation.HISTORY_READ, limitSchema())
    ).sortedBy { it.name }

    fun find(name: String): AgentMcpToolDefinition? =
        tools.firstOrNull { it.name == name }

    private fun read(
        name: String,
        description: String,
        operation: AgentOperation,
        schema: JsonObject
    ) = AgentMcpToolDefinition(
        name = name,
        description = description,
        inputSchema = schema,
        requiredScopes = operation.requiredScopes,
        readOnly = true,
        idempotent = true
    )

    private fun mutate(
        name: String,
        description: String,
        operation: AgentOperation,
        schema: JsonObject
    ) = AgentMcpToolDefinition(
        name = name,
        description = description,
        inputSchema = schema,
        requiredScopes = operation.requiredScopes,
        idempotent = true
    )

    private fun emptySchema() = objectSchema(emptyMap())

    private fun idSchema() = objectSchema(
        mapOf("id" to stringSchema(TASK_ID_PATTERN)),
        listOf("id")
    )

    private fun limitSchema() = objectSchema(
        mapOf(
            "limit" to buildJsonObject {
                put("type", "integer")
                put("minimum", 1)
                put("maximum", 200)
                put("default", 50)
            }
        )
    )

    private fun validateSchema() = objectSchema(
        mapOf(
            "task" to taskSchema,
            "existingAutomationId" to stringSchema(TASK_ID_PATTERN)
        ),
        listOf("task")
    )

    private fun scheduleSchema() = objectSchema(
        mapOf(
            "task" to taskSchema,
            "fromEpochMillis" to integerSchema(),
            "count" to integerSchema(1, 32),
            "zonePolicy" to enumSchema("TASK_CONFIG", "DEVICE_LOCAL", "FIXED_IANA"),
            "fixedZoneId" to stringSchema()
        ),
        listOf("task", "fromEpochMillis")
    )

    private fun simulationSchema() = objectSchema(
        mapOf(
            "task" to taskSchema,
            "existingAutomationId" to stringSchema(TASK_ID_PATTERN),
            "schedule" to objectSchema(
                mapOf(
                    "fromEpochMillis" to integerSchema(),
                    "count" to integerSchema(1, 32),
                    "zonePolicy" to enumSchema("TASK_CONFIG", "DEVICE_LOCAL", "FIXED_IANA"),
                    "fixedZoneId" to stringSchema()
                ),
                listOf("fromEpochMillis")
            )
        ),
        listOf("task")
    )

    private fun createSchema() = objectSchema(
        mutationProperties(),
        listOf("task", "idempotencyKey")
    )

    private fun updateSchema() = objectSchema(
        mapOf(
            "id" to stringSchema(TASK_ID_PATTERN),
            "revision" to integerSchema(1)
        ) + mutationProperties(),
        listOf("id", "revision", "task", "idempotencyKey")
    )

    private fun cloneSchema() = objectSchema(
        mapOf(
            "id" to stringSchema(TASK_ID_PATTERN),
            "revision" to integerSchema(1),
            "idempotencyKey" to idempotencySchema(),
            "name" to stringSchema(),
            "enabled" to buildJsonObject { put("type", "boolean") },
            "providerId" to stringSchema(),
            "modelId" to stringSchema(),
            "conversationId" to stringSchema(),
            "requestId" to stringSchema(),
            "riskLevel" to stringSchema()
        ),
        listOf("id", "revision", "idempotencyKey")
    )

    private fun stateSchema() = objectSchema(
        mapOf(
            "id" to stringSchema(TASK_ID_PATTERN),
            "revision" to integerSchema(1),
            "idempotencyKey" to idempotencySchema(),
            "requestId" to stringSchema()
        ),
        listOf("id", "revision", "idempotencyKey")
    )

    private fun mutationProperties() = mapOf(
        "task" to taskSchema,
        "idempotencyKey" to idempotencySchema(),
        "providerId" to stringSchema(),
        "modelId" to stringSchema(),
        "conversationId" to stringSchema(),
        "requestId" to stringSchema(),
        "riskLevel" to stringSchema(),
        "requireExecutable" to buildJsonObject {
            put("type", "boolean")
            put("default", true)
        }
    )

    private fun objectSchema(
        properties: Map<String, JsonObject>,
        required: List<String> = emptyList()
    ) = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") {
            properties.forEach { (key, value) -> put(key, value) }
        }
        if (required.isNotEmpty()) {
            putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
        }
    }

    private fun stringSchema(pattern: String? = null) = buildJsonObject {
        put("type", "string")
        pattern?.let { put("pattern", it) }
    }

    private fun idempotencySchema() = buildJsonObject {
        put("type", "string")
        put("minLength", 8)
        put("maxLength", 256)
    }

    private fun integerSchema(
        minimum: Int? = null,
        maximum: Int? = null
    ) = buildJsonObject {
        put("type", "integer")
        minimum?.let { put("minimum", it) }
        maximum?.let { put("maximum", it) }
    }

    private fun enumSchema(vararg values: String) = buildJsonObject {
        put("type", "string")
        put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    }

    private const val TASK_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"
}
