package com.nexaflow.core.execution

import android.content.Context
import android.os.PowerManager
import com.nexaflow.core.execution.compat.WorkflowCapabilityValidationResult
import com.nexaflow.core.execution.compat.WorkflowCapabilityValidator
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.capability.PrivilegeSnapshot
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.Automation

internal enum class SnapshotFreshness {
    FRESH,
    STALE,
    NEVER_OBSERVED
}

internal fun classifySnapshotFreshness(
    snapshot: CapabilitySnapshot,
    nowMs: Long,
    freshnessMs: Long
): SnapshotFreshness = when {
    snapshot.neverObserved -> SnapshotFreshness.NEVER_OBSERVED
    nowMs - snapshot.observedAtMs > freshnessMs -> SnapshotFreshness.STALE
    else -> SnapshotFreshness.FRESH
}

internal fun classifySnapshotFreshness(
    snapshot: PrivilegeSnapshot,
    nowMs: Long,
    freshnessMs: Long
): SnapshotFreshness = when {
    snapshot.neverObserved -> SnapshotFreshness.NEVER_OBSERVED
    nowMs - snapshot.observedAtMs > freshnessMs -> SnapshotFreshness.STALE
    else -> SnapshotFreshness.FRESH
}

internal data class WorkflowRequirementGateEvaluation(
    val validation: WorkflowCapabilityValidationResult,
    val capabilityFresh: Boolean,
    val missingDetail: String
)

internal fun evaluateWorkflowRequirementGate(
    automation: Automation,
    capabilitySnapshot: CapabilitySnapshot,
    privilegeSnapshot: PrivilegeSnapshot,
    nowMs: Long,
    freshnessMs: Long
): WorkflowRequirementGateEvaluation {
    val capabilityFresh =
        classifySnapshotFreshness(capabilitySnapshot, nowMs, freshnessMs) == SnapshotFreshness.FRESH
    val effectivePrivilegeSnapshot =
        if (classifySnapshotFreshness(privilegeSnapshot, nowMs, freshnessMs) == SnapshotFreshness.FRESH) {
            privilegeSnapshot
        } else {
            PrivilegeSnapshot()
        }
    val validation = WorkflowCapabilityValidator.validate(
        automation = automation,
        capabilitySnapshot = capabilitySnapshot,
        privilegeSnapshot = effectivePrivilegeSnapshot
    )
    val missingDetail = buildList {
        addAll(validation.blockedOwners.map { "node:$it" })
        addAll(validation.missingCapabilities.map { "capability:${it.name}" })
        addAll(validation.missingPrivileges.map { ref ->
            "privilege:${ref.surface.name}:${ref.key}"
        })
    }.joinToString().ifBlank { "unmapped or unavailable execution path" }

    return WorkflowRequirementGateEvaluation(
        validation = validation,
        capabilityFresh = capabilityFresh,
        missingDetail = missingDetail
    )
}

internal fun acquireExecutionWakeLock(
    context: Context,
    tag: String
): PowerManager.WakeLock? = try {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val safeTag = if (tag.length > 60) tag.take(60) else tag
    powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, safeTag)?.apply {
        setReferenceCounted(false)
        acquire(10 * 60 * 1000L)
    }
} catch (_: Throwable) {
    null
}

internal fun buildExecutionMessage(results: List<ActionExecutionResult>): String =
    if (results.isEmpty()) {
        "No actions configured"
    } else {
        results.joinToString(" | ") { it.message }
    }
