package com.nexaflow.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
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
import com.nexaflow.core.rom.RootPermissionGranter
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
     * needs automatically — no dialogs, no system screens. When a root manager
     * is installed but root was never granted yet, [requestAndGrantAll] first
     * pops the manager's one-tap allow dialog and waits for the user, so a
     * fresh install ends up fully granted instead of silently skipping. Runs
     * once on launch, off the main thread; the notification permission prompt
     * below is skipped entirely when the grant succeeds.
     */
    private fun autoGrantPermissionsWithRoot() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    RootPermissionGranter.requestAndGrantAll(applicationContext)
                }.onSuccess { result ->
                    if (result.anyGranted) {
                        Log.i(
                            "MainActivity",
                            "Auto-granted via root: " +
                                "runtime=${result.runtimeGranted.size}, " +
                                "appOps=${result.appOpsGranted.size}, " +
                                "secure=${result.secureSettingsWritten.size}, " +
                                "batteryExempt=${result.batteryExempted}, " +
                                "listener=${result.notificationListenerGranted}"
                        )
                    }
                    if (result.remaining.isNotEmpty()) {
                        Log.w(
                            "MainActivity",
                            "Still missing after auto-grant: ${result.remaining}"
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
