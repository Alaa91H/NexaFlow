package com.nexaflow.domain.usecases

import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import javax.inject.Inject

class GetExecutionByIdUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    suspend operator fun invoke(id: String): ExecutionRecord? = repository.getExecutionById(id)
}
