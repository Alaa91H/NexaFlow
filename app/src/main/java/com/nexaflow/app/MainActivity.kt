package com.nexaflow.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import com.nexaflow.app.ui.theme.NexaFlowTheme
import com.nexaflow.core.datastore.ThemePreferences
import com.nexaflow.core.datastore.ThemeSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferences: ThemePreferences

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+ enforces edge-to-edge for targetSdk 35+; opt in
        // explicitly so every API level draws behind the system bars
        // uniformly (status/nav bars stay transparent, Scaffolds handle insets).
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            val theme by themePreferences.theme.collectAsStateWithLifecycle(initialValue = ThemeSettings())
            NexaFlowTheme(themeMode = theme.mode, accent = theme.accent) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NexaFlowApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { WidgetUpdater.refreshAll(applicationContext) }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
