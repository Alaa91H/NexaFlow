package com.nexaflow.domain.repositories

import com.nexaflow.domain.models.GlobalVariable
import kotlinx.coroutines.flow.Flow

interface VariableRepository {
    fun getVariables(): Flow<List<GlobalVariable>>
    suspend fun getVariablesOnce(): List<GlobalVariable>
    suspend fun saveVariable(variable: GlobalVariable)
    suspend fun deleteVariable(id: String)
}
