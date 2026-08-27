package com.nexaflow.domain.usecases

import com.nexaflow.domain.models.ExecutionHistoryOutcome
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import androidx.paging.PagingSource
import javax.inject.Inject

class GetExecutionPagingUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    operator fun invoke(
        automationId: String? = null,
        outcome: ExecutionHistoryOutcome? = null
    ): PagingSource<Int, ExecutionRecord> =
        if (automationId.isNullOrBlank() && outcome == null) {
            repository.getExecutionPaging()
        } else {
            repository.getExecutionPaging(automationId, outcome)
        }
}
