package com.nexaflow.core.execution.variables

import com.nexaflow.core.execution.WorkflowRunContext
import com.nexaflow.domain.repositories.VariableRepository
import com.nexaflow.domain.variables.RuntimeValue
import com.nexaflow.domain.variables.RuntimeVariable
import com.nexaflow.domain.variables.VariableScope
import com.nexaflow.domain.variables.VariableSnapshot

/**
 * Typed runtime scopes layered over the existing [WorkflowRunContext] and the
 * established [VariableRepository]. Only GLOBAL is persisted here; workflow,
 * execution, node and action values live for one run and cannot leak upward
 * unless a caller explicitly writes GLOBAL through the repository contract.
 */
class ScopedDataRuntime(
    private val runContext: WorkflowRunContext,
    private val globalVariables: VariableRepository,
    workflowVariables: Collection<RuntimeVariable> = emptyList()
) {
    private val workflow = variablesFor(VariableScope.WORKFLOW, workflowVariables)
    private val execution = LinkedHashMap<String, RuntimeVariable>()
    private val node = LinkedHashMap<String, LinkedHashMap<String, RuntimeVariable>>()
    private val action = LinkedHashMap<String, LinkedHashMap<String, RuntimeVariable>>()

    init {
        require(workflowVariables.none { it.sensitive }) {
            "Sensitive values must remain GLOBAL until CredentialVault references are available"
        }
    }

    /**
     * Lookup precedence is ACTION → NODE → EXECUTION → WORKFLOW → GLOBAL.
     * `actionId`/`nodeId` restrict visibility to their own lexical scope.
     */
    suspend fun resolve(
        name: String,
        nodeId: String? = null,
        actionId: String? = null
    ): RuntimeVariable? {
        validateName(name)
        val key = canonical(name)
        actionId?.let { action[it]?.get(key)?.let { value -> return value } }
        nodeId?.let { node[it]?.get(key)?.let { value -> return value } }
        execution[key]?.let { return it }
        workflow[key]?.let { return it }
        return globalVariables.resolve(name)
    }

    /** Writes one value in the exact scope declared by [variable]. */
    suspend fun set(
        variable: RuntimeVariable,
        updatedAt: Long,
        nodeId: String? = null,
        actionId: String? = null
    ): RuntimeVariable = when (variable.scope) {
        VariableScope.GLOBAL -> globalVariables.set(variable, updatedAt)
        VariableScope.WORKFLOW -> put(workflow, variable)
        VariableScope.EXECUTION -> put(execution, variable).also { syncExecutionValue(it) }
        VariableScope.NODE -> {
            require(!nodeId.isNullOrBlank()) { "NODE scope requires nodeId" }
            put(node.getOrPut(nodeId) { LinkedHashMap() }, variable)
        }
        VariableScope.ACTION -> {
            require(!actionId.isNullOrBlank()) { "ACTION scope requires actionId" }
            put(action.getOrPut(actionId) { LinkedHashMap() }, variable)
        }
    }

    /** Deletes only from the explicitly requested scope. */
    suspend fun delete(
        scope: VariableScope,
        name: String,
        nodeId: String? = null,
        actionId: String? = null
    ): Boolean {
        validateName(name)
        val key = canonical(name)
        return when (scope) {
            VariableScope.GLOBAL -> globalVariables.delete(name)
            VariableScope.WORKFLOW -> workflow.remove(key) != null
            VariableScope.EXECUTION -> execution.remove(key)?.also { syncExecutionRemoval(it.name) } != null
            VariableScope.NODE -> {
                require(!nodeId.isNullOrBlank()) { "NODE scope requires nodeId" }
                node[nodeId]?.remove(key) != null
            }
            VariableScope.ACTION -> {
                require(!actionId.isNullOrBlank()) { "ACTION scope requires actionId" }
                action[actionId]?.remove(key) != null
            }
        }
    }

    /** Snapshot of a single scope; global snapshots delegate to the repository. */
    suspend fun snapshot(
        scope: VariableScope,
        capturedAt: Long,
        nodeId: String? = null,
        actionId: String? = null
    ): VariableSnapshot = when (scope) {
        VariableScope.GLOBAL -> globalVariables.snapshot(capturedAt)
        VariableScope.WORKFLOW -> snapshotOf(scope, workflow, capturedAt)
        VariableScope.EXECUTION -> snapshotOf(scope, execution, capturedAt)
        VariableScope.NODE -> {
            require(!nodeId.isNullOrBlank()) { "NODE scope requires nodeId" }
            snapshotOf(scope, node[nodeId].orEmpty(), capturedAt)
        }
        VariableScope.ACTION -> {
            require(!actionId.isNullOrBlank()) { "ACTION scope requires actionId" }
            snapshotOf(scope, action[actionId].orEmpty(), capturedAt)
        }
    }

    /** Restores a snapshot only into its declared scope. */
    suspend fun restore(
        snapshot: VariableSnapshot,
        updatedAt: Long,
        nodeId: String? = null,
        actionId: String? = null
    ): List<String> = when (snapshot.scope) {
        VariableScope.GLOBAL -> globalVariables.restore(snapshot, updatedAt)
        VariableScope.WORKFLOW -> restoreLocal(workflow, snapshot)
        VariableScope.EXECUTION -> restoreLocal(execution, snapshot).also {
            execution.values.forEach(::syncExecutionValue)
        }
        VariableScope.NODE -> {
            require(!nodeId.isNullOrBlank()) { "NODE scope requires nodeId" }
            restoreLocal(node.getOrPut(nodeId) { LinkedHashMap() }, snapshot)
        }
        VariableScope.ACTION -> {
            require(!actionId.isNullOrBlank()) { "ACTION scope requires actionId" }
            restoreLocal(action.getOrPut(actionId) { LinkedHashMap() }, snapshot)
        }
    }

    private fun restoreLocal(
        target: LinkedHashMap<String, RuntimeVariable>,
        snapshot: VariableSnapshot
    ): List<String> {
        val restored = mutableListOf<String>()
        snapshot.variables.forEach { variable ->
            require(!variable.sensitive) {
                "Sensitive non-global values are not restorable before CredentialVault references"
            }
            target[canonical(variable.name)] = variable
            restored += variable.name
        }
        return restored
    }

    private fun put(
        target: LinkedHashMap<String, RuntimeVariable>,
        variable: RuntimeVariable
    ): RuntimeVariable {
        require(!variable.sensitive) {
            "Sensitive values must use GLOBAL scope until CredentialVault references are available"
        }
        target[canonical(variable.name)] = variable
        return variable
    }

    private fun syncExecutionValue(variable: RuntimeVariable) {
        runContext.put("$INTERNAL_EXECUTION_PATH.${variable.name}", variable.value.toContextValue())
    }

    private fun syncExecutionRemoval(name: String) {
        // WorkflowRunContext uses JSON Merge Patch replacement rather than a
        // delete token. A null retains the precise information that the scoped
        // value no longer resolves and keeps context writes transactional.
        runContext.put("$INTERNAL_EXECUTION_PATH.$name", null)
    }

    private fun snapshotOf(
        scope: VariableScope,
        values: Map<String, RuntimeVariable>,
        capturedAt: Long
    ): VariableSnapshot = VariableSnapshot(
        scope = scope,
        variables = values.values.sortedBy { it.name.lowercase() },
        capturedAt = capturedAt
    )

    private fun variablesFor(
        expectedScope: VariableScope,
        values: Collection<RuntimeVariable>
    ): LinkedHashMap<String, RuntimeVariable> {
        val target = LinkedHashMap<String, RuntimeVariable>()
        values.forEach { variable ->
            require(variable.scope == expectedScope) {
                "Expected $expectedScope variable but received ${variable.scope}"
            }
            put(target, variable)
        }
        return target
    }

    private fun validateName(name: String) {
        require(RuntimeVariable.VARIABLE_NAME.matches(name)) { "Invalid variable name: $name" }
    }

    private fun canonical(name: String): String = name.lowercase()

    private fun RuntimeValue.toContextValue(): Any? = when (this) {
        RuntimeValue.NullValue -> null
        is RuntimeValue.StringValue -> value
        is RuntimeValue.BooleanValue -> value
        is RuntimeValue.IntValue -> value
        is RuntimeValue.LongValue -> value
        is RuntimeValue.DoubleValue -> value
        is RuntimeValue.ListValue -> values.map { item -> item.toContextValue() }
        is RuntimeValue.ObjectValue -> values.mapValues { (_, item) -> item.toContextValue() }
    }

    private companion object {
        const val INTERNAL_EXECUTION_PATH = "$.nexaflowRuntimeVars"
    }
}
