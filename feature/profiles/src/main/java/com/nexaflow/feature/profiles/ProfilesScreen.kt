package com.nexaflow.feature.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader

private data class Profile(val id: String, val name: String, val description: String, val icon: ImageVector, val color: Color)

private val demoProfiles = listOf(
    Profile("1", "Home", "Relaxed mode at home", Icons.Filled.Home, Color(0xFF1B62B7)),
    Profile("2", "Work", "Focus and notifications on", Icons.Filled.Work, Color(0xFF2FA84F)),
    Profile("3", "Driving", "Hands-free and DND", Icons.Filled.DirectionsCar, Color(0xFFE5533D)),
    Profile("4", "Night", "Dim screen, silent", Icons.Filled.Person, Color(0xFF7A5BD1))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(navController: NavController) {
    Scaffold(
        topBar = { NexaFlowTopBar(title = "Profiles", onBack = { navController.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { }, icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("New Profile") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(text = "PROFILES")
            }
            items(demoProfiles) { profile ->
                NexaFlowCard(modifier = Modifier.clickable(onClick = { })) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconBadge(icon = profile.icon, containerColor = profile.color)
                        Column {
                            Text(text = profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = profile.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
