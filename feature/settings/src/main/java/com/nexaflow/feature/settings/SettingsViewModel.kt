package com.nexaflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.data.backup.BackupManager
import com.nexaflow.data.backup.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupManager: BackupManager
) : ViewModel() {

    private val _importResult = MutableSharedFlow<ImportResult>()
    val importResult: SharedFlow<ImportResult> = _importResult.asSharedFlow()

    /** Returns the pretty-printed JSON backup, or null on failure. */
    fun exportBackup(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val json = runCatching {
                backupManager.toJson(backupManager.export())
            }.getOrNull()
            onResult(json)
        }
    }

    fun importBackup(json: String) {
        viewModelScope.launch {
            _importResult.emit(backupManager.import(json))
        }
    }
}
