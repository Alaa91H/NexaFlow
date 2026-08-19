package com.nexaflow.domain.repositories

import androidx.paging.PagingSource
import com.nexaflow.domain.models.GlobalVariable
import com.nexaflow.domain.variables.RuntimeValue
import com.nexaflow.domain.variables.RuntimeValueCodec
import com.nexaflow.domain.variables.RuntimeVariable
import com.nexaflow.domain.variables.VariableScope
import com.nexaflow.domain.variables.VariableSnapshot
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for persisted user globals. Typed values are serialized
 * in the existing global-variable record; workflow/execution/node/action scopes
 * are intentionally runtime-local and are layered above this repository.
 */
interface VariableRepository {
    fun getVariables(): Flow<List<GlobalVariable>>
    fun getVariablesPaging(): PagingSource<Int, GlobalVariable>
    suspend fun getVariablesOnce(): List<GlobalVariable>
    suspend fun saveVariable(variable: GlobalVariable)
    suspend fun deleteVariable(id: String)

    /** Case-insensitive global lookup compatible with `%NAME` substitution. */
    suspend fun get(name: String): GlobalVariable? =
        getVariablesOnce().firstOrNull { it.name.equals(name, ignoreCase = true) }

    suspend fun exists(name: String): Boolean = get(name) != null

    /** Resolves a persisted global into a closed typed value algebra. */
    suspend fun resolve(name: String): RuntimeVariable? = get(name)?.let { variable ->
        RuntimeVariable(
            name = variable.name,
            value = variable.serializedValue
                ?.let(RuntimeValueCodec::decodeOrLegacyText)
                ?: RuntimeValue.StringValue(variable.value),
            scope = VariableScope.GLOBAL,
            version = variable.version,
            sensitive = variable.sensitive
        )
    }

    /**
     * Persists one typed global using the existing entity/repository path. The
     * revision advances from the currently persisted value, preventing a stale
     * caller from accidentally reusing a revision number.
     */
    suspend fun set(variable: RuntimeVariable, updatedAt: Long): RuntimeVariable {
        require(variable.scope == VariableScope.GLOBAL) {
            "VariableRepository persists GLOBAL scope only; use ScopedDataRuntime for ${variable.scope}"
        }
        val existing = get(variable.name)
        val persisted = GlobalVariable(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = variable.name,
            value = RuntimeValueCodec.display(variable.value),
            updatedAt = updatedAt,
            version = (existing?.version ?: 0L) + 1L,
            serializedValue = RuntimeValueCodec.encode(variable.value),
            sensitive = variable.sensitive
        )
        saveVariable(persisted)
        return variable.copy(version = persisted.version)
    }

    /** Deletes a global by logical name and reports whether it was present. */
    suspend fun delete(name: String): Boolean {
        val existing = get(name) ?: return false
        deleteVariable(existing.id)
        return true
    }

    /** Creates a deterministic global-only checkpoint; sensitive values stay encrypted at rest. */
    suspend fun snapshot(capturedAt: Long): VariableSnapshot = VariableSnapshot(
        scope = VariableScope.GLOBAL,
        variables = getVariablesOnce()
            .sortedBy { it.name.lowercase() }
            .map { variable ->
                RuntimeVariable(
                    name = variable.name,
                    value = variable.serializedValue
                        ?.let(RuntimeValueCodec::decodeOrLegacyText)
                        ?: RuntimeValue.StringValue(variable.value),
                    scope = VariableScope.GLOBAL,
                    version = variable.version,
                    sensitive = variable.sensitive
                )
            },
        capturedAt = capturedAt
    )

    /**
     * Restores a global checkpoint non-destructively: a row with a newer local
     * revision wins, so replaying an old snapshot cannot overwrite a later edit.
     * Returns the logical names actually restored.
     */
    suspend fun restore(snapshot: VariableSnapshot, updatedAt: Long): List<String> {
        require(snapshot.scope == VariableScope.GLOBAL) {
            "VariableRepository restores GLOBAL snapshots only"
        }
        val restored = mutableListOf<String>()
        snapshot.variables.forEach { variable ->
            val existing = get(variable.name)
            if (existing == null || variable.version >= existing.version) {
                saveVariable(
                    GlobalVariable(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        name = variable.name,
                        value = RuntimeValueCodec.display(variable.value),
                        updatedAt = updatedAt,
                        version = variable.version,
                        serializedValue = RuntimeValueCodec.encode(variable.value),
                        sensitive = variable.sensitive
                    )
                )
                restored += variable.name
            }
        }
        return restored
    }
}
