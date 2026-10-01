package com.nexaflow.core.execution

import android.content.Context
import com.nexaflow.core.common.EpochMillis
import com.nexaflow.core.execution.variables.BuiltinVariables
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.repositories.VariableRepository
import com.nexaflow.domain.variables.RuntimeValueCodec
import com.nexaflow.domain.variables.VariableResolver

/** Resolves durable/global variables and per-run context selectors. */
internal class ExecutionValueResolver(
    private val context: Context,
    private val variableRepository: VariableRepository?,
    private val epochMillis: EpochMillis,
) {
    private val opaqueConfigKeys = setOf("bundleJson", "action_buttons")

    suspend fun snapshot(): Map<String, String> {
        val builtins = runCatching { BuiltinVariables.provide(context) }.getOrDefault(emptyMap())
        val globals = runCatching {
            variableRepository?.snapshot(epochMillis.now())?.variables.orEmpty()
        }.getOrDefault(emptyList())
        if (globals.isEmpty()) return builtins
        return builtins + globals.associate { it.name to RuntimeValueCodec.display(it.value) }
    }

    fun resolve(
        action: Action,
        variables: Map<String, String>,
        runContext: WorkflowRunContext? = null,
    ): Action {
        val globalsResolved = if (variables.isEmpty()) {
            action
        } else {
            action.copy(
                config = action.config.mapValues { (key, value) ->
                    if (key in opaqueConfigKeys) value
                    else VariableResolver.resolve(value, variables)
                }
            )
        }
        return runContext?.let { context ->
            globalsResolved.copy(
                config = globalsResolved.config.mapValues { (key, value) ->
                    if (key in opaqueConfigKeys) value
                    else ContextVariableResolver.resolve(value, context)
                }
            )
        } ?: globalsResolved
    }
}
