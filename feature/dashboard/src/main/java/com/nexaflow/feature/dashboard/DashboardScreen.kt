package com.nexaflow.feature.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@Composable
fun DashboardScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = "Dashboard Screen")
        // TODO: Implement dashboard UI with active, scheduled, and running automations
    }
}
