package com.nexaflow.feature.history

import com.nexaflow.core.logging.ErrorLogEntry
import com.nexaflow.core.logging.ExecutionTimelineEntry
import com.nexaflow.core.logging.SecretRedactor
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Device/app identity attached to every diagnostic bundle. */
data class DiagnosticBuildInfo(
    val versionName: String,
    val versionCode: Long,
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val locale: String
)

/**
 * Local diagnostic bundle (NFA-09): the evidence a user can attach to a bug
 * report without sending anything to a server.
 *
 * The bundle is deliberately assembled from data the app already keeps — the
 * schedule definition, the durable execution log and the in-memory log store —
 * so exporting never touches the network and never needs elevated permissions.
 * Every free-text field passes through [SecretRedactor] at this boundary, then
 * the bundle is bounded (50 executions, 200 timeline rows, 50 errors) so it
 * stays small enough to email.
 */
object DiagnosticBundle {

    const val SCHEMA = "nexaflow.diagnostics/1"
    const val MAX_EXECUTIONS = 50
    const val MAX_TIMELINE = 200
    const val MAX_ERRORS = 50
    private const val MAX_MESSAGE_CHARS = 2_000

    fun build(
        generatedAtMs: Long,
        build: DiagnosticBuildInfo,
        channel: String?,
        automations: List<Automation>,
        executions: List<ExecutionRecord>,
        timeline: List<ExecutionTimelineEntry>,
        errors: List<ErrorLogEntry>
    ): String {
        val root = buildJsonObject {
            put("schema", SCHEMA)
            put("generatedAtMs", generatedAtMs)
            putJsonObject("app") {
                put("versionName", build.versionName)
                put("versionCode", build.versionCode)
                put("androidRelease", build.androidRelease)
                put("sdkInt", build.sdkInt)
                put("manufacturer", redact(build.manufacturer))
                put("model", redact(build.model))
                put("locale", build.locale)
            }
            put("executionChannel", channel)
            putJsonObject("scheduler") {
                put("automationCount", automations.size)
                put("enabledCount", automations.count { it.enabled })
                putJsonArray("automations") {
                    automations
                        .sortedBy { it.id }
                        .forEach { automation ->
                            add(
                                buildJsonObject {
                                    put("id", automation.id)
                                    put("name", redact(automation.name))
                                    put("enabled", automation.enabled)
                                    put("priority", automation.priority)
                                    putJsonArray("triggers") {
                                        automation.triggers
                                            .map { it.type.name }
                                            .distinct()
                                            .sorted()
                                            .forEach { type -> add(JsonPrimitive(type)) }
                                    }
                                }
                            )
                        }
                }
            }
            putJsonObject("executions") {
                put("retained", minOf(executions.size, MAX_EXECUTIONS))
                putJsonArray("recent") {
                    executions
                        .sortedByDescending { it.executedAt }
                        .take(MAX_EXECUTIONS)
                        .forEach { record ->
                            add(
                                buildJsonObject {
                                    put("id", record.id)
                                    put("automationId", record.automationId)
                                    put("automationName", redact(record.automationName))
                                    put("success", record.success)
                                    put("executedAt", record.executedAt)
                                    put("channel", record.channel)
                                    put("message", redact(record.message))
                                    putJsonArray("actions") {
                                        record.actionResults.forEach { result ->
                                            add(
                                                buildJsonObject {
                                                    put("actionType", result.actionType)
                                                    put("success", result.success)
                                                    put("durationMs", result.durationMs)
                                                    put("message", redact(result.message))
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                }
            }
            putJsonObject("logs") {
                putJsonArray("timeline") {
                    timeline
                        .sortedByDescending { it.startedAt }
                        .take(MAX_TIMELINE)
                        .forEach { entry ->
                            add(
                                buildJsonObject {
                                    put("timestamp", entry.startedAt)
                                    put("level", entry.kind)
                                    put("automationId", entry.automationId)
                                    put("automationName", redact(entry.automationName))
                                    put("success", entry.success)
                                    put("durationMs", entry.durationMs)
                                    put("channel", entry.channel)
                                    put("runId", entry.runId)
                                    put("message", redact(entry.message))
                                }
                            )
                        }
                }
                putJsonArray("errors") {
                    errors
                        .sortedByDescending { it.timestamp }
                        .take(MAX_ERRORS)
                        .forEach { entry ->
                            add(
                                buildJsonObject {
                                    put("timestamp", entry.timestamp)
                                    put("source", redact(entry.source))
                                    put("message", redact(entry.message))
                                    put("stackTrace", redact(entry.stackTrace))
                                }
                            )
                        }
                }
            }
        }
        return PRETTY.encodeToString(JsonObject.serializer(), root)
    }

    internal fun redact(value: String?): String =
        SecretRedactor.redact(value)?.take(MAX_MESSAGE_CHARS).orEmpty()

    private val PRETTY = Json { prettyPrint = true }
}
