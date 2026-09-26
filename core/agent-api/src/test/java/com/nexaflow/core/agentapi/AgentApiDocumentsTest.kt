package com.nexaflow.core.agentapi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentApiDocumentsTest {

    @Test
    fun openApiDocumentIsValidJsonAndListsCoreRoutes() {
        val root = Json.parseToJsonElement(AgentApiDocuments.openApiJson).jsonObject

        assertEquals("3.1.0", root.getValue("openapi").jsonPrimitive.content)
        val paths = root.getValue("paths").jsonObject
        assertTrue("/tasks" in paths)
        assertTrue("/simulate" in paths)
        assertTrue("/schedules/preview" in paths)
        assertTrue("/history" in paths)
        assertTrue("/audit" in paths)
    }

    @Test
    fun taskSchemaIsDraft202012AndPinsVersionOne() {
        val root = Json.parseToJsonElement(AgentApiDocuments.taskSchemaJson).jsonObject

        assertEquals(
            "https://json-schema.org/draft/2020-12/schema",
            root.getValue("$" + "schema").jsonPrimitive.content
        )
        val properties = root.getValue("properties").jsonObject
        val schemaVersion = properties.getValue("schemaVersion").jsonObject
        assertEquals("1", schemaVersion.getValue("const").jsonPrimitive.content)
    }
}
