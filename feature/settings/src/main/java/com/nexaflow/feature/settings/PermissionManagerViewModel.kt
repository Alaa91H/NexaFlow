package com.nexaflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.execution.capability.PrivilegeStateStore
import com.nexaflow.domain.capability.PrivilegeSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Settings-facing projection of the single process-wide privilege store.
 * It never grants permissions itself; actions still go through the existing
 * reviewed grant flows and this model only requests verified read-back.
 */
@HiltViewModel
class PermissionManagerViewModel @Inject constructor(
    private val privilegeStateStore: PrivilegeStateStore
) : ViewModel() {
    val privilegeSnapshot: StateFlow<PrivilegeSnapshot> = privilegeStateStore.snapshot

    fun refreshPrivileges() {
        privilegeStateStore.invalidate()
    }

    fun refreshPrivilegesAsync() {
        viewModelScope.launch {
            privilegeStateStore.freshSnapshot()
        }
    }
}
