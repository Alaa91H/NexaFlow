package com.nexaflow.domain.diagnostics

import kotlinx.serialization.Serializable

/**
 * Unified structured diagnostics system for NexaFlow.
 *
 * Every error, warning, or status in the system must use this taxonomy.
 * This replaces scattered string-based error reporting with a machine-readable,
 * actionable diagnostic model that the UI and AI layer can reason about.
 */

/** Broad category to allow quick routing in the UI and logging layers. */
@Serializable
enum class DiagnosticCategory {
    CAPABILITY,
    BACKEND,
    POLICY,
    PERMISSION,
    PLUGIN,
    SCHEDULER,
    WORKFLOW,
    ACCESSIBILITY,
    NETWORK,
    STORAGE,
    SECURITY,
    AI,
    SYSTEM,
    UNKNOWN
}

/** Severity aligned to standard log levels. */
@Serializable
enum class DiagnosticSeverity {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    CRITICAL
}

/** Whether and how this error can be remediated without a developer fix. */
@Serializable
enum class DiagnosticRecoverability {
    /** Will self-recover (e.g., temporary network loss). */
    TRANSIENT,
    /** User must take an action (e.g., grant permission, enable Shizuku). */
    USER_ACTION_REQUIRED,
    /** Requires workflow/config change. */
    CONFIGURATION_REQUIRED,
    /** Non-recoverable at runtime; may require an app update. */
    PERMANENT,
    /** Unknown recoverability. */
    UNKNOWN
}

/**
 * A single structured diagnostic entry.
 *
 * Every subsystem (capabilities, backends, workflows, plugins) emits these
 * instead of raw strings, enabling:
 *  - Structured display in the Execution Inspector UI
 *  - AI-readable failure analysis
 *  - Filtered diagnostics export
 */
@Serializable
data class DiagnosticEntry(
    /** Unique ID for deduplication and referencing in the Inspector. */
    val id: String,
    val severity: DiagnosticSeverity,
    val category: DiagnosticCategory,
    val recoverability: DiagnosticRecoverability,

    /** Human-readable title shown in the UI. */
    val title: String,

    /** Developer-facing technical detail. MUST NOT contain secrets. */
    val technicalDetail: String,

    /** User-facing actionable suggestion shown in the UI. */
    val suggestedAction: String? = null,

    /** Backend that emitted this diagnostic, if applicable. */
    val backendId: String? = null,

    /** Workflow context, if applicable. */
    val workflowId: String? = null,
    val runId: String? = null,
    val nodeId: String? = null,

    /** Epoch millisecond timestamp. */
    val timestampMs: Long = System.currentTimeMillis(),

    /** Arbitrary key-value metadata — must not contain sensitive data. */
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(id.isNotBlank()) { "DiagnosticEntry id must not be blank" }
        require(title.isNotBlank()) { "DiagnosticEntry title must not be blank" }
        require(title.length <= 256) { "DiagnosticEntry title must be at most 256 chars" }
        require(technicalDetail.length <= 4096) { "technicalDetail must be at most 4096 chars" }
    }

    val isActionable: Boolean
        get() = recoverability == DiagnosticRecoverability.USER_ACTION_REQUIRED

    val isCritical: Boolean
        get() = severity == DiagnosticSeverity.CRITICAL || severity == DiagnosticSeverity.ERROR

    companion object {
        fun warn(
            id: String,
            category: DiagnosticCategory,
            title: String,
            detail: String,
            suggestedAction: String? = null
        ) = DiagnosticEntry(
            id = id,
            severity = DiagnosticSeverity.WARN,
            category = category,
            recoverability = DiagnosticRecoverability.UNKNOWN,
            title = title,
            technicalDetail = detail,
            suggestedAction = suggestedAction
        )

        fun error(
            id: String,
            category: DiagnosticCategory,
            recoverability: DiagnosticRecoverability,
            title: String,
            detail: String,
            suggestedAction: String? = null,
            backendId: String? = null,
            metadata: Map<String, String> = emptyMap()
        ) = DiagnosticEntry(
            id = id,
            severity = DiagnosticSeverity.ERROR,
            category = category,
            recoverability = recoverability,
            title = title,
            technicalDetail = detail,
            suggestedAction = suggestedAction,
            backendId = backendId,
            metadata = metadata
        )

        fun info(
            id: String,
            category: DiagnosticCategory,
            title: String,
            detail: String
        ) = DiagnosticEntry(
            id = id,
            severity = DiagnosticSeverity.INFO,
            category = category,
            recoverability = DiagnosticRecoverability.TRANSIENT,
            title = title,
            technicalDetail = detail
        )
    }
}

/** Collects and filters diagnostics across subsystems. */
interface DiagnosticsCollector {
    fun emit(entry: DiagnosticEntry)
    fun entries(
        minSeverity: DiagnosticSeverity = DiagnosticSeverity.DEBUG,
        category: DiagnosticCategory? = null,
        runId: String? = null
    ): List<DiagnosticEntry>
    fun clear()
}

/** Thread-safe in-memory diagnostics collector. For production use,
 *  wire to the Room-backed persistence layer in the data module. */
class InMemoryDiagnosticsCollector(
    private val maxEntries: Int = 500
) : DiagnosticsCollector {
    private val entries = ArrayDeque<DiagnosticEntry>()
    private val lock = Any()

    override fun emit(entry: DiagnosticEntry) {
        synchronized(lock) {
            if (entries.size >= maxEntries) entries.removeFirst()
            entries.addLast(entry)
        }
    }

    override fun entries(
        minSeverity: DiagnosticSeverity,
        category: DiagnosticCategory?,
        runId: String?
    ): List<DiagnosticEntry> = synchronized(lock) {
        entries.filter { e ->
            e.severity.ordinal >= minSeverity.ordinal &&
                (category == null || e.category == category) &&
                (runId == null || e.runId == runId)
        }.toList()
    }

    override fun clear() = synchronized(lock) { entries.clear() }
}
