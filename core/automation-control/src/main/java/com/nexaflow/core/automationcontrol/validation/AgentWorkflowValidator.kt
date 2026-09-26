package com.nexaflow.core.automationcontrol.validation

import com.nexaflow.domain.catalog.AutomationNodeCatalog
import com.nexaflow.domain.catalog.NodeConfigurationValidator
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.workflow.AutomationDependencyValidator
import com.nexaflow.domain.workflow.WorkflowValidationIssue
import com.nexaflow.domain.workflow.WorkflowValidator

data class AgentConfigValidationIssue(
    val owner: String,
    val key: String,
    val code: String
)

data class AgentWorkflowValidationReport(
    val workflowIssues: List<WorkflowValidationIssue>,
    val configIssues: List<AgentConfigValidationIssue>
) {
    val isValid: Boolean
        get() = workflowIssues.isEmpty() && configIssues.isEmpty()
}

/**
 * Agent-facing preflight combines the existing structural/dependency rules with
 * the canonical typed node schemas. It performs no device I/O and never
 * executes an action.
 */
object AgentWorkflowValidator {

    fun validate(
        automation: Automation,
        catalog: Collection<Automation>
    ): AgentWorkflowValidationReport {
        val completeCatalog = catalog.filterNot { it.id == automation.id } + automation
        val workflowIssues = WorkflowValidator.validate(automation).issues +
            AutomationDependencyValidator.validate(completeCatalog).issuesFor(automation.id)

        val configIssues = buildList {
            automation.triggers.forEachIndexed { index, trigger ->
                val schema = AutomationNodeCatalog.definitionFor(trigger.type).configuration
                NodeConfigurationValidator.validate(schema, trigger.config).forEach { issue ->
                    add(
                        AgentConfigValidationIssue(
                            owner = "triggers[$index]",
                            key = issue.key,
                            code = issue.code.name
                        )
                    )
                }
            }
            automation.actions.forEachIndexed { index, action ->
                validateAction("actions[$index]", action, this)
            }
            automation.exitActions.forEachIndexed { index, action ->
                validateAction("exitActions[$index]", action, this)
            }
            automation.constraints.forEachIndexed { index, constraint ->
                validateBoundedConfig(
                    owner = "constraints[$index]",
                    config = constraint.config,
                    destination = this
                )
            }
        }

        return AgentWorkflowValidationReport(
            workflowIssues = workflowIssues,
            configIssues = configIssues
        )
    }

    private fun validateAction(
        owner: String,
        action: Action,
        destination: MutableList<AgentConfigValidationIssue>
    ) {
        val schema = AutomationNodeCatalog.definitionFor(action.type).configuration
        NodeConfigurationValidator.validate(schema, action.config).forEach { issue ->
            destination += AgentConfigValidationIssue(
                owner = owner,
                key = issue.key,
                code = issue.code.name
            )
        }
        action.endBehavior?.let { behavior ->
            validateBoundedConfig(
                owner = "$owner.endBehavior",
                config = behavior.config,
                destination = destination
            )
        }
    }

    private fun validateBoundedConfig(
        owner: String,
        config: Map<String, String>,
        destination: MutableList<AgentConfigValidationIssue>
    ) {
        if (config.size > WorkflowValidator.MAX_CONFIG_ENTRIES) {
            destination += AgentConfigValidationIssue(
                owner = owner,
                key = "*",
                code = "TOO_MANY_CONFIG_VALUES"
            )
        }
        config.forEach { (key, value) ->
            if (key.isBlank()) {
                destination += AgentConfigValidationIssue(
                    owner = owner,
                    key = key,
                    code = "BLANK_CONFIG_KEY"
                )
            }
            if (value.length > WorkflowValidator.MAX_CONFIG_VALUE_LENGTH) {
                destination += AgentConfigValidationIssue(
                    owner = owner,
                    key = key,
                    code = "CONFIG_VALUE_TOO_LONG"
                )
            }
        }
    }
}
