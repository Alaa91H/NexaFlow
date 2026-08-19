package com.nexaflow.core.execution.variables

import androidx.paging.PagingSource
import com.nexaflow.core.execution.WorkflowRunContext
import com.nexaflow.domain.models.GlobalVariable
import com.nexaflow.domain.repositories.VariableRepository
import com.nexaflow.domain.variables.RuntimeValue
import com.nexaflow.domain.variables.RuntimeVariable
import com.nexaflow.domain.variables.VariableScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopedDataRuntimeTest {

    private class MemoryVariables : VariableRepository {
        private val values = LinkedHashMap<String, GlobalVariable>()

        override fun getVariables(): Flow<List<GlobalVariable>> = flowOf(values.values.toList())
        override fun getVariablesPaging(): PagingSource<Int, GlobalVariable> = error("Paging is not used by this test")
        override suspend fun getVariablesOnce(): List<GlobalVariable> = values.values.toList()
        override suspend fun saveVariable(variable: GlobalVariable) {
            values[variable.id] = variable
        }
        override suspend fun deleteVariable(id: String) {
            values.remove(id)
        }
    }

    private fun variable(
        name: String,
        value: RuntimeValue,
        scope: VariableScope,
        version: Long = 1L
    ) = RuntimeVariable(name, value, scope, version)

    @Test
    fun `scopes resolve from action through global without upward leakage`() = runBlocking {
        val globals = MemoryVariables()
        val context = WorkflowRunContext("run", "workflow", 1L)
        val runtime = ScopedDataRuntime(
            runContext = context,
            globalVariables = globals,
            workflowVariables = listOf(variable("mode", RuntimeValue.StringValue("workflow"), VariableScope.WORKFLOW))
        )
        runtime.set(variable("mode", RuntimeValue.StringValue("global"), VariableScope.GLOBAL), 2L)
        runtime.set(variable("mode", RuntimeValue.StringValue("execution"), VariableScope.EXECUTION), 3L)
        runtime.set(variable("mode", RuntimeValue.StringValue("node"), VariableScope.NODE), 4L, nodeId = "node-a")
        runtime.set(variable("mode", RuntimeValue.StringValue("action"), VariableScope.ACTION), 5L, actionId = "action-a")

        assertEquals("action", (runtime.resolve("mode", "node-a", "action-a")!!.value as RuntimeValue.StringValue).value)
        assertEquals("node", (runtime.resolve("mode", "node-a")!!.value as RuntimeValue.StringValue).value)
        assertEquals("execution", (runtime.resolve("mode")!!.value as RuntimeValue.StringValue).value)
        assertEquals("execution", context.get("$.nexaflowRuntimeVars.mode"))
        assertEquals("execution", (runtime.resolve("mode", "other")!!.value as RuntimeValue.StringValue).value)
    }

    @Test
    fun `delete acts only on requested scope and synchronizes execution context`() = runBlocking {
        val runtime = ScopedDataRuntime(
            WorkflowRunContext("run", "workflow", 1L),
            MemoryVariables(),
            listOf(variable("enabled", RuntimeValue.BooleanValue(false), VariableScope.WORKFLOW))
        )
        runtime.set(variable("enabled", RuntimeValue.BooleanValue(true), VariableScope.EXECUTION), 2L)
        assertTrue(runtime.delete(VariableScope.EXECUTION, "enabled"))
        assertFalse(runtime.delete(VariableScope.EXECUTION, "enabled"))
        assertEquals(false, (runtime.resolve("enabled")!!.value as RuntimeValue.BooleanValue).value)
    }

    @Test
    fun `global typed values round trip through existing repository snapshot`() = runBlocking {
        val globals = MemoryVariables()
        val runtime = ScopedDataRuntime(WorkflowRunContext("run", "workflow", 1L), globals)
        val written = runtime.set(
            variable(
                "threshold",
                RuntimeValue.ObjectValue(
                    mapOf("minimum" to RuntimeValue.IntValue(85), "wifi" to RuntimeValue.BooleanValue(true))
                ),
                VariableScope.GLOBAL
            ),
            updatedAt = 100L
        )
        assertEquals(1L, written.version)
        val snapshot = runtime.snapshot(VariableScope.GLOBAL, 200L)
        assertEquals(1, snapshot.variables.size)
        assertTrue(snapshot.variables.single().value is RuntimeValue.ObjectValue)

        assertTrue(runtime.delete(VariableScope.GLOBAL, "threshold"))
        assertEquals(listOf("threshold"), runtime.restore(snapshot, 300L))
        val restored = runtime.resolve("threshold")!!.value as RuntimeValue.ObjectValue
        assertEquals(85, (restored.values.getValue("minimum") as RuntimeValue.IntValue).value)
    }
}
