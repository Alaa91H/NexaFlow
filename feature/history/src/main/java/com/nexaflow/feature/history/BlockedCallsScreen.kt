package com.nexaflow.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.NexaFlowCard
import kotlinx.coroutines.launch

/**
 * Blocked-call history in the spirit of BlackList's call log: one entry per
 * call-screening task run, with per-rule (per-task) filtering. Tapping an
 * entry opens a details sheet with the matching rule, the caller category,
 * and the actions the blocking task ran (from the engine's execution log).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlockedCallsScreen(navController: NavController) {
    val viewModel: BlockedCallsViewModel = hiltViewModel()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val selectedRule by viewModel.selectedRule.collectAsStateWithLifecycle()
    var detailsTarget by remember { mutableStateOf<BlockedCallEntry?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.blocked_calls_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedRule == null,
                    onClick = { viewModel.selectRule(null) },
                    label = { Text(text = stringResource(R.string.blocked_calls_filter_all)) }
                )
                rules.forEach { rule ->
                    FilterChip(
                        selected = selectedRule == rule,
                        onClick = { viewModel.selectRule(rule) },
                        label = { Text(text = rule) }
                    )
                }
            }
            if (entries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.blocked_calls_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        NexaFlowCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { detailsTarget = entry }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.blocked_calls_number_label,
                                        entry.maskedNumber
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = entry.automationName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = java.text.DateFormat.getDateTimeInstance()
                                        .format(java.util.Date(entry.executedAt)),
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

    detailsTarget?.let { entry ->
        val sheetState = rememberModalBottomSheetState()
        var ruleResults by remember(entry.id) { mutableStateOf<List<String>>(emptyList()) }
        var ruleTaskId by remember(entry.id) { mutableStateOf<String?>(null) }
        // Resolve the first matching rule's task id and its latest engine-run
        // action results off the main thread, once per opened entry.
        androidx.compose.runtime.LaunchedEffect(entry.id) {
            val ruleName = entry.ruleNames.firstOrNull() ?: return@LaunchedEffect
            ruleTaskId = viewModel.ruleTaskId(ruleName)
            ruleResults = viewModel.latestActionSummaries(ruleName)
        }
        ModalBottomSheet(
            onDismissRequest = { detailsTarget = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.blocked_calls_details_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                BlockedCallDetailRow(
                    label = stringResource(R.string.blocked_calls_details_number),
                    value = entry.maskedNumber
                )
                BlockedCallDetailRow(
                    label = stringResource(R.string.blocked_calls_details_category),
                    value = categoryLabel(entry.callerCategory)
                )
                BlockedCallDetailRow(
                    label = stringResource(R.string.blocked_calls_details_rules),
                    value = entry.ruleNames.ifEmpty {
                        listOf(entry.automationName)
                    }.joinToString(separator = ", ")
                )
                BlockedCallDetailRow(
                    label = stringResource(R.string.blocked_calls_details_time),
                    value = java.text.DateFormat.getDateTimeInstance()
                        .format(java.util.Date(entry.executedAt))
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = stringResource(R.string.blocked_calls_details_actions),
                    style = MaterialTheme.typography.titleSmall
                )
                if (ruleResults.isEmpty()) {
                    Text(
                        text = stringResource(R.string.blocked_calls_details_no_actions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ruleResults.forEach { summary ->
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                ruleTaskId?.let { id ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        // Jump straight into the blocking task in the builder.
                        TextButton(onClick = {
                            detailsTarget = null
                            navController.navigate("automation_builder?automationId=$id")
                        }) {
                            Text(stringResource(R.string.blocked_calls_details_open_rule))
                        }
                    }
                }
            }
        }
    }
}

/** Human-readable caller category for the details sheet. */
@Composable
private fun categoryLabel(category: String?): String = when (category) {
    "PRIVATE" -> stringResource(R.string.block_category_private)
    "CONTACT" -> stringResource(R.string.block_category_contact)
    "UNKNOWN" -> stringResource(R.string.block_category_unknown)
    "ANY" -> stringResource(R.string.block_category_any)
    else -> stringResource(R.string.blocked_calls_details_unknown)
}

@Composable
private fun BlockedCallDetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
