package com.nexaflow.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state for the in-app update checker. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Latest(val info: UpdateInfo) : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data object Downloading : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloadedApk: java.io.File? = null

    /**
     * Starts an update check only after an explicit user action. The ViewModel
     * deliberately has no init-time check, so newly installed apps keep update
     * checking disabled by default and never contact GitHub until this method
     * is invoked from the Settings screen.
     */
    fun check() {
        _state.value = UpdateUiState.Checking
        viewModelScope.launch {
            // Blocking HttpURLConnection calls must never touch the main thread
            // (ANR risk); the GitHub API round-trip can take up to 15 s.
            val info = withContext(Dispatchers.IO) {
                runCatching {
                    UpdateChecker.fetchLatestJson()?.let { UpdateChecker.parseRelease(it) }
                }.getOrNull()
            }
            _state.value = if (info == null) {
                UpdateUiState.Error("update_check_failed")
            } else if (info.version.removePrefix("v") == currentVersion()) {
                UpdateUiState.Latest(info)
            } else {
                UpdateUiState.Available(info)
            }
        }
    }

    /** Downloads (and verifies) the APK, then hands it to the system installer. */
    fun downloadAndInstall() {
        val state = _state.value
        val info = (state as? UpdateUiState.Available)?.info ?: return
        if (!info.canInstall) {
            _state.value = UpdateUiState.Error("update_no_apk")
            return
        }
        _state.value = UpdateUiState.Downloading
        viewModelScope.launch {
            // The APK download (multi-MB over HTTP) must run off the main thread.
            val apk = withContext(Dispatchers.IO) {
                UpdateChecker.downloadAndVerify(
                    getApplication(),
                    info.apkUrl!!,
                    info.sha256
                )
            }
            if (apk == null) {
                _state.value = UpdateUiState.Error("update_download_failed")
                return@launch
            }
            downloadedApk = apk
            val started = UpdateChecker.install(getApplication(), apk)
            _state.value = if (started) {
                UpdateUiState.Idle
            } else {
                UpdateUiState.Error("update_install_failed")
            }
        }
    }

    private fun currentVersion(): String = runCatching {
        getApplication<Application>().packageManager
            .getPackageInfo(getApplication<Application>().packageName, 0)
            .versionName
            .orEmpty()
    }.getOrDefault("")
}
