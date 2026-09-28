package com.nexaflow.domain.canonical

import kotlinx.serialization.Serializable

/**
 * T11 — Unified Error Model & Structured Execution Journal (plan §21–§23,
 * ADR-009/ADR-012 groundwork).
 *
 * The error model is the single machine-readable vocabulary every new path
 * uses; raw execution errors never reach the user (plan rule 46.16). The
 * journal records one run's lifecycle without secrets: metadata is
 * developer-safe by construction — supplying a secret-looking entry throws.
 */
@Serializable
enum class ExecutionErrorCode {
    INVALID_CONFIGURATION,
    UNSUPPORTED,
    PERMISSION_MISSING,
    CAPABILITY_MISSING,
    SECURITY_REJECTED,
    TARGET_NOT_FOUND,
    TIMEOUT,
    TRANSIENT_FAILURE,
    PROVIDER_UNAVAILABLE,
    CANCELLED,
    CONFLICT,
    MIGRATION_FAILED,
}

/** Lifecycle phases of one workflow run, in execution order (plan §22). */
@Serializable
enum class ExecutionPhase {
    TRIGGER_EVALUATION,
    SEMANTIC_VALIDATION,
    CAPABILITY_RESOLUTION,
    PLANNING,
    COMMAND_EXECUTION,
    FINAL_RESULT,
}

/** Outcome of a phase or the run. */
@Serializable
enum class ExecutionPhaseStatus {
    PASSED,
    SKIPPED,
    FAILED,
    CANCELLED,
}

/** Which trigger events were observed for this run (plan §23 provenance). */
@Serializable
data class TriggerEvaluation(
    /** Stable predicate id of each observed event. */
    val observedPredicates: List<PredicateId> = emptyList(),
    /** Stable target ids the trigger monitored. */
    val monitoredTargets: List<TargetId> = emptyList(),
    /** Condition outcomes: stable field id -> satisfied? */
    val conditionOutcomes: Map<String, Boolean> = emptyMap(),
) {
    init {
        require(conditionOutcomes.size <= MAX_CONDITIONS) {
            "conditionOutcomes exceeds $MAX_CONDITIONS entries"
        }
    }

    companion object {
        private const val MAX_CONDITIONS = 256
    }
}

/** Why a command or the run skipped execution (plan §23 "Why didn't it run?"). */
@Serializable
data class SkipReason(
    val phase: ExecutionPhase,
    val errorCode: ExecutionErrorCode,
    /** Human-readable, non-sensitive explanation rendered by diagnostics UI. */
    val message: String,
)

/** One command execution record inside a run. */
@Serializable
data class CommandRecord(
    val commandId: String,
    val target: TargetId,
    val operation: OperationId,
    val status: ExecutionPhaseStatus,
    val errorCode: ExecutionErrorCode? = null,
    val durationMs: Long = 0,
    /**
     * Backend/provider channel that executed the command. Only stable
     * vocabulary (backend ids), never a raw process or command line.
     */
    val providerLabel: String? = null,
    /** Sanitized, secret-free metadata (validated by [ExecutionRunRecord]). */
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * The complete structured record of one workflow run. Deterministic data,
 * serializable, and safe to persist: secret-looking metadata is rejected at
 * construction so a leaked token cannot enter the journal by accident.
 */
@Serializable
data class ExecutionRunRecord(
    val runId: String,
    val workflowNodeCount: Int,
    val startedAtMs: Long,
    val completedAtMs: Long? = null,
    val triggerEvaluation: TriggerEvaluation? = null,
    /** Findings that blocked the run (e.g. T09 verdict findings). */
    val validationFailures: List<ValidationFinding> = emptyList(),
    /** Capability exclusions from the T07 resolver (plan §23 rendering). */
    val capabilityExclusions: List<ProviderExclusion> = emptyList(),
    val commandRecords: List<CommandRecord> = emptyList(),
    val skipReason: SkipReason? = null,
    val finalStatus: ExecutionPhaseStatus = ExecutionPhaseStatus.PASSED,
    val finalErrorCode: ExecutionErrorCode? = null,
) {
    init {
        require(runId.matches(RUN_ID)) { "runId must be a bounded opaque id" }
        require(workflowNodeCount >= 0) { "workflowNodeCount must be non-negative" }
        require(completedAtMs == null || completedAtMs >= startedAtMs) {
            "completedAtMs must not precede startedAtMs"
        }
        metadataSweep(commandRecords)
        skipReason?.let { sweepText(it.message, "skipReason") }
        validationFailures.forEach {
            sweepText(it.message, "validationFailures")
        }
        capabilityExclusions.forEach {
            sweepText(it.reason, "capabilityExclusions")
        }
    }

    val durationMs: Long?
        get() = completedAtMs?.let { it - startedAtMs }

    private fun metadataSweep(records: List<CommandRecord>) {
        records.forEach { record ->
            record.metadata.forEach { (key, value) ->
                sweepEntry(key, value, "command ${record.commandId}")
            }
        }
    }

    private fun sweepText(text: String, origin: String) {
        if (SECRET_VALUE_HINTS.any { text.contains(it, ignoreCase = true) }) {
            throw IllegalArgumentException(
                "$origin text looks like it carries a secret; refusing to journal it",
            )
        }
    }

    private fun sweepEntry(key: String, value: String, origin: String) {
        if (key.contains(SECRET_KEY_HINT, ignoreCase = true)) {
            throw IllegalArgumentException(
                "$origin metadata key '$key' looks like a secret; refusing to journal it",
            )
        }
        if (SECRET_VALUE_HINTS.any { value.contains(it, ignoreCase = true) }) {
            throw IllegalArgumentException(
                "$origin metadata value looks like a secret; refusing to journal it",
            )
        }
    }

    companion object {
        private val RUN_ID = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{2,127}")
        private const val SECRET_KEY_HINT = "token"
        private val SECRET_VALUE_HINTS = listOf(
            "Bearer ",
            "-----BEGIN",
            "ghp_",
            "sk_live_",
            "AKIA",
        )
    }
}

/**
 * Builds the run record for a workflow that never started executing because
 * validation failed (plan §23: the user must see exactly why). Deterministic.
 */
fun validationBlockedRun(
    runId: String,
    verdict: ValidationVerdict,
    startedAtMs: Long,
): ExecutionRunRecord {
    require(!verdict.isValid) { "validationBlockedRun requires an invalid verdict" }
    return ExecutionRunRecord(
        runId = runId,
        workflowNodeCount = 0,
        startedAtMs = startedAtMs,
        validationFailures = verdict.findings,
        skipReason = SkipReason(
            phase = when (verdict.findings.first().stage) {
                ValidationStage.SEMANTIC -> ExecutionPhase.SEMANTIC_VALIDATION
                ValidationStage.CAPABILITY -> ExecutionPhase.CAPABILITY_RESOLUTION
                else -> ExecutionPhase.PLANNING
            },
            errorCode = when (verdict.findings.first().stage) {
                ValidationStage.CAPABILITY -> ExecutionErrorCode.CAPABILITY_MISSING
                else -> ExecutionErrorCode.INVALID_CONFIGURATION
            },
            message = verdict.findings.first().message,
        ),
        finalStatus = ExecutionPhaseStatus.SKIPPED,
        finalErrorCode = ExecutionErrorCode.INVALID_CONFIGURATION,
    )
}

/**
 * Builds the run record for a workflow whose capability resolution produced
 * no executable provider (plan §23 example: "Shizuku unavailable... no
 * supported provider").
 */
fun capabilityBlockedRun(
    runId: String,
    resolution: CapabilityResolution,
    startedAtMs: Long,
): ExecutionRunRecord {
    require(!resolution.isExecutable) {
        "capabilityBlockedRun requires a non-executable resolution"
    }
    return ExecutionRunRecord(
        runId = runId,
        workflowNodeCount = 0,
        startedAtMs = startedAtMs,
        capabilityExclusions = resolution.exclusions,
        skipReason = SkipReason(
            phase = ExecutionPhase.CAPABILITY_RESOLUTION,
            errorCode = when (resolution.errorCode) {
                CapabilityResolutionError.PRIVILEGE_NOT_GRANTED,
                CapabilityResolutionError.CAPABILITY_MISSING,
                -> ExecutionErrorCode.PERMISSION_MISSING
                else -> ExecutionErrorCode.PROVIDER_UNAVAILABLE
            },
            message = resolution.exclusions.firstOrNull()?.reason
                ?: "no supported provider for this intent on this device",
        ),
        finalStatus = ExecutionPhaseStatus.SKIPPED,
        finalErrorCode = ExecutionErrorCode.PROVIDER_UNAVAILABLE,
    )
}
