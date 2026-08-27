package com.nexaflow.domain.usecases

import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import androidx.paging.PagingSource
import javax.inject.Inject

class GetExecutionPagingUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    operator fun invoke(
        automationId: String? = null,
        success: Boolean? = null
    ): PagingSource<Int, ExecutionRecord> =
        if (automationId.isNullOrBlank() && success == null) {
            repository.getExecutionPaging()
        } else {
            repository.getExecutionPaging(automationId, success)
        }
}
