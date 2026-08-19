package com.nexaflow.core.logging

import kotlinx.coroutines.flow.Flow

/**
 * Conservative secret redactor used at the logging boundary. It removes common
 * credential shapes before a value reaches any LogStore implementation. The
 * redactor intentionally favours masking over preserving diagnostic detail.
 */
object SecretRedactor {
    private val bearer = Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]{8,}")
    private val querySecret = Regex("(?i)([?&;](?:token|api[_-]?key|password|secret|authorization)=)[^&\\s]+")
    private val assignmentSecret = Regex("(?i)((?:token|api[_-]?key|password|secret|authorization)\\s*[:=]\\s*)([^,;\\s}]+)")
    private val jsonSecret = Regex("(?i)(\\\"(?:token|api[_-]?key|password|secret|authorization)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")")
    private val vaultReference = Regex("vault:[A-Za-z0-9._:-]{1,128}")

    fun redact(text: String?): String? = text
        ?.replace(bearer, "$1[REDACTED]")
        ?.replace(querySecret, "$1[REDACTED]")
        ?.replace(assignmentSecret, "$1[REDACTED]")
        ?.replace(jsonSecret, "$1[REDACTED]$2")
        ?.replace(vaultReference, "vault:[REDACTED]")

    fun redactMap(metadata: Map<String, String>): Map<String, String> = metadata.mapValues { (key, value) ->
        if (key.contains("token", true) || key.contains("secret", true) ||
            key.contains("password", true) || key.contains("authorization", true)
        ) "[REDACTED]" else redact(value).orEmpty()
    }
}

/** Applies [SecretRedactor] once at the common logging boundary. */
class RedactingLogStore(private val delegate: LogStore) : LogStore {
    override fun timeline(): Flow<List<ExecutionTimelineEntry>> = delegate.timeline()
    override fun errors(): Flow<List<ErrorLogEntry>> = delegate.errors()
    override fun metrics(): Flow<List<PerformanceMetric>> = delegate.metrics()

    override suspend fun recordExecution(entry: ExecutionTimelineEntry) {
        delegate.recordExecution(entry.copy(message = SecretRedactor.redact(entry.message).orEmpty()))
    }

    override suspend fun recordError(entry: ErrorLogEntry) {
        delegate.recordError(
            entry.copy(
                message = SecretRedactor.redact(entry.message).orEmpty(),
                stackTrace = SecretRedactor.redact(entry.stackTrace)
            )
        )
    }

    override suspend fun recordMetric(metric: PerformanceMetric) = delegate.recordMetric(metric)

    override suspend fun clear() = delegate.clear()
}
