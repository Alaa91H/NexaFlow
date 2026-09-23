package com.nexaflow.feature.dashboard

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.AutomationHealthReport
import com.nexaflow.domain.models.AutomationHealthStatus
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w480dp-h900dp")
class RoutineCardLiveStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            1
        )
    }




    @Test
    fun expandedCardShowsDurableExecutionHealthDiagnostics() {
        val automation = Automation(
            id = "health-card",
            name = "Health card",
            description = "",
            icon = "bolt",
            iconColor = 0xFFFFFFFF,
            backgroundColor = 0xFF3A3A3A,
            category = "custom",
            priority = 0,
            enabled = true,
            triggers = emptyList(),
            actions = emptyList(),
            createdAt = 0,
            updatedAt = 0
        )
        val report = AutomationHealthReport(
            automationId = automation.id,
            lastExecutionAt = 1L,
            completedRuns = 8,
            skippedRuns = 2,
            failedRuns = 4,
            consecutiveFailures = 3,
            latestFailureMessage = "Permission unavailable",
            status = AutomationHealthStatus.NEEDS_ATTENTION,
            recoveryReviewPending = true
        )

        composeRule.setContent {
            MaterialTheme {
                RoutineCard(
                    row = AutomationRow(
                        automation = automation,
                        lastRunAt = 1L,
                        lastRunSucceeded = false,
                        healthReport = report
                    ),
                    summary = "Health summary",
                    nextRun = null,
                    isRunning = false,
                    containerColor = Color.DarkGray,
                    expanded = true,
                    menuExpanded = false,
                    onRun = {},
                    onEdit = {},
                    onDelete = {},
                    onShare = {},
                    onToggle = {},
                    onToggleToast = { _, _ -> },
                    onExpandedChange = {},
                    onLongClick = {},
                    onDismissMenu = {},
                    modifier = Modifier
                )
            }
        }

        composeRule.onNodeWithText("Execution health").assertIsDisplayed()
        composeRule.onNodeWithText("Needs attention").assertIsDisplayed()
        composeRule.onNodeWithText("Recovery review pending").assertIsDisplayed()
        composeRule.onNodeWithText("Permission unavailable").assertIsDisplayed()
    }

    @Test
    fun durableExitFailureStaysVisibleAfterTaskIsDisabled() {
        val id = "disabled-exit-failure-card"
        val store = AutomationRuntimeStore(context)
        runBlocking {
            store.clear(id)
            store.activate(
                AutomationRuntimeState(
                    automationId = id,
                    occurrenceId = "occurrence",
                    source = "test",
                    sourceKey = "source",
                    lifecycleState = AutomationRuntimeLifecycleState.EXIT_FAILED,
                    activatedAt = System.currentTimeMillis() - 1_000,
                    exitStartedAt = System.currentTimeMillis(),
                    exitAttempt = 1,
                    exitReason = ExitReason.AUTOMATION_DISABLED
                )
            )
        }

        val automation = Automation(
            id = id,
            name = "Disabled with failed exit",
            description = "",
            icon = "bolt",
            iconColor = 0xFFFFFFFF,
            backgroundColor = 0xFF3A3A3A,
            category = "custom",
            priority = 0,
            enabled = false,
            triggers = emptyList(),
            actions = emptyList(),
            createdAt = 0,
            updatedAt = 0
        )

        composeRule.setContent {
            MaterialTheme {
                RoutineCard(
                    row = AutomationRow(automation = automation, lastRunAt = null),
                    summary = "Disabled exit failure summary",
                    nextRun = null,
                    isRunning = false,
                    containerColor = Color.DarkGray,
                    expanded = true,
                    menuExpanded = false,
                    onRun = {},
                    onEdit = {},
                    onDelete = {},
                    onShare = {},
                    onToggle = {},
                    onToggleToast = { _, _ -> },
                    onExpandedChange = {},
                    onLongClick = {},
                    onDismissMenu = {},
                    modifier = Modifier
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("End failed").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithText("Task was disabled").assertIsDisplayed()
        composeRule.onNodeWithText("End behavior needs recovery").assertIsDisplayed()

        runBlocking { store.clear(id) }
    }

    @Test
    fun activeRuntimeLifecycleIsShownWithRestorePlan() {
        val id = "active-lifecycle-card"
        val store = AutomationRuntimeStore(context)
        runBlocking {
            store.clear(id)
            store.activate(
                AutomationRuntimeState(
                    automationId = id,
                    occurrenceId = "occurrence",
                    source = "test",
                    sourceKey = "source",
                    lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                    activatedAt = System.currentTimeMillis()
                )
            )
        }

        val automation = Automation(
            id = id,
            name = "Active lifecycle",
            description = "",
            icon = "bolt",
            iconColor = 0xFFFFFFFF,
            backgroundColor = 0xFF3A3A3A,
            category = "custom",
            priority = 0,
            enabled = true,
            triggers = emptyList(),
            actions = listOf(
                Action(
                    type = ActionType.SYSTEM_SCREEN_ROTATION,
                    config = mapOf("autoRotate" to "true")
                )
            ),
            revertOnExit = true,
            createdAt = 0,
            updatedAt = 0
        )

        composeRule.setContent {
            MaterialTheme {
                RoutineCard(
                    row = AutomationRow(automation = automation, lastRunAt = null),
                    summary = "Active lifecycle summary",
                    nextRun = null,
                    isRunning = false,
                    containerColor = Color.DarkGray,
                    expanded = true,
                    menuExpanded = false,
                    onRun = {},
                    onEdit = {},
                    onDelete = {},
                    onShare = {},
                    onToggle = {},
                    onToggleToast = { _, _ -> },
                    onExpandedChange = {},
                    onLongClick = {},
                    onDismissMenu = {},
                    modifier = Modifier
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Active").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithText("Summary").assertIsDisplayed()
        composeRule.onNodeWithText("Active lifecycle summary").assertIsDisplayed()
        composeRule.onNodeWithText("Conditions are active and the task is currently in effect")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Restore the original state").assertIsDisplayed()

        runBlocking { store.clear(id) }
    }

    @Test
    fun expandedRotationActionShowsLiveCurrentStateAndTargetMatch() {
        val automation = Automation(
            id = "rotation-live-state",
            name = "Rotation",
            description = "",
            icon = "bolt",
            iconColor = 0xFFFFFFFF,
            backgroundColor = 0xFF3A3A3A,
            category = "custom",
            priority = 0,
            enabled = true,
            triggers = emptyList(),
            actions = listOf(
                Action(
                    type = ActionType.SYSTEM_SCREEN_ROTATION,
                    config = mapOf("autoRotate" to "true")
                )
            ),
            createdAt = 0,
            updatedAt = 0
        )

        composeRule.setContent {
            MaterialTheme {
                RoutineCard(
                    row = AutomationRow(
                        automation = automation,
                        lastRunAt = null
                    ),
                    summary = "Rotation test",
                    nextRun = null,
                    isRunning = false,
                    containerColor = Color.DarkGray,
                    expanded = true,
                    menuExpanded = false,
                    onRun = {},
                    onEdit = {},
                    onDelete = {},
                    onShare = {},
                    onToggle = {},
                    onToggleToast = { _, _ -> },
                    onExpandedChange = {},
                    onLongClick = {},
                    onDismissMenu = {},
                    modifier = Modifier
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Matches target").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithText("Readiness now").assertIsDisplayed()
        composeRule.onNodeWithText("Ready now").assertIsDisplayed()
        composeRule.onNodeWithText("Task lifecycle").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting").assertIsDisplayed()
        composeRule.onNodeWithText("Leave the current state unchanged").assertIsDisplayed()
        composeRule.onNodeWithText("Current state").assertIsDisplayed()
        composeRule.onNodeWithText("Matches target").assertIsDisplayed()
    }
}
