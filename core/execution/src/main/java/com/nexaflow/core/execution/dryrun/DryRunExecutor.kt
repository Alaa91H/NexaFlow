package com.nexaflow.core.execution.dryrun

import com.nexaflow.core.execution.workflow.WorkflowNode
import com.nexaflow.core.execution.workflow.WorkflowExecutionResult
import com.nexaflow.core.execution.workflow.WorkflowInterpreter
import com.nexaflow.core.execution.workflow.NodeResult
import com.nexaflow.core.execution.workflow.ActionExecutor
import com.nexaflow.core.rom.model.SystemControlResult

/**
 * Executes a workflow in a completely safe, simulated environment.
 * 
 * The DryRunExecutor uses a mock ActionExecutor that never modifies system state.
 * It is used for validating workflows, checking permission requirements before
 * actual execution, and unit testing logic branches.
 */
class DryRunExecutor {

    /**
     * Dry-runs a workflow node tree and returns the simulated execution result.
     * All action nodes instantly succeed with a simulated message.
     */
    suspend fun simulate(root: WorkflowNode): WorkflowExecutionResult {
        val dryRunActionExecutor = ActionExecutor { action ->
            SystemControlResult.ok("Simulated execution of ${action.type}")
        }
        
        val interpreter = WorkflowInterpreter(executor = dryRunActionExecutor)
        return interpreter.execute(root)
    }
}
