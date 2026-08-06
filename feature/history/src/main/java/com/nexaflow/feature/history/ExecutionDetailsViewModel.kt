package com.nexaflow.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.usecases.GetExecutionByIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Screen state: [loading] distinguishes a pending load from a genuinely missing record. */
data class ExecutionDetailsUiState(
    val loading: Boolean = false,
    val record: ExecutionRecord? = null
)

@HiltViewModel
class ExecutionDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getExecutionById: GetExecutionByIdUseCase
) : ViewModel() {

    private val recordId: String = checkNotNull(savedStateHandle["recordId"])

    private val _uiState = MutableStateFlow(ExecutionDetailsUiState(loading = true))
    val uiState: StateFlow<ExecutionDetailsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = ExecutionDetailsUiState(
                loading = false,
                record = getExecutionById(recordId)
            )
        }
    }
}
