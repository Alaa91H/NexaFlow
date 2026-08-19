package com.nexaflow.data.repository

import androidx.paging.PagingSource
import com.nexaflow.core.database.GlobalVariableEntity
import com.nexaflow.core.database.VariableDao
import com.nexaflow.core.security.SecureStorage
import com.nexaflow.data.paging.MappedPagingSource
import com.nexaflow.domain.models.GlobalVariable
import com.nexaflow.domain.repositories.VariableRepository
import com.nexaflow.domain.variables.RuntimeValueCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Variable persistence with at-rest encryption (P0-4): variables flagged
 * [GlobalVariable.sensitive] are stored as ciphertext (Keystore AES-GCM via
 * [SecureStorage]) and decrypted transparently on read. The database never
 * contains the plaintext secret; the UI always shows the decrypted value.
 */
class VariableRepositoryImpl @Inject constructor(
    private val variableDao: VariableDao,
    private val secureStorage: SecureStorage
) : VariableRepository {

    override fun getVariables(): Flow<List<GlobalVariable>> {
        return variableDao.getAllVariables().map { entities ->
            val result = ArrayList<GlobalVariable>(entities.size)
            for (entity in entities) {
                result.add(entity.toDomain(secureStorage))
            }
            result
        }
    }

    override fun getVariablesPaging(): PagingSource<Int, GlobalVariable> {
        // Sensitive values decrypt lazily per page inside the mapping; the
        // page never materializes the whole table.
        return MappedPagingSource(variableDao.getAllVariablesPaged()) { entity ->
            entity.toDomain(secureStorage)
        }
    }

    override suspend fun getVariablesOnce(): List<GlobalVariable> {
        val result = ArrayList<GlobalVariable>()
        for (entity in variableDao.getVariablesOnce()) {
            result.add(entity.toDomain(secureStorage))
        }
        return result
    }

    override suspend fun saveVariable(variable: GlobalVariable) {
        variableDao.upsert(variable.toEntity(secureStorage))
    }

    override suspend fun deleteVariable(id: String) {
        variableDao.deleteById(id)
        // Best effort: drop the ciphertext too so the secret is fully gone.
        secureStorage.remove(SECRET_PREFIX + id)
    }

    private companion object {
        const val SECRET_PREFIX = "variable_"
    }
}

private suspend fun GlobalVariableEntity.toDomain(secureStorage: SecureStorage): GlobalVariable {
    // A sensitive typed value stores its typed JSON only in SecureStorage; the
    // Room row retains a marker in both display/serialized columns. A legacy
    // sensitive value has no serialized marker and remains plain text after
    // decryption, preserving older `%NAME` behavior.
    val secret = if (sensitive) {
        runCatching { secureStorage.get(SECRET_PREFIX + id) }.getOrNull()
    } else {
        null
    }
    val typedSerialized = when {
        sensitive && serializedValue != null -> secret
        else -> serializedValue
    }
    val displayValue = when {
        typedSerialized != null -> RuntimeValueCodec.display(
            RuntimeValueCodec.decodeOrLegacyText(typedSerialized)
        )
        sensitive -> secret.orEmpty()
        else -> value
    }
    return GlobalVariable(
        id = id,
        name = name,
        value = displayValue,
        updatedAt = updatedAt,
        version = version,
        serializedValue = typedSerialized,
        sensitive = sensitive
    )
}

private suspend fun GlobalVariable.toEntity(secureStorage: SecureStorage): GlobalVariableEntity {
    val rawForStorage = serializedValue ?: value
    val isTyped = serializedValue != null
    return GlobalVariableEntity(
        id = id,
        name = name,
        value = if (sensitive) {
            secureStorage.put(SECRET_PREFIX + id, rawForStorage)
            // Ciphertext marker; the real value remains only in SecureStorage.
            SECRET_MARKER
        } else {
            value
        },
        updatedAt = updatedAt,
        version = version,
        serializedValue = if (sensitive && isTyped) SECRET_MARKER else serializedValue,
        sensitive = sensitive
    )
}

private const val SECRET_MARKER = "*encrypted*"
private const val SECRET_PREFIX = "variable_"

