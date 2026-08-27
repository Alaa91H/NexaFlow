package com.nexaflow.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.usecases.GetExecutionPagingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    getExecutionPaging: GetExecutionPagingUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val routineId: String? = savedStateHandle.get<String>("automationId")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    /** True only when this destination was opened from one routine's health card. */
    val isRoutineHistory: Boolean = routineId != null

    private val _showFailuresOnly = MutableStateFlow(
        savedStateHandle.get<String>("outcome")?.equals(FAILED_OUTCOME, ignoreCase = true) == true
    )
    val showFailuresOnly = _showFailuresOnly.asStateFlow()

    /**
     * Pageable history. The table is capped at 1000 rows, so materializing the
     * whole list on every DB change is wasteful; Paging streams [PAGE_SIZE]
     * rows at a time and is cached for the ViewModel's lifetime.
     */
    val pagingData: Flow<PagingData<ExecutionRecord>> = showFailuresOnly
        .map { failuresOnly ->
            Pager(
                config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
                pagingSourceFactory = {
                    getExecutionPaging(routineId, success = if (failuresOnly) false else null)
                }
            ).flow
        }
        .flatMapLatest { it }
        .cachedIn(viewModelScope)

    fun setShowFailuresOnly(enabled: Boolean) {
        _showFailuresOnly.value = enabled
    }

    private companion object {
        const val FAILED_OUTCOME = "failed"
        const val PAGE_SIZE = 30
    }
}
