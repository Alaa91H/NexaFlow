package com.nexaflow.domain.usecases

import com.nexaflow.domain.repositories.HistoryRepository
import javax.inject.Inject

class GetExecutionHistoryUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    operator fun invoke() = repository.getExecutionHistory()
}
