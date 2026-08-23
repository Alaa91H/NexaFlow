package com.nexaflow.core.ui

import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview

/** A top-level destination of the app's bottom navigation bar. */
data class NavigationDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Google 2026 bottom navigation — M3 NavigationBar density (80dp, icon +
 * label) with the tonal pill indicator and quiet unselected items.
 */
@Composable
fun NexaFlowNavigationBar(
    currentRoute: String?,
    destinations: List<NavigationDestination>,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        destinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination.route) },
                icon = { Icon(imageVector = destination.icon, contentDescription = null) },
                label = {
                    Text(
                        text = destination.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Preview(name = "NavigationBar", showBackground = true)
@Composable
private fun NavigationBarPreview() {
    MaterialTheme {
        NexaFlowNavigationBar(
            currentRoute = "home",
            destinations = listOf(
                NavigationDestination(route = "home", label = "Home", icon = Icons.Filled.Home),
                NavigationDestination(route = "settings", label = "Settings", icon = Icons.Filled.Settings)
            ),
            onNavigate = {}
        )
    }
}

@Preview(name = "NavigationBar – Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NavigationBarDarkPreview() {
    MaterialTheme {
        NexaFlowNavigationBar(
            currentRoute = "home",
            destinations = listOf(
                NavigationDestination(route = "home", label = "Home", icon = Icons.Filled.Home),
                NavigationDestination(route = "settings", label = "Settings", icon = Icons.Filled.Settings)
            ),
            onNavigate = {}
        )
    }
}
