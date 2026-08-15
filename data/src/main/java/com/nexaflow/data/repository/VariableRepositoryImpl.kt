package com.nexaflow.data.repository

import androidx.paging.PagingSource
import com.nexaflow.core.database.GlobalVariableEntity
import com.nexaflow.core.database.VariableDao
import com.nexaflow.core.security.SecureStorage
import com.nexaflow.data.paging.MappedPagingSource
import com.nexaflow.domain.models.GlobalVariable
import com.nexaflow.domain.repositories.VariableRepository
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

private suspend fun GlobalVariableEntity.toDomain(secureStorage: SecureStorage): GlobalVariable =
    GlobalVariable(
        id = id,
        name = name,
        // Sensitive values are decrypted on read; a missing key (reinstall
        // restored the DB but Keystore keys are gone) degrades to blank rather
        // than crashing the engine.
        value = if (sensitive) {
            runCatching { secureStorage.get(SECRET_PREFIX + id) }.getOrNull() ?: ""
        } else {
            value
        },
        updatedAt = updatedAt,
        sensitive = sensitive
    )

private suspend fun GlobalVariable.toEntity(secureStorage: SecureStorage): GlobalVariableEntity =
    GlobalVariableEntity(
        id = id,
        name = name,
        value = if (sensitive) {
            secureStorage.put(SECRET_PREFIX + id, value)
            // Ciphertext marker; the real secret lives only in SecureStorage.
            SECRET_MARKER
        } else {
            value
        },
        updatedAt = updatedAt,
        sensitive = sensitive
    )

private const val SECRET_MARKER = "*encrypted*"
private const val SECRET_PREFIX = "variable_"

