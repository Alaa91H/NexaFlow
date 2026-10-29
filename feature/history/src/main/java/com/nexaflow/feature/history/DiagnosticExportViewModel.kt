package com.nexaflow.feature.history

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.logging.LogStore
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Builds the local diagnostic bundle (NFA-09) and writes it to the app cache.
 *
 * Kept separate from [DiagnosticsViewModel] so the findings projection stays a
 * pure, device-free unit test while this device-facing part only assembles the
 * already-redacted snapshots.
 */
@HiltViewModel
class DiagnosticExportViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val automationRepository: AutomationRepository,
    private val logStore: LogStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /**
     * Writes the sanitized bundle and hands the file back for sharing. All
     * reads are one-shot snapshots; AI credentials, webhook URLs and agent
     * tokens never reach the history or log stores unredacted, and every
     * free-text field passes [DiagnosticBundle]'s redaction once more.
     */
    fun export(onResult: (File?) -> Unit) {
        viewModelScope.launch {
            val file = runCatching {
                val executions = historyRepository.getExecutionHistory().first()
                val json = DiagnosticBundle.build(
                    generatedAtMs = System.currentTimeMillis(),
                    build = buildInfo(),
                    channel = executions.firstOrNull { it.channel != null }?.channel,
                    automations = automationRepository.getAutomations().first(),
                    executions = executions,
                    timeline = logStore.timeline().first(),
                    errors = logStore.errors().first()
                )
                val directory = File(context.cacheDir, DIAGNOSTICS_DIRECTORY).apply { mkdirs() }
                File(directory, "nexaflow-diagnostics-${System.currentTimeMillis()}.json")
                    .apply { writeText(json) }
            }.getOrNull()
            onResult(file)
        }
    }

    private fun buildInfo(): DiagnosticBuildInfo {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return DiagnosticBuildInfo(
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            locale = Locale.getDefault().toLanguageTag()
        )
    }

    companion object {
        /** Cache subdirectory registered in the app FileProvider paths. */
        const val DIAGNOSTICS_DIRECTORY = "diagnostics"
    }
}
