package com.nexaflow.feature.history

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import com.nexaflow.core.ui.LoadingState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.nexaflow.core.ui.EmptyState
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.StatusPill
import com.nexaflow.domain.models.ExecutionHistoryOutcome
import com.nexaflow.domain.models.ExecutionOutcomeClassifier
import com.nexaflow.domain.models.ExecutionRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Stateful wrapper: owns the ViewModel and hands a stateless [HistoryContent] the paged flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val selectedOutcome by viewModel.selectedOutcome.collectAsStateWithLifecycle()
    HistoryContent(
        // Streams pages from Room instead of materializing the whole table.
        history = viewModel.pagingData.collectAsLazyPagingItems(),
        isRoutineHistory = viewModel.isRoutineHistory,
        selectedOutcome = selectedOutcome,
        onSelectedOutcomeChange = viewModel::setSelectedOutcome,
        onBack = { navController.popBackStack() },
        onOpen = { id -> navController.navigate("execution_details/$id") }
    )
}

/**
 * Stateless history body — takes an already-collected [LazyPagingItems] so the
 * four paging states (loading / error+retry / empty / list) are unit-testable
 * with plain [androidx.paging.PagingSource] fakes, without Hilt or Room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryContent(
    history: LazyPagingItems<ExecutionRecord>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    isRoutineHistory: Boolean = false,
    selectedOutcome: ExecutionHistoryOutcome? = null,
    onSelectedOutcomeChange: (ExecutionHistoryOutcome?) -> Unit = {}
) {
    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(
                    if (isRoutineHistory) R.string.routine_history_title else R.string.history_title
                ),
                onBack = onBack
            )
        }
    ) { padding ->
        when (val refresh = history.loadState.refresh) {
            is LoadState.Loading -> {
                if (history.itemCount == 0) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding).testTag("history_loading"),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
            }
            is LoadState.Error -> {
                if (history.itemCount == 0) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EmptyState(
                            icon = Icons.Filled.History,
                            title = stringResource(R.string.history_load_error_title),
                            subtitle = stringResource(R.string.history_load_error_subtitle)
                        )
                        TextButton(
                            onClick = { history.retry() },
                            modifier = Modifier.testTag("history_retry")
                        ) {
                            Text(stringResource(R.string.history_retry))
                        }
                    }
                }
            }
            else -> Unit
        }

        if (history.itemCount == 0 && history.loadState.refresh is LoadState.NotLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HistoryOutcomeFilters(
                    selectedOutcome = selectedOutcome,
                    onSelectedOutcomeChange = onSelectedOutcomeChange
                )
                EmptyState(
                    icon = Icons.Filled.History,
                    title = stringResource(
                        when (selectedOutcome) {
                            ExecutionHistoryOutcome.FAILED -> R.string.no_failed_runs_title
                            ExecutionHistoryOutcome.SKIPPED -> R.string.no_skipped_runs_title
                            null -> R.string.no_runs_title
                        }
                    ),
                    subtitle = stringResource(
                        when (selectedOutcome) {
                            ExecutionHistoryOutcome.FAILED -> R.string.no_failed_runs_subtitle
                            ExecutionHistoryOutcome.SKIPPED -> R.string.no_skipped_runs_subtitle
                            null -> R.string.no_runs_subtitle
                        }
                    )
                )
            }
        } else if (history.itemCount > 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    HistoryOutcomeFilters(
                        selectedOutcome = selectedOutcome,
                        onSelectedOutcomeChange = onSelectedOutcomeChange
                    )
                }
                // Paging 3.4 API: LazyPagingItems exposes stable key/contentType
                // factories; the list itself is streamed via items(count = ...).
                items(
                    count = history.itemCount,
                    key = history.itemKey { it.id }
                ) { index ->
                    val entry = history[index]
                    if (entry != null) {
                        HistoryCard(
                            entry = entry,
                            alternatingIndex = index,
                            onClick = { onOpen(entry.id) }
                        )
                    }
                }
                when (val append = history.loadState.append) {
                    is LoadState.Loading -> item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
                    }
                    is LoadState.Error -> item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = { history.retry() },
                                modifier = Modifier.testTag("history_retry_append")
                            ) {
                                Text(stringResource(R.string.history_retry))
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: ExecutionRecord,
    alternatingIndex: Int,
    onClick: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = remember(locale) { SimpleDateFormat("MMM d, HH:mm", locale) }
    NexaFlowCard(
        modifier = Modifier.clickable { onClick() },
        alternatingIndex = alternatingIndex
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(
                icon = Icons.Filled.Bolt,
                containerColor = if (entry.success) NexaFlowTheme.colors.successContainer else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (entry.success) NexaFlowTheme.colors.success else MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.automationName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
                // Show which execution channel actually ran this task (e.g. "via Root").
                val channel = entry.channel
                if (channel != null) {
                    Text(
                        text = stringResource(
                            R.string.executed_via,
                            stringResource(channelLabelRes(channel))
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = timeFormat.format(Date(entry.executedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            StatusPill(
                text = stringResource(
                    when {
                        !entry.success -> R.string.status_failed
                        ExecutionOutcomeClassifier.isSkipped(entry) -> R.string.status_skipped
                        else -> R.string.status_success
                    }
                ),
                background = if (entry.success) NexaFlowTheme.colors.successContainer else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (entry.success) NexaFlowTheme.colors.success else MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Maps the stored channel name to its localized label. */
@StringRes
internal fun channelLabelRes(channel: String): Int {
    return when (channel) {
        "ROOT" -> R.string.channel_root
        "SHIZUKU" -> R.string.channel_shizuku
        "SYSTEM_APP" -> R.string.channel_system_app
        "ADB" -> R.string.channel_adb
        "ANDROID" -> R.string.channel_android
        "ACCESSIBILITY" -> R.string.channel_accessibility
        else -> R.string.channel_unknown
    }
}

/** Local, read-only outcome controls for global or routine-scoped execution history. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryOutcomeFilters(
    selectedOutcome: ExecutionHistoryOutcome?,
    onSelectedOutcomeChange: (ExecutionHistoryOutcome?) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedOutcome == null,
            onClick = { onSelectedOutcomeChange(null) },
            label = { Text(stringResource(R.string.history_filter_all_runs)) }
        )
        FilterChip(
            selected = selectedOutcome == ExecutionHistoryOutcome.FAILED,
            onClick = { onSelectedOutcomeChange(ExecutionHistoryOutcome.FAILED) },
            label = { Text(stringResource(R.string.history_filter_failed)) }
        )
        FilterChip(
            selected = selectedOutcome == ExecutionHistoryOutcome.SKIPPED,
            onClick = { onSelectedOutcomeChange(ExecutionHistoryOutcome.SKIPPED) },
            label = { Text(stringResource(R.string.history_filter_skipped)) }
        )
    }
}
