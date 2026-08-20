package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.Automation

/** Stable identifiers for save/import/preflight diagnostics. */
enum class WorkflowValidationCode {
    UNSUPPORTED_WORKFLOW_VERSION,
    BLANK_AUTOMATION_ID,
    BLANK_AUTOMATION_NAME,
    INVALID_COOLDOWN,
    TOO_MANY_TRIGGERS,
    TOO_MANY_ACTIONS,
    TOO_MANY_EXIT_ACTIONS,
    BLANK_CONFIG_KEY,
    CONFIG_VALUE_TOO_LONG,
    TOO_MANY_CONFIG_VALUES,
    DUPLICATE_AUTOMATION_ID,
    SELF_DEPENDENCY,
    MISSING_DEPENDENCY,
    CIRCULAR_DEPENDENCY
}

data class WorkflowValidationIssue(
    val code: WorkflowValidationCode,
    val location: String,
    val message: String
)

data class WorkflowValidationResult(val issues: List<WorkflowValidationIssue>) {
    val isValid: Boolean get() = issues.isEmpty()
}

/**
 * Domain-only semantic boundary shared by builder save, import and dry-run.
 * It checks structural safety without evaluating conditions or executing actions.
 */
object WorkflowValidator {
    const val MAX_TRIGGERS = 64
    const val MAX_ACTIONS = 256
    const val MAX_CONFIG_ENTRIES = 64
    const val MAX_CONFIG_VALUE_LENGTH = 8_192

    fun validate(automation: Automation): WorkflowValidationResult {
        val issues = mutableListOf<WorkflowValidationIssue>()
        if (automation.workflowVersion !in 1..Automation.CURRENT_WORKFLOW_VERSION) {
            issues += issue(WorkflowValidationCode.UNSUPPORTED_WORKFLOW_VERSION, "workflowVersion")
        }
        if (automation.id.isBlank()) issues += issue(WorkflowValidationCode.BLANK_AUTOMATION_ID, "id")
        if (automation.name.isBlank()) issues += issue(WorkflowValidationCode.BLANK_AUTOMATION_NAME, "name")
        if (automation.cooldownSeconds < 0) issues += issue(WorkflowValidationCode.INVALID_COOLDOWN, "cooldownSeconds")
        if (automation.triggers.size > MAX_TRIGGERS) issues += issue(WorkflowValidationCode.TOO_MANY_TRIGGERS, "triggers")
        if (automation.actions.size > MAX_ACTIONS) issues += issue(WorkflowValidationCode.TOO_MANY_ACTIONS, "actions")
        if (automation.exitActions.size > MAX_ACTIONS) issues += issue(WorkflowValidationCode.TOO_MANY_EXIT_ACTIONS, "exitActions")
        automation.triggers.forEachIndexed { index, trigger -> validateConfig("triggers[$index]", trigger.config, issues) }
        automation.actions.forEachIndexed { index, action -> validateConfig("actions[$index]", action.config, issues) }
        automation.exitActions.forEachIndexed { index, action -> validateConfig("exitActions[$index]", action.config, issues) }
        return WorkflowValidationResult(issues)
    }

    private fun validateConfig(
        location: String,
        config: Map<String, String>,
        issues: MutableList<WorkflowValidationIssue>
    ) {
        if (config.size > MAX_CONFIG_ENTRIES) issues += issue(WorkflowValidationCode.TOO_MANY_CONFIG_VALUES, location)
        config.forEach { (key, value) ->
            if (key.isBlank()) issues += issue(WorkflowValidationCode.BLANK_CONFIG_KEY, "$location.config")
            if (value.length > MAX_CONFIG_VALUE_LENGTH) {
                issues += issue(WorkflowValidationCode.CONFIG_VALUE_TOO_LONG, "$location.$key")
            }
        }
    }

    private fun issue(code: WorkflowValidationCode, location: String) = WorkflowValidationIssue(
        code = code,
        location = location,
        message = code.name.lowercase().replace('_', ' ')
    )
}
