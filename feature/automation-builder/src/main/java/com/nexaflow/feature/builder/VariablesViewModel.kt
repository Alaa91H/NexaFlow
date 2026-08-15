package com.nexaflow.feature.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.nexaflow.domain.models.GlobalVariable
import com.nexaflow.domain.repositories.VariableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Manages the Tasker-style global variables. Names are stored WITHOUT the
 * leading `%`; the engine and the editor reference them as `%NAME`.
 */
@HiltViewModel
class VariablesViewModel @Inject constructor(
    private val repository: VariableRepository
) : ViewModel() {

    // Paged stream: variables can grow unbounded, so the screen loads them
    // page by page instead of materializing the whole table.
    val variables: Flow<androidx.paging.PagingData<GlobalVariable>> = Pager(
        config = PagingConfig(pageSize = 30, initialLoadSize = 60, enablePlaceholders = false)
    ) {
        repository.getVariablesPaging()
    }.flow.cachedIn(viewModelScope)

    /** Adds a new variable, generating a fresh id. */
    fun add(name: String, value: String, sensitive: Boolean = false) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || !NAME_REGEX.matches(trimmed)) return
        viewModelScope.launch {
            repository.saveVariable(
                GlobalVariable(
                    id = UUID.randomUUID().toString(),
                    name = trimmed,
                    value = value,
                    updatedAt = System.currentTimeMillis(),
                    sensitive = sensitive
                )
            )
        }
    }

    /** Updates an existing variable, keeping its id. */
    fun update(id: String, name: String, value: String, sensitive: Boolean = false) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || !NAME_REGEX.matches(trimmed)) return
        viewModelScope.launch {
            repository.saveVariable(
                GlobalVariable(
                    id = id,
                    name = trimmed,
                    value = value,
                    updatedAt = System.currentTimeMillis(),
                    sensitive = sensitive
                )
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteVariable(id) }
    }

    private companion object {
        val NAME_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
