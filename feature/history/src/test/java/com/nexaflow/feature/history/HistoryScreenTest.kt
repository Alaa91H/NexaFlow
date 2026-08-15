package com.nexaflow.feature.history

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.ExecutionRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI tests (Robolectric) for the paging states of [HistoryContent]:
 * loading spinner, full-screen error + working retry, empty state, and a
 * populated list. Each state is driven by a plain [PagingSource] fake (no
 * Hilt, no Room) through the real [androidx.paging.compose.collectAsLazyPagingItems]
 * pipeline, so the assertions exercise the exact production composable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Pinned to the highest SDK Robolectric 4.16.1 supports (project pattern);
// a tall window keeps a two-card list fully on screen.
@Config(sdk = [35], qualifiers = "w480dp-h900dp")
class HistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun record(id: String, name: String, success: Boolean = true) = ExecutionRecord(
        id = id,
        automationId = "a-$id",
        automationName = name,
        success = success,
        message = "completed",
        executedAt = 1_700_000_000_000L,
        channel = "ROOT",
        actionResults = listOf(ActionExecutionResult("SYSTEM_BRIGHTNESS", true, "ok", 5))
    )

    private fun setScreen(flow: Flow<PagingData<ExecutionRecord>>) {
        composeRule.setContent {
            HistoryContent(
                history = flow.collectAsLazyPagingItems(),
                onBack = {},
                onOpen = {}
            )
        }
    }

    /** Paging loads run on background dispatchers, so poll until a node appears. */
    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // --- loading ------------------------------------------------------------

    @Test
    fun loadingState_showsCenteredSpinner() {
        setScreen(Pager(PagingConfig(pageSize = 30)) { SuspendingSource() }.flow)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("history_loading").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("history_loading").assertIsDisplayed()
    }

    // --- error + retry ------------------------------------------------------

    @Test
    fun errorState_showsErrorMessage_andRetryRecovers() {
        setScreen(Pager(PagingConfig(pageSize = 30)) { FlakySource() }.flow)

        waitForText(context.getString(R.string.history_load_error_title))
        composeRule.onNodeWithTag("history_retry").assertIsDisplayed()

        composeRule.onNodeWithTag("history_retry").performClick()

        // The second load succeeds: the list must replace the error screen.
        waitForText("After Retry")
        composeRule.onNodeWithText("After Retry").assertIsDisplayed()
        composeRule.onNodeWithTag("history_retry").assertDoesNotExist()
    }

    // --- empty --------------------------------------------------------------

    @Test
    fun emptyState_showsNoRunsMessage() {
        // A real (empty) page load, not PagingData.empty(): the latter never
        // performs a load, so refresh would stay Loading and the screen would
        // keep showing the spinner instead of the empty state.
        setScreen(Pager(PagingConfig(pageSize = 30)) { EmptySource() }.flow)

        waitForText(context.getString(R.string.no_runs_title))
        composeRule.onNodeWithText(context.getString(R.string.no_runs_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.no_runs_subtitle)).assertIsDisplayed()
    }

    // --- populated list -----------------------------------------------------

    @Test
    fun successState_listsEveryRun() {
        setScreen(
            flowOf(
                PagingData.from(
                    listOf(
                        record("1", "Morning Mode"),
                        record("2", "Night Mode", success = false)
                    )
                )
            )
        )

        waitForText("Morning Mode")
        composeRule.onNodeWithText("Morning Mode").assertIsDisplayed()
        composeRule.onNodeWithText("Night Mode").assertIsDisplayed()
        // The channel line is rendered for the stored provider (e.g. "via Root").
        composeRule.onNodeWithText(context.getString(R.string.status_failed)).assertIsDisplayed()
    }

    // --- fakes --------------------------------------------------------------

    /** Load never returns: the refresh state stays Loading forever. */
    private class SuspendingSource : PagingSource<Int, ExecutionRecord>() {
        override fun getRefreshKey(state: PagingState<Int, ExecutionRecord>): Int? = null

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExecutionRecord> {
            CompletableDeferred<Unit>().await()
            return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        }
    }

    /** Empty page on load: refresh ends NotLoading with zero items. */
    private class EmptySource : PagingSource<Int, ExecutionRecord>() {
        override fun getRefreshKey(state: PagingState<Int, ExecutionRecord>): Int? = null

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExecutionRecord> =
            LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
    }

    /**
     * Signals failure via [LoadResult.Error] on the first attempt (the contract
     * Paging turns into LoadState.Error), then succeeds on retry — proves
     * [androidx.paging.compose.LazyPagingItems.retry] end to end.
     */
    private class FlakySource : PagingSource<Int, ExecutionRecord>() {
        private var attempts = 0

        override fun getRefreshKey(state: PagingState<Int, ExecutionRecord>): Int? = null

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExecutionRecord> {
            attempts++
            if (attempts == 1) {
                return LoadResult.Error(IllegalStateException("boom"))
            }
            return LoadResult.Page(
                data = listOf(ExecutionRecord("r", "a-r", "After Retry", true, "completed", 1L, "ROOT")),
                prevKey = null,
                nextKey = null
            )
        }
    }
}
