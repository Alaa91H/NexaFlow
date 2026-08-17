package com.nexaflow.feature.builder

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 * Compose UI tests (Robolectric) for [SpecialPermissionStatusRow] — the live
 * Samsung-style permission badge inside action/trigger cards. The test seam
 * [SpecialPermissionStatusRow.probe] pins each visual state without touching
 * the real root/Shizuku/settings probes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Compose-UI tests stay pinned to SDK 35: Espresso 3.7.0's
// InputManagerEventInjectionStrategy reflectively calls the static
// InputManager.getInstance(), which was removed from Robolectric's
// android-all for SDK 37 (AOSP moved to context-based lookup). Every
// non-Compose test in the repo runs on the real SDK 37 via the Java 21
// toolchain; this test only asserts UI semantics, so 35 is sufficient.
@Config(sdk = [35])
class SpecialPermissionStatusRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun setRow(
        special: SpecialPermission = SpecialPermission.WRITE_SETTINGS,
        status: SpecialStatus,
        onRequest: () -> Unit = {}
    ) {
        composeRule.setContent {
            SpecialPermissionStatusRow(
                // Short hint keeps the pill + chevron comfortably inside the
                // small default Robolectric window.
                hintText = "Permission",
                special = special,
                context = context,
                onRequest = onRequest,
                probe = { status }
            )
        }
    }

    // --- GRANTED: the row disappears entirely (no permanent badge) ----------

    @Test
    fun grantedState_hidesTheWholeRow() {
        setRow(status = SpecialStatus.GRANTED)

        // The row exists only to collect a missing permission: once granted it
        // must vanish from the card — no pill, no chevron, no hint text.
        composeRule.onNodeWithTag("special_status_row").assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.elevated_status_granted))
            .assertDoesNotExist()
        composeRule.onNodeWithTag("special_status_chevron").assertDoesNotExist()
    }

    @Test
    fun grantedState_doesNotTriggerOnRequest() {
        var requests = 0
        setRow(status = SpecialStatus.GRANTED, onRequest = { requests++ })

        composeRule.onNodeWithTag("special_status_row").assertDoesNotExist()
        assertEquals(0, requests)
    }

    // --- AVAILABLE: amber pill, tap grants, chevron shown ------------------

    @Test
    fun availableState_showsTapToGrantPill_enablesClick_showsChevron() {
        var requests = 0
        // AVAILABLE only ever occurs for the elevated trio (root/Shizuku/
        // elevated) — binary permissions never report it — so pin ROOT here
        // to exercise a state that actually happens in production.
        setRow(special = SpecialPermission.ROOT, status = SpecialStatus.AVAILABLE, onRequest = { requests++ })

        composeRule.onNodeWithText(context.getString(R.string.elevated_status_available))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("special_status_row").assertIsEnabled()
        // Presence is the contract: the chevron only enters the semantics tree
        // when the row is tappable. The row's clickable merges descendant
        // semantics, so the chevron tag lives only in the unmerged tree.
        composeRule.onNodeWithTag("special_status_chevron", useUnmergedTree = true).assertExists()

        composeRule.onNodeWithTag("special_status_row").performClick()
        composeRule.waitForIdle()

        assertEquals(1, requests)
    }

    // --- NOT_AVAILABLE: binary vs elevated label ---------------------------

    @Test
    fun notAvailable_binaryPermission_showsNotGrantedPill_andChevron() {
        setRow(
            special = SpecialPermission.WRITE_SETTINGS,
            status = SpecialStatus.NOT_AVAILABLE
        )

        composeRule.onNodeWithText(context.getString(R.string.special_status_not_granted))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("special_status_row").assertIsEnabled()
        // The row's clickable merges descendant semantics, so the chevron tag
        // lives only in the unmerged tree.
        composeRule.onNodeWithTag("special_status_chevron", useUnmergedTree = true).assertExists()
    }

    @Test
    fun notAvailable_elevatedPermission_showsNotAvailablePill() {
        setRow(
            special = SpecialPermission.ROOT,
            status = SpecialStatus.NOT_AVAILABLE
        )

        composeRule.onNodeWithText(context.getString(R.string.elevated_status_unavailable))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("special_status_row").assertIsEnabled()
        // The row's clickable merges descendant semantics, so the chevron tag
        // lives only in the unmerged tree.
        composeRule.onNodeWithTag("special_status_chevron", useUnmergedTree = true).assertExists()
    }
}
