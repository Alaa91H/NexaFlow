package com.nexaflow.domain.usecases

import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import androidx.paging.PagingSource
import javax.inject.Inject

class GetExecutionPagingUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    operator fun invoke(): PagingSource<Int, ExecutionRecord> = repository.getExecutionPaging()
}
