package com.nexaflow.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.nexaflow.domain.models.ExecutionHistoryOutcome
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

    /** Null means all recorded runs; a non-null value is filtered by Room Paging. */
    private val _selectedOutcome = MutableStateFlow(
        ExecutionHistoryOutcome.fromRoute(savedStateHandle.get<String>("outcome"))
    )
    val selectedOutcome = _selectedOutcome.asStateFlow()

    /**
     * Pageable history. The table is capped at 1000 rows, so materializing the
     * whole list on every DB change is wasteful; Paging streams [PAGE_SIZE]
     * rows at a time and is cached for the ViewModel's lifetime.
     */
    val pagingData: Flow<PagingData<ExecutionRecord>> = selectedOutcome
        .map { outcome ->
            Pager(
                config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
                pagingSourceFactory = { getExecutionPaging(routineId, outcome) }
            ).flow
        }
        .flatMapLatest { it }
        .cachedIn(viewModelScope)

    fun setSelectedOutcome(outcome: ExecutionHistoryOutcome?) {
        _selectedOutcome.value = outcome
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
