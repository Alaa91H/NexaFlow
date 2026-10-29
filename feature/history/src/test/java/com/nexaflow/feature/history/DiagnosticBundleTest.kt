package com.nexaflow.feature.history

import com.nexaflow.core.logging.ErrorLogEntry
import com.nexaflow.core.logging.ExecutionTimelineEntry
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticBundleTest {

    private val build = DiagnosticBuildInfo(
        versionName = "3.60.0",
        versionCode = 3_060_000,
        androidRelease = "16",
        sdkInt = 36,
        manufacturer = "Google",
        model = "Reference phone",
        locale = "ar-SA"
    )

    private fun automation(id: String, enabled: Boolean = true) = Automation(
        id = id,
        name = "Task $id",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "SYSTEM",
        priority = 1,
        enabled = enabled,
        triggers = listOf(Trigger(TriggerType.TIME, emptyMap())),
        actions = emptyList(),
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun record(index: Int, message: String = "ok") = ExecutionRecord(
        id = "run-$index",
        automationId = "task-1",
        automationName = "Task task-1",
        success = index % 2 == 0,
        message = message,
        executedAt = index.toLong(),
        channel = "ROOT",
        actionResults = listOf(
            ActionExecutionResult(
                actionType = "SYSTEM_BRIGHTNESS",
                success = true,
                message = "done",
                durationMs = 5L
            )
        )
    )

    @Test
    fun includesIdentityScheduleAndExecutionSections() {
        val json = Json.parseToJsonElement(
            DiagnosticBundle.build(
                generatedAtMs = 42L,
                build = build,
                channel = "ROOT",
                automations = listOf(automation("task-1"), automation("task-2", enabled = false)),
                executions = listOf(record(1)),
                timeline = emptyList(),
                errors = emptyList()
            )
        ).jsonObject

        assertEquals("nexaflow.diagnostics/1", json["schema"]!!.jsonPrimitive.content)
        assertEquals("3.60.0", json["app"]!!.jsonObject["versionName"]!!.jsonPrimitive.content)
        val scheduler = json["scheduler"]!!.jsonObject
        assertEquals("2", scheduler["automationCount"]!!.jsonPrimitive.content)
        assertEquals("1", scheduler["enabledCount"]!!.jsonPrimitive.content)
        assertEquals(
            "TIME",
            scheduler["automations"]!!.jsonArray[0].jsonObject["triggers"]!!.jsonArray[0].jsonPrimitive.content
        )
        assertEquals("ROOT", json["executionChannel"]!!.jsonPrimitive.content)
        assertEquals(
            "run-1",
            json["executions"]!!.jsonObject["recent"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun redactsSecretsEverywhere() {
        val json = DiagnosticBundle.build(
            generatedAtMs = 1L,
            build = build,
            channel = null,
            automations = listOf(automation("task-1").copy(name = "token=xxxx-fixture-xxxx")),
            executions = listOf(record(1, "Authorization: Bearer abcdefghijklmnop")),
            timeline = listOf(
                ExecutionTimelineEntry(
                    id = "t1",
                    automationId = "task-1",
                    automationName = "Task",
                    kind = "RUN",
                    success = true,
                    message = "api_key=super-secret-value",
                    startedAt = 1L,
                    durationMs = 2L
                )
            ),
            errors = listOf(
                ErrorLogEntry(
                    id = "e1",
                    source = "engine",
                    message = "password=hunter2",
                    stackTrace = "vault:agent/token",
                    timestamp = 3L
                )
            )
        )

        assertFalse(json.contains("xxxx-fixture-xxxx"))
        assertFalse(json.contains("abcdefghijklmnop"))
        assertFalse(json.contains("super-secret-value"))
        assertFalse(json.contains("hunter2"))
        assertFalse(json.contains("vault:agent/token"))
        assertTrue(json.contains("[REDACTED]"))
    }

    @Test
    fun boundsRetainedExecutionsAndLogsNewestFirst() {
        val executions = (1..80).map { record(it) }
        val timeline = (1..250).map {
            ExecutionTimelineEntry(
                id = "t$it",
                automationId = "task-1",
                automationName = "Task",
                kind = "RUN",
                success = true,
                message = "m$it",
                startedAt = it.toLong(),
                durationMs = 1L
            )
        }
        val json = Json.parseToJsonElement(
            DiagnosticBundle.build(
                generatedAtMs = 1L,
                build = build,
                channel = null,
                automations = emptyList(),
                executions = executions,
                timeline = timeline,
                errors = emptyList()
            )
        ).jsonObject

        val recent = json["executions"]!!.jsonObject["recent"]!!.jsonArray
        assertEquals(DiagnosticBundle.MAX_EXECUTIONS, recent.size)
        assertEquals("run-80", recent[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals(
            "run-31",
            recent.last().jsonObject["id"]!!.jsonPrimitive.content
        )
        val rows = json["logs"]!!.jsonObject["timeline"]!!.jsonArray
        assertEquals(DiagnosticBundle.MAX_TIMELINE, rows.size)
        assertEquals("250", rows[0].jsonObject["message"]!!.jsonPrimitive.content.removePrefix("m"))
        assertEquals("51", rows.last().jsonObject["message"]!!.jsonPrimitive.content.removePrefix("m"))
    }
}
