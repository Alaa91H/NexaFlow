package com.nexaflow.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.nexaflow.app.ui.theme.NexaFlowTheme
import com.nexaflow.core.datastore.ThemePreferences
import com.nexaflow.core.datastore.ThemeSettings
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.rom.RootPermissionGranter
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    lateinit var executionEngine: ExecutionEngine

    @Inject
    lateinit var automationRepository: AutomationRepository

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Branded splash (core-splashscreen): keep it up until the first frame
        // is drawn so the brand color never flashes to a blank window.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Android 15+ enforces edge-to-edge for targetSdk 35+; opt in
        // explicitly so every API level draws behind the system bars
        // uniformly (status/nav bars stay transparent, Scaffolds handle insets).
        enableEdgeToEdge()
        // On a rooted device, grant everything silently first so the system
        // permission prompt below is skipped entirely.
        autoGrantPermissionsWithRoot()
        // Deep link (P2-5): nexaflow://run-task/{id} runs the task directly.
        handleDeepLink(intent)
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
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTop: a second deep link while the activity is alive arrives here.
        handleDeepLink(intent)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            try {
                WidgetUpdater.refreshAll(applicationContext)
            } catch (t: Throwable) {
                // Widget refresh is best-effort — a transient DB or widget
                // error must never force-close the app on every open.
                Log.e("MainActivity", "Widget refresh failed", t)
            }
        }
    }

    /**
     * Runs the task targeted by a `nexaflow://run-task/{automationId}` deep
     * link. Missing/unknown ids are ignored silently so the app just opens
     * normally for any other launch.
     */
    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "nexaflow" || uri.host != "run-task") return
        val id = uri.path?.trim('/') ?: return
        if (id.isBlank()) return
        lifecycleScope.launch {
            val automation = automationRepository.getAutomationById(id)
            if (automation != null) {
                val record = executionEngine.runAutomation(automation)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.deep_link_run_toast, automation.name) + " — " + record.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * On a rooted (or Shizuku-granted) device, grant every permission the app
     * needs automatically — no dialogs, no system screens. Runtime permissions
     * via `pm grant`, special app-ops via `appops set`, battery exemption via
     * deviceidle whitelist, accessibility via secure settings. Runs once on
     * launch, off the main thread; the notification permission prompt below is
     * skipped entirely when the grant succeeds.
     */
    private fun autoGrantPermissionsWithRoot() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    RootPermissionGranter.grantAll(applicationContext)
                }.onSuccess { result ->
                    if (result.anyGranted) {
                        Log.i(
                            "MainActivity",
                            "Auto-granted via root: " +
                                "runtime=${result.runtimeGranted.size}, " +
                                "appOps=${result.appOpsGranted.size}, " +
                                "secure=${result.secureSettingsWritten.size}, " +
                                "batteryExempt=${result.batteryExempted}"
                        )
                    }
                }.onFailure { t ->
                    Log.w("MainActivity", "Auto-grant skipped", t)
                }
            }
            // Only prompt for notifications when root was NOT able to grant
            // them — a rooted user never sees the system permission dialog.
            if (!hasNotificationPermission()) {
                requestNotificationPermissionIfNeeded()
            }
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
