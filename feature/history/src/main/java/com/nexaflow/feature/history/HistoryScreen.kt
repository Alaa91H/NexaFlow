package com.nexaflow.feature.history

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
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
import com.nexaflow.core.ui.CategoryChip
import com.nexaflow.core.ui.CategoryChipRow
import com.nexaflow.core.ui.Dimens
import com.nexaflow.core.ui.EmptyState
import com.nexaflow.core.ui.IconTile
import com.nexaflow.core.ui.ScreenHeader
import com.nexaflow.core.ui.StatusPill
import com.nexaflow.core.ui.theme.NexaFlowAlphas
import com.nexaflow.core.ui.theme.NexaFlowCategory
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.core.ui.theme.forText
import com.nexaflow.core.ui.theme.forUserText
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
            // Ink (§7.4): the sub-screen header instead of the app top bar.
            Column(
                modifier = Modifier.padding(
                    horizontal = Dimens.Space4,
                    vertical = Dimens.Space2
                )
            ) {
                ScreenHeader(
                    title = stringResource(
                        if (isRoutineHistory) R.string.routine_history_title else R.string.history_title
                    ),
                    onBack = onBack,
                    backContentDescription = stringResource(R.string.back),
                    titleStyle = NexaFlowTheme.type.subScreenTitle
                )
            }
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
                        val previous = if (index > 0) history[index - 1] else null
                        val next = if (index + 1 < history.itemCount) history[index + 1] else null
                        val startsDay = previous == null ||
                            dayOf(previous.executedAt) != dayOf(entry.executedAt)
                        val endsDay = next == null ||
                            dayOf(next.executedAt) != dayOf(entry.executedAt)
                        if (startsDay) {
                            HistoryDayLabel(executedAt = entry.executedAt)
                        }
                        HistoryRow(
                            entry = entry,
                            isFirstOfDay = startsDay,
                            isLastOfDay = endsDay,
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
private fun HistoryRow(
    entry: ExecutionRecord,
    isFirstOfDay: Boolean,
    isLastOfDay: Boolean,
    onClick: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    val succeeded = entry.success || ExecutionOutcomeClassifier.isSkipped(entry)
    val shape = RoundedCornerShape(
        topStart = if (isFirstOfDay) Dimens.GroupRadius else 0.dp,
        topEnd = if (isFirstOfDay) Dimens.GroupRadius else 0.dp,
        bottomStart = if (isLastOfDay) Dimens.GroupRadius else 0.dp,
        bottomEnd = if (isLastOfDay) Dimens.GroupRadius else 0.dp
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                Dimens.HairlineBorder,
                MaterialTheme.colorScheme.onSurface.copy(alpha = NexaFlowAlphas.HairlineBorder),
                shape
            )
            .clickable { onClick() }
    ) {
        if (!isFirstOfDay) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.HairlineBorder)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = NexaFlowAlphas.Divider))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Space4, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.TileTextGap)
        ) {
            IconTile(
                icon = Icons.Filled.Bolt,
                category = NexaFlowCategory.ADVANCED,
                size = Dimens.PickerTile,
                radius = Dimens.PickerTileRadius,
                picker = true
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entry.automationName,
                    style = NexaFlowTheme.type.pickerRowTitle.forUserText(entry.automationName),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(com.nexaflow.core.execution.ExecutionResultPresentation.summaryRes(entry)),
                    style = NexaFlowTheme.type.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                // Skips carry the exact reason in the backend diagnostic message
                // ("Skipped: ..."). Surface it here so a silent skip is always
                // diagnosable from the history list without opening details.
                val skipReason = if (ExecutionOutcomeClassifier.isSkipped(entry)) {
                    entry.message.removePrefix(
                        com.nexaflow.domain.models.ExecutionOutcomeClassifier.SKIPPED_MESSAGE_PREFIX
                    ).trim()
                } else {
                    null
                }
                if (!skipReason.isNullOrEmpty()) {
                    Text(
                        text = skipReason,
                        style = NexaFlowTheme.type.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                // Show which execution channel actually ran this task (e.g. "via Root").
                val channel = entry.channel
                if (channel != null) {
                    Text(
                        text = stringResource(
                            R.string.executed_via,
                            stringResource(channelLabelRes(channel))
                        ),
                        style = NexaFlowTheme.type.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = timeFormat.format(Date(entry.executedAt)),
                    style = NexaFlowTheme.type.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(
                text = stringResource(
                    when {
                        ExecutionOutcomeClassifier.isSkipped(entry) -> R.string.status_skipped
                        !entry.success -> R.string.status_failed
                        else -> R.string.status_success
                    }
                ),
                background = if (succeeded) {
                    NexaFlowTheme.colors.successContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (succeeded) {
                    NexaFlowTheme.colors.success
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

/** 13sp/700 day separator above the first run of each day (§7.4). */
@Composable
private fun HistoryDayLabel(executedAt: Long) {
    val locale = LocalConfiguration.current.locales[0]
    val dayFormat = remember(locale) { SimpleDateFormat("EEEE, MMM d", locale) }
    Text(
        text = dayFormat.format(Date(executedAt)),
        style = NexaFlowTheme.type.sectionLabel.forText(dayFormat.format(Date(executedAt))),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Dimens.Space1, top = Dimens.Space2)
    )
}

/** Start-of-day bucket used to group history rows without a second query. */
private fun dayOf(epochMillis: Long): Long {
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = epochMillis
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
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
    // Ink (§7.4): the same category chips as the picker instead of stock
    // FilterChips, so the filter row matches the rest of the app.
    CategoryChipRow {
        CategoryChip(
            selected = selectedOutcome == null,
            onClick = { onSelectedOutcomeChange(null) },
            label = stringResource(R.string.history_filter_all_runs)
        )
        CategoryChip(
            selected = selectedOutcome == ExecutionHistoryOutcome.FAILED,
            onClick = { onSelectedOutcomeChange(ExecutionHistoryOutcome.FAILED) },
            label = stringResource(R.string.history_filter_failed)
        )
        CategoryChip(
            selected = selectedOutcome == ExecutionHistoryOutcome.SKIPPED,
            onClick = { onSelectedOutcomeChange(ExecutionHistoryOutcome.SKIPPED) },
            label = stringResource(R.string.history_filter_skipped)
        )
    }
}
