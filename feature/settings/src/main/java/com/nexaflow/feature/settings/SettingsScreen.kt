package com.nexaflow.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.SettingRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(topBar = { NexaFlowTopBar(title = "Settings", onBack = { navController.popBackStack() }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        ) {
            item {
                SectionHeader(text = "APPEARANCE")
            }
            item {
                NexaFlowCard {
                    SettingRow(
                        icon = Icons.Filled.Palette,
                        title = "Themes",
                        subtitle = "Pick a One UI color scheme",
                        onClick = { navController.navigate("themes") }
                    )
                    SettingRow(
                        icon = Icons.Filled.Widgets,
                        title = "Widgets",
                        subtitle = "Manage home screen widgets",
                        onClick = { navController.navigate("widgets") }
                    )
                }
            }
            item {
                SectionHeader(text = "INTEGRATION")
            }
            item {
                NexaFlowCard {
                    SettingRow(
                        icon = Icons.Filled.Security,
                        title = "Capability Center",
                        subtitle = "ROM features and permissions",
                        onClick = { navController.navigate("capability_center") }
                    )
                    SettingRow(
                        icon = Icons.Filled.PlayArrow,
                        title = "Execution History",
                        subtitle = "View automation runs",
                        onClick = { navController.navigate("history") }
                    )
                }
            }
            item {
                SectionHeader(text = "ABOUT")
            }
            item {
                NexaFlowCard {
                    SettingRow(
                        icon = Icons.Filled.Info,
                        title = "About NexaFlow",
                        subtitle = "Version 1.0.0"
                    )
                    SettingRow(
                        icon = Icons.Filled.Settings,
                        title = "ROM Integration",
                        subtitle = "System-level access when baked into a ROM"
                    )
                }
            }
        }
    }
}
