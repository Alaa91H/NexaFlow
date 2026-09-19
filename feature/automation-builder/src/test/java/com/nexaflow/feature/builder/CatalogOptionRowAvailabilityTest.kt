package com.nexaflow.feature.builder

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI tests (Robolectric) for the builder option pickers' locked-row
 * states ([CatalogOptionRow] through [ActionOptionRow]/[TriggerOptionRow]).
 *
 * The picker must render three distinct row states for the same catalogue
 * entry (regression coverage for issue #5, "options missing on my phone"):
 *  - READY: the row is tappable and toggles the option directly.
 *  - PERMISSION_REQUIRED: the row stays visible and locked, shows the grant
 *    hint plus the "Tap to grant" pill, and tapping routes to the grant flow
 *    instead of toggling.
 *  - UNSUPPORTED: the row stays visible, shows the unavailable message and
 *    pill, and cannot be toggled at all.
 *
 * The harness uses the real catalogue option for dark mode so any drift
 * between the option model and the row composable is caught too.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Compose-UI tests stay pinned to SDK 35 for the same Espresso/Robolectric
// SDK-37 InputManager incompatibility documented in SpecialPermissionStatusRowTest.
@Config(sdk = [35])
class CatalogOptionRowAvailabilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    // A real catalog entry (icon, title/subtitle resources, category color)
    // so the row renders exactly as it does in the production picker.
    private val option: ActionOption =
        actionOptions.first { it.actionType == com.nexaflow.domain.models.ActionType.SYSTEM_DARK_MODE }

    private fun setActionRow(
        availability: BuilderOptionAvailability,
        onToggle: () -> Unit = {},
        onBlockedClick: () -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                ActionOptionRow(
                    option = option,
                    checked = false,
                    onToggle = onToggle,
                    availability = availability,
                    onBlockedClick = onBlockedClick
                )
            }
        }
    }

    // --- READY: tappable row, toggles the option, no lock chrome ----------

    @Test
    fun readyState_showsTitle_withoutAnyLockedHint() {
        setActionRow(availability = BuilderOptionAvailability.READY)

        composeRule.onNodeWithText(context.getString(option.titleRes)).assertIsDisplayed()

        // Neither locked message may leak into a ready row.
        composeRule.onAllNodesWithText(context.getString(R.string.permission_denied_hint))
            .assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.elevated_status_available))
            .assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.elevated_status_unavailable))
            .assertCountEquals(0)
    }

    @Test
    fun readyState_rowTap_togglesTheOption() {
        var toggles = 0
        var blocked = 0
        setActionRow(
            availability = BuilderOptionAvailability.READY,
            onToggle = { toggles++ },
            onBlockedClick = { blocked++ }
        )

        composeRule.onNodeWithText(context.getString(option.titleRes)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, toggles)
        assertEquals(0, blocked)
    }

    // --- PERMISSION_REQUIRED: visible, locked, grantable -------------------

    @Test
    fun permissionRequired_showsGrantHintAndTapToGrantPill() {
        setActionRow(availability = BuilderOptionAvailability.PERMISSION_REQUIRED)

        composeRule.onNodeWithText(context.getString(R.string.permission_denied_hint))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.elevated_status_available))
            .assertIsDisplayed()
    }

    @Test
    fun permissionRequired_rowTap_routesToGrantFlow_andNeverToggles() {
        var toggles = 0
        var blocked = 0
        setActionRow(
            availability = BuilderOptionAvailability.PERMISSION_REQUIRED,
            onToggle = { toggles++ },
            onBlockedClick = { blocked++ }
        )

        composeRule.onNodeWithText(context.getString(option.titleRes)).performClick()
        composeRule.waitForIdle()

        assertEquals("a locked row must never toggle the option", 0, toggles)
        assertEquals("a locked row must route into the grant flow", 1, blocked)
    }

    // --- UNSUPPORTED: visible but not grantable -----------------------------

    @Test
    fun unsupported_showsUnavailableMessage_andUnavailablePill() {
        setActionRow(availability = BuilderOptionAvailability.UNSUPPORTED)

        // Both the inline locked message and the trailing status pill render
        // the same localized text — the pair is the contract for a device
        // that genuinely cannot support the option. The row's disabled
        // clickable merges descendant semantics, so the two individual text
        // nodes only exist in the unmerged tree.
        composeRule.onAllNodesWithText(
            context.getString(R.string.elevated_status_unavailable),
            useUnmergedTree = true
        ).assertCountEquals(2)
        composeRule.onAllNodesWithText(
            context.getString(R.string.elevated_status_unavailable),
            useUnmergedTree = true
        )[0].assertIsDisplayed()
        // The grant hint must never appear on an unsupported row.
        composeRule.onAllNodesWithText(context.getString(R.string.permission_denied_hint))
            .assertCountEquals(0)
    }

    // --- The same locked-row contract applies to the trigger picker ---------

    @Test
    fun triggerPicker_permissionRequired_rowStaysVisibleAndRoutesToGrantFlow() {
        var toggles = 0
        var blocked = 0
        composeRule.setContent {
            MaterialTheme {
                TriggerOptionRow(
                    type = com.nexaflow.domain.models.TriggerType.DARK_MODE,
                    checked = false,
                    onSelect = { toggles++ },
                    availability = BuilderOptionAvailability.PERMISSION_REQUIRED,
                    onBlockedClick = { blocked++ }
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(com.nexaflow.domain.models.TriggerType.DARK_MODE.labelRes())
        ).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.elevated_status_available))
            .assertIsDisplayed()

        composeRule.onNodeWithText(
            context.getString(com.nexaflow.domain.models.TriggerType.DARK_MODE.labelRes())
        ).performClick()
        composeRule.waitForIdle()

        assertEquals(0, toggles)
        assertEquals(1, blocked)
    }
}
