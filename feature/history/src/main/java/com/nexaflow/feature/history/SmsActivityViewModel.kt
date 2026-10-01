package com.nexaflow.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.SmsActivityEvent
import com.nexaflow.domain.repositories.SmsActivityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SmsActivityViewModel @Inject constructor(repository: SmsActivityRepository) : ViewModel() {
    val events = repository.observeLatest().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<SmsActivityEvent>())
    private val activityRepository = repository

    fun clear() {
        viewModelScope.launch { activityRepository.clear() }
    }
}
