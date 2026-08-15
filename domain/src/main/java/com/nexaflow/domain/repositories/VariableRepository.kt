package com.nexaflow.domain.repositories

import androidx.paging.PagingSource
import com.nexaflow.domain.models.GlobalVariable
import kotlinx.coroutines.flow.Flow

interface VariableRepository {
    fun getVariables(): Flow<List<GlobalVariable>>
    fun getVariablesPaging(): PagingSource<Int, GlobalVariable>
    suspend fun getVariablesOnce(): List<GlobalVariable>
    suspend fun saveVariable(variable: GlobalVariable)
    suspend fun deleteVariable(id: String)
}
