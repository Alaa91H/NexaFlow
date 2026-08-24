package com.nexaflow.core.execution.dryrun

import com.nexaflow.core.execution.workflow.WorkflowNode
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DryRunExecutorTest {

    @Test
    fun `simulate executes action safely`() = runBlocking {
        val executor = DryRunExecutor()
        val action = Action("a1", ActionType.SYSTEM_WIFI, emptyMap())
        val root = WorkflowNode.ActionNode("node1", action)

        val result = executor.simulate(root)

        assertTrue(result.success)
        assertEquals(1, result.nodeResults.size)
        assertTrue(result.nodeResults[0].success)
        assertTrue(result.nodeResults[0].message.contains("Simulated execution"))
    }
}
