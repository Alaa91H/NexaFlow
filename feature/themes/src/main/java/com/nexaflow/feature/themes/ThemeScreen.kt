package com.nexaflow.feature.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.ToggleRow

private data class AccentOption(val key: String, val color: Color, val label: String)

private val accentOptions = listOf(
    AccentOption("blue", Color(0xFF1B62B7), "Blue"),
    AccentOption("green", Color(0xFF2FA84F), "Green"),
    AccentOption("red", Color(0xFFE5533D), "Red"),
    AccentOption("purple", Color(0xFF7A5BD1), "Purple"),
    AccentOption("amber", Color(0xFFE8A33D), "Amber"),
    AccentOption("teal", Color(0xFF13A5A8), "Teal")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(navController: NavController) {
    val viewModel: ThemeViewModel = hiltViewModel()
    val theme by viewModel.theme.collectAsStateWithLifecycle()

    Scaffold(topBar = { NexaFlowTopBar(title = "Themes", onBack = { navController.popBackStack() }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(text = "DARK MODE")
            }
            item {
                NexaFlowCard {
                    ToggleRow(
                        icon = Icons.Filled.DarkMode,
                        title = "Dark mode",
                        subtitle = "Use the dark One UI color scheme",
                        checked = theme.darkMode,
                        onCheckedChange = { viewModel.setDarkMode(it) }
                    )
                }
            }
            item {
                SectionHeader(text = "ACCENT COLOR")
            }
            item {
                NexaFlowCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        accentOptions.forEach { option ->
                            val selected = theme.accent == option.key
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(color = option.color, shape = CircleShape)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.setAccent(option.key) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "Selected: ${accentOptions.firstOrNull { it.key == theme.accent }?.label ?: "Blue"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            item {
                SectionHeader(text = "PREVIEW")
            }
            item {
                NexaFlowCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconBadge(
                                icon = Icons.Filled.Palette,
                                containerColor = accentOptions.firstOrNull { it.key == theme.accent }?.color ?: Color(0xFF1B62B7)
                            )
                            Column {
                                Text(text = "NexaFlow theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (theme.darkMode) "Dark preview" else "Light preview",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = accentOptions.firstOrNull { it.key == theme.accent }?.color ?: Color(0xFF1B62B7),
                                    shape = MaterialTheme.shapes.medium
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Primary accent",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
