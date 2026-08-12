package com.nexaflow.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.execution.plugin.PluginFireClient
import com.nexaflow.core.pluginsdk.LocaleContract
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.toImageBitmapOrNull
import com.nexaflow.domain.models.PluginInfo
import kotlinx.coroutines.launch

/**
 * Plugin manager: lists every installed Locale-compatible plugin with its app
 * icon, label and package, offers a one-tap **test fire** (ordered broadcast
 * with an empty bundle) and opens the plugin's app-info screen. This is where
 * NexaFlow's extensibility becomes visible to users and attractive to
 * developers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagerScreen(navController: NavController) {
    val viewModel: PluginManagerViewModel = hiltViewModel()
    val context = LocalContext.current
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var testingPackage by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<Pair<PluginInfo, com.nexaflow.core.execution.plugin.PluginFireResult>?>(null) }

    val client = remember { PluginFireClient(timeoutMs = 4_000) }
    val stringTestSuccess = stringResource(R.string.plugin_test_success)
    val stringTestFailed = stringResource(R.string.plugin_test_failed)
    val stringTestTimeout = stringResource(R.string.plugin_test_timeout)
    val stringTestPending = stringResource(R.string.plugin_test_pending)

    fun testFire(plugin: PluginInfo) {
        testingPackage = plugin.packageName
        // PluginFireClient never throws and always returns a result (it catches
        // internally), so no extra error handling is needed here.
        scope.launch {
            val result = client.fire(context, plugin.packageName, plugin.receiverClass, bundle = null)
            testingPackage = null
            testResult = plugin to result
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.plugin_manager_title),
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.plugin_refresh)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.plugin_manager_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (refreshing && plugins.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }
            if (!refreshing && plugins.isEmpty()) {
                item {
                    NexaFlowCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.plugin_no_plugins),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.plugin_no_plugins_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            items(plugins.size, key = { plugins[it].receiverClass }) { index ->
                val plugin = plugins[index]
                NexaFlowCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val drawable = runCatching {
                                context.packageManager.getApplicationIcon(plugin.packageName)
                            }.getOrNull()
                            val bitmap = drawable?.toImageBitmapOrNull()
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(MaterialTheme.shapes.small),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bitmap != null) {
                                    Image(bitmap = bitmap, contentDescription = null)
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Extension,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plugin.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = plugin.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (testingPackage == plugin.packageName) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                enabled = testingPackage == null,
                                onClick = { testFire(plugin) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.plugin_test),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", plugin.packageName, null)
                                            )
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.plugin_open_info),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    testResult?.let { (plugin, result) ->
        val message = when {
            result.timedOut -> stringTestTimeout
            result.resultCode == LocaleContract.RESULT_CODE_OK -> stringTestSuccess
            result.resultCode == LocaleContract.RESULT_CODE_PENDING -> stringTestPending
            result.resultCode == LocaleContract.RESULT_CODE_FAILED ->
                stringTestFailed + (result.message?.let { ": $it" } ?: "")
            else -> result.message ?: plugin.label
        }
        AlertDialog(
            onDismissRequest = { testResult = null },
            title = { Text(text = plugin.label) },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = { testResult = null }) {
                    Text(text = stringResource(R.string.ok))
                }
            }
        )
    }
}
