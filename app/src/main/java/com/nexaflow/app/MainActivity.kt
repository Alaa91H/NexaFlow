package com.nexaflow.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.nexaflow.app.ui.theme.NexaFlowTheme
import com.nexaflow.core.datastore.ThemeMode
import com.nexaflow.core.datastore.ThemePreferences
import com.nexaflow.core.datastore.ThemeSettings
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.ExecutionResultPresentation
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Parsed `nexaflow://run-task/{id}[?token=...&force=1]` target. */
internal data class RunTaskDeepLink(val automationId: String, val token: String?, val force: Boolean)

/**
 * Pure parser for run-task deep links so the admission/force policy contract
 * is unit-testable without activity scaffolding. Any other scheme, host, or a
 * blank id yields null — the app then just opens normally.
 *
 * P0.2: the automation id alone is NOT an authorization — custom schemes are
 * not verifiable ownership, so any app can craft `nexaflow://run-task/...`.
 * External execution requires a per-task opt-in `token` minted by the user.
 */
internal fun parseRunTaskDeepLink(uri: android.net.Uri?): RunTaskDeepLink? {
    if (uri == null) return null
    if (uri.scheme != "nexaflow" || uri.host != "run-task") return null
    val id = uri.path?.trim('/').orEmpty()
    if (id.isBlank()) return null
    return RunTaskDeepLink(
        automationId = id,
        token = uri.getQueryParameter("token")?.trim()?.ifBlank { null },
        force = uri.getQueryParameter("force") == "1"
    )
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "MainActivity"
    }

    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    lateinit var executionEngine: ExecutionEngine

    @Inject
    lateinit var automationRepository: AutomationRepository

    @Inject
    lateinit var backupManager: com.nexaflow.data.backup.BackupManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Branded splash (core-splashscreen): keep it up until the theme is
        // resolved so the background matches the actual Material You surface
        // color instead of the static XML fallback (values-night #1C1B1F).
        val splash = installSplashScreen()
        var themeResolved = false
        splash.setKeepOnScreenCondition { !themeResolved }
        super.onCreate(savedInstanceState)
        // Resolve the real theme (mode + dynamic color) and paint the window
        // with its surface color before releasing the splash. Dynamic color
        // can't be expressed in XML, so the window background is the only way
        // the splash-to-app handoff can match a wallpaper-sourced palette:
        // the splash fades into a window whose background already equals the
        // surface color the app draws on. On a slow read the static XML color
        // stays visible — no flash either way.
        lifecycleScope.launch {
            val settings = themePreferences.theme.first()
            window.setBackgroundDrawable(ColorDrawable(resolveSplashSurface(settings)))
            themeResolved = true
        }
        // Android 15+ enforces edge-to-edge for targetSdk 35+; opt in
        // explicitly so every API level draws behind the system bars
        // uniformly (status/nav bars stay transparent, Scaffolds handle insets).
        enableEdgeToEdge()
        // Privileged access is never used as a launch-time permission escalator.
        // Root/Shizuku and Android permissions are requested only from explicit
        // feature flows that need them, keeping startup least-privileged.
        // Deep link (P2-5): nexaflow://run-task/{id} runs the task directly.
        handleDeepLink(intent)
        // Single-task share target: a .nexaflow file opened from another app
        // (messenger, file manager, AirDrop-equivalent) imports on launch.
        handleSharedTask(intent)
        setContent {
            val theme by themePreferences.theme.collectAsStateWithLifecycle(initialValue = ThemeSettings())
            NexaFlowTheme(
                themeMode = theme.mode,
                accent = theme.accent,
                dynamicColor = theme.dynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NexaFlowApp()
                }
            }
            // Report time-to-full-display once the first frame is actually
            // drawn, so the system (and Play/Perfetto) measure real TTFD
            // instead of assuming the first frame. Must run after the frame.
            LaunchedEffect(Unit) {
                withFrameNanos { }
                reportFullyDrawn()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTop: a second deep link while the activity is alive arrives here.
        handleDeepLink(intent)
        handleSharedTask(intent)
    }

    /**
     * Imports a shared single-task (.nexaflow) file opened via ACTION_VIEW.
     * The import rides the same validated pipeline as full-backup imports
     * (structural preflight, workflow validation, ID-collision re-keying,
     * review-before-enable), and every outcome is surfaced as a toast so a
     * failed share is never a silent no-op.
     */
    private fun handleSharedTask(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        lifecycleScope.launch {
            // P0.3: bounded read — a hostile provider can no longer exhaust
            // memory through an unbounded content stream.
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { com.nexaflow.data.backup.ImportLimits.readBoundedText(it) }
                }.getOrNull()
            }
            val message = when {
                json == null -> getString(R.string.task_import_invalid_file)
                else -> when (val result = backupManager.importSingle(json)) {
                    is com.nexaflow.data.backup.SingleTaskImportResult.Success ->
                        getString(R.string.task_import_success, result.automation.name)
                    is com.nexaflow.data.backup.SingleTaskImportResult.InvalidWorkflow ->
                        getString(R.string.task_import_invalid_workflow)
                    com.nexaflow.data.backup.SingleTaskImportResult.NotSingle ->
                        getString(R.string.task_import_not_single)
                    com.nexaflow.data.backup.SingleTaskImportResult.InvalidFile ->
                        getString(R.string.task_import_invalid_file)
                }
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            try {
                WidgetUpdater.refreshAll(applicationContext)
            } catch (t: Throwable) {
                // Widget refresh is best-effort — a transient DB or widget
                // error must never force-close the app on every open.
                Log.e(TAG, "Widget refresh failed", t)
            }
        }
        refreshMonitoringServiceLifecycle()
    }

    /**
     * Android 17 background-audio hardening: volume and ringer writes are
     * silently discarded unless the calling app holds a foreground service
     * with while-in-use (WIU) capability. WIU is granted only when the FGS is
     * started while the app is visible — a service started from the boot
     * alarm never has it, so every scheduled volume/ringer action would fail
     * until the service is restarted from a visible context. Restarting the
     * running service on app open (a visibility event by definition) grants
     * WIU for the service's remaining lifetime. Idle when the service is not
     * running or the platform predates the hardening (pre-API 34 guard is
     * conservative and free), so the common open/close cycle costs nothing.
     */
    private fun refreshMonitoringServiceLifecycle() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (!com.nexaflow.core.engine.MonitoringService.isRunning) return
        try {
            com.nexaflow.core.engine.MonitoringService.stop(this)
            com.nexaflow.core.engine.MonitoringService.start(this)
        } catch (t: Throwable) {
            // A refused restart must never break app open; monitors re-arm
            // through their own recovery paths.
            Log.w(TAG, "Monitoring service refresh failed", t)
        }
    }

    /**
     * Runs the task targeted by a `nexaflow://run-task/{automationId}` deep
     * link. External execution is P0.2 fail-closed: without a valid per-task
     * opt-in token the link only opens the app (review surface) — nothing
     * runs. With a valid token, `?force=1` still routes through the explicit
     * force-run confirmation so a bypass is always a deliberate user action.
     * Missing/unknown ids are ignored silently so the app just opens normally
     * for any other launch.
     */
    private fun handleDeepLink(intent: Intent?) {
        val link = parseRunTaskDeepLink(intent?.data) ?: return
        lifecycleScope.launch {
            val automation = automationRepository.getAutomationById(link.automationId) ?: return@launch
            if (!deepLinkTokenAuthorized(link.token, automation.deepLinkToken)) {
                // Tokenless or mismatched link: review-only. Never execute.
                return@launch
            }
            if (link.force) {
                showForceRunConfirmation(automation)
            } else {
                runThroughAdmissionGate(automation)
            }
        }
    }


    /**
     * Manual invocation via deep link obeys the same admission policy as the
     * in-app Run now: the task's triggers and constraints must match,
     * otherwise only the end behavior runs (or the mismatch is reported
     * explicitly). The reason for a rejection is included in the toast so a
     * deep-link invocation is never a silent no-op.
     */
    private fun runThroughAdmissionGate(automation: com.nexaflow.domain.models.Automation) {
        lifecycleScope.launch {
            val record = executionEngine.runWithConditionGate(automation)
            val reason = executionEngine.describeManualBlock(automation)
            val reasonText = if (reason != null && reason.kind != ExecutionEngine.ManualBlockKind.NONE) {
                when (reason.kind) {
                    ExecutionEngine.ManualBlockKind.TRIGGERS_NOT_MET ->
                        reason.failedTriggerLabels.joinToString().ifEmpty { null }
                    ExecutionEngine.ManualBlockKind.TRIGGERS_UNKNOWN ->
                        reason.failedTriggerLabels.joinToString().ifEmpty { null }
                    ExecutionEngine.ManualBlockKind.CONSTRAINTS_NOT_MET ->
                        reason.failedConstraintLabels.joinToString().ifEmpty { null }
                    else -> null
                }?.let { " — $it" } ?: ""
            } else ""
            Toast.makeText(
                this@MainActivity,
                getString(R.string.deep_link_run_toast, automation.name) + " — " +
                    ExecutionResultPresentation.summary(this@MainActivity, record) + reasonText,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * The `force=1` path: mirrors the dashboard's force-run confirmation so
     * the bypass is never one accidental tap away. Confirming executes the
     * full action chain (the engine durably logs the bypass); dismissing
     * simply closes the dialog without running anything.
     */
    private fun showForceRunConfirmation(automation: com.nexaflow.domain.models.Automation) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.run_force_title)
            .setMessage(getString(R.string.deep_link_force_message, automation.name))
            .setPositiveButton(R.string.run_force_confirm) { _, _ ->
                lifecycleScope.launch {
                    val record = executionEngine.forceRun(automation)
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.deep_link_run_toast, automation.name) + " — " +
                            ExecutionResultPresentation.summary(this@MainActivity, record),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * The actual surface color the splash should paint, mirroring the same
     * resolution NexaFlowTheme uses: dark/light by mode, then the Material You
     * wallpaper palette when dynamic color is enabled (Android 12+), else the
     * static splash background as fallback.
     */
    private fun resolveSplashSurface(settings: ThemeSettings): Int {
        val dark = when (settings.mode) {
            ThemeMode.SYSTEM ->
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (dark) dynamicDarkColorScheme(this).surface.toArgb()
            else dynamicLightColorScheme(this).surface.toArgb()
        }
        return ContextCompat.getColor(this, R.color.splash_background)
    }
}
