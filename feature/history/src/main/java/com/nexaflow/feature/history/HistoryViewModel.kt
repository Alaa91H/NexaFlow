package com.nexaflow.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.usecases.GetExecutionHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    getExecutionHistory: GetExecutionHistoryUseCase
) : ViewModel() {

    val history: StateFlow<List<ExecutionRecord>> = getExecutionHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
