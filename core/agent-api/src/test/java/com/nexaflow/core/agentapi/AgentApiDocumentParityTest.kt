package com.nexaflow.core.agentapi

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentApiDocumentParityTest {

    @Test
    fun checkedInOpenApiMatchesRuntimeContract() {
        assertEquals(
            AgentApiDocuments.openApiJson.trim(),
            File(repositoryRoot(), "docs/api/openapi-v1.json").readText().trim()
        )
    }

    @Test
    fun checkedInTaskSchemaMatchesRuntimeContract() {
        assertEquals(
            AgentApiDocuments.taskSchemaJson.trim(),
            File(repositoryRoot(), "docs/api/task-schema-v1.json").readText().trim()
        )
    }

    private fun repositoryRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        repeat(MAX_PARENT_SEARCH) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile
                ?: error("Unable to locate repository root")
        }
        error("Unable to locate repository root")
    }

    private companion object {
        const val MAX_PARENT_SEARCH = 8
    }
}
