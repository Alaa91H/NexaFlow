package com.nexaflow.data.repository

import com.nexaflow.core.database.GlobalVariableEntity
import com.nexaflow.core.database.VariableDao
import com.nexaflow.domain.models.GlobalVariable
import com.nexaflow.domain.repositories.VariableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class VariableRepositoryImpl @Inject constructor(
    private val variableDao: VariableDao
) : VariableRepository {

    override fun getVariables(): Flow<List<GlobalVariable>> {
        return variableDao.getAllVariables().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getVariablesOnce(): List<GlobalVariable> {
        return variableDao.getVariablesOnce().map { it.toDomain() }
    }

    override suspend fun saveVariable(variable: GlobalVariable) {
        variableDao.upsert(variable.toEntity())
    }

    override suspend fun deleteVariable(id: String) {
        variableDao.deleteById(id)
    }
}

private fun GlobalVariableEntity.toDomain(): GlobalVariable = GlobalVariable(
    id = id,
    name = name,
    value = value,
    updatedAt = updatedAt
)

private fun GlobalVariable.toEntity(): GlobalVariableEntity = GlobalVariableEntity(
    id = id,
    name = name,
    value = value,
    updatedAt = updatedAt
)
