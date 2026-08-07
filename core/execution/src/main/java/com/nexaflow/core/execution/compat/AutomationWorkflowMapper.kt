package com.nexaflow.core.execution.compat

import com.nexaflow.core.execution.workflow.Workflow
import com.nexaflow.core.execution.workflow.WorkflowNode
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.Trigger

/**
 * The translated form of a legacy [Automation] for the Phase-3 workflow engine.
 *
 * - [entryTriggers]  — the triggers kept as entry metadata (the event sources
 *   that fire this task). The monitors gate on them exactly as before.
 * - [runWorkflow]    — `actions` mapped to a [WorkflowNode.SequenceNode] of
 *   action nodes (sequential, fail-fast — same as the legacy engine).
 * - [exitWorkflow]   — `exitActions` mapped to their own sequence; null when the
 *   task has no exit behavior (or restores state instead, see [revertOnExit]).
 * - [revertOnExit]   — when true, the runner captures a `StateTransaction`
 *   before the run and rolls it back on exit instead of running exit actions.
 */
data class MappedAutomation(
    val automationId: String,
    val automationName: String,
    val priority: Int,
    val entryTriggers: List<Trigger>,
    val runWorkflow: Workflow,
    val exitWorkflow: Workflow?,
    val revertOnExit: Boolean
)

/**
 * Pure, testable translation of the legacy [Automation] model into [Workflow]
 * trees — the compatibility layer of Phase 4. No behavior change, no UI change:
 * the legacy monitors keep firing automations; the new engine consumes them via
 * this mapping.
 */
object AutomationWorkflowMapper {

    fun map(automation: Automation): MappedAutomation {
        val runRoot = WorkflowNode.SequenceNode(
            id = "run:${automation.id}",
            children = automation.actions.mapIndexed { index, action ->
                actionNode("run", automation.id, index, action)
            }
        )
        // Per-action adaptive end behavior (SET_VALUE) is synthesized into the
        // exit workflow so the compat runner honors it too. REVERT is handled by
        // the global revertOnExit snapshot path (per-setting revert lives in the
        // ExecutionEngine's DeviceStateSnapshot.restoreSetting).
        val endBehaviorNodes = automation.actions.mapIndexedNotNull { index, action ->
            val behavior = action.endBehavior
            if (behavior?.mode == EndMode.SET_VALUE) {
                actionNode("exit", automation.id, index, action.withConfig(behavior.config))
            } else {
                null
            }
        }
        val exitRoot = if (automation.exitActions.isEmpty() && endBehaviorNodes.isEmpty()) {
            null
        } else {
            WorkflowNode.SequenceNode(
                id = "exit:${automation.id}",
                children = endBehaviorNodes + automation.exitActions.mapIndexed { index, action ->
                    actionNode("exit", automation.id, index, action)
                }
            )
        }
        return MappedAutomation(
            automationId = automation.id,
            automationName = automation.name,
            priority = automation.priority,
            entryTriggers = automation.triggers,
            runWorkflow = Workflow(id = automation.id, name = automation.name, root = runRoot),
            exitWorkflow = exitRoot?.let {
                Workflow(id = "exit:${automation.id}", name = "Exit: ${automation.name}", root = it)
            },
            revertOnExit = automation.revertOnExit
        )
    }

    private fun actionNode(kind: String, automationId: String, index: Int, action: Action): WorkflowNode.ActionNode {
        return WorkflowNode.ActionNode(
            id = "$kind:$automationId:$index:${action.type}",
            action = action
        )
    }
}
