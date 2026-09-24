package com.nexaflow.core.execution

import com.nexaflow.core.execution.compat.WorkflowCapabilityValidationResult
import com.nexaflow.core.execution.compat.WorkflowCapabilityValidator
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.capability.PrivilegeSnapshot
import com.nexaflow.domain.models.Automation

internal enum class WorkflowAdmissionState {
    ADMITTED,
    STALE_EVIDENCE_ADMITTED,
    BLOCKED
}

internal data class WorkflowAdmissionDecision(
    val state: WorkflowAdmissionState,
    val validation: WorkflowCapabilityValidationResult? = null
) {
    val missingDetail: String
        get() {
            val result = validation ?: return "unmapped or unavailable execution path"
            return buildList {
                addAll(result.blockedOwners.map { "node:\${it}" })
                addAll(result.missingCapabilities.map { "capability:\${it.name}" })
                addAll(result.missingPrivileges.map { ref ->
                    "privilege:\${ref.surface.name}:\${ref.key}"
                })
            }.joinToString().ifBlank { "unmapped or unavailable execution path" }
        }
}

/**
 * Side-effect-free whole-workflow admission decision.
 *
 * Fresh capability/privilege observations may block a run. Stale privilege
 * observations are deliberately downgraded to UNKNOWN so a grant that changed
 * while the app was backgrounded cannot create a false denial. A stale
 * capability snapshot keeps the historical admit-and-live-verify behavior.
 */
internal class WorkflowAdmissionGate(
    private val capabilitySnapshotProvider: (() -> CapabilitySnapshot)?,
    private val privilegeSnapshotProvider: (() -> PrivilegeSnapshot)?,
    private val capabilitySnapshotInvalidator: (() -> Unit)?,
    private val privilegeSnapshotInvalidator: (() -> Unit)?,
    private val nowMs: () -> Long,
    private val freshnessMs: Long
) {
    fun evaluate(automation: Automation): WorkflowAdmissionDecision {
        val capabilitySnapshot = capabilitySnapshotProvider?.invoke()
            ?: return WorkflowAdmissionDecision(WorkflowAdmissionState.ADMITTED)
        val now = nowMs()
        val capabilityFresh = classifySnapshotFreshness(
            capabilitySnapshot,
            now,
            freshnessMs
        ) == SnapshotFreshness.FRESH

        val rawPrivilegeSnapshot = privilegeSnapshotProvider?.invoke() ?: PrivilegeSnapshot()
        val effectivePrivilegeSnapshot = if (
            classifySnapshotFreshness(
                rawPrivilegeSnapshot,
                now,
                freshnessMs
            ) == SnapshotFreshness.FRESH
        ) {
            rawPrivilegeSnapshot
        } else {
            PrivilegeSnapshot()
        }

        val validation = WorkflowCapabilityValidator.validate(
            automation = automation,
            capabilitySnapshot = capabilitySnapshot,
            privilegeSnapshot = effectivePrivilegeSnapshot
        )
        if (validation.admissible) {
            return WorkflowAdmissionDecision(
                state = WorkflowAdmissionState.ADMITTED,
                validation = validation
            )
        }
        if (!capabilityFresh) {
            return WorkflowAdmissionDecision(
                state = WorkflowAdmissionState.STALE_EVIDENCE_ADMITTED,
                validation = validation
            )
        }

        // Fresh observed evidence proved no declared requirement branch is
        // usable. Ask both stores to refresh so the next occurrence sees a
        // grant that may have landed immediately after this observation.
        runCatching { capabilitySnapshotInvalidator?.invoke() }
        runCatching { privilegeSnapshotInvalidator?.invoke() }
        return WorkflowAdmissionDecision(
            state = WorkflowAdmissionState.BLOCKED,
            validation = validation
        )
    }
}
