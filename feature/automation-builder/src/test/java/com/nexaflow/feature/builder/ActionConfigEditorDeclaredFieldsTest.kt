package com.nexaflow.feature.builder

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.rom.RebootMode
import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Render tests for the actions whose declared fields the legacy editor used to
 * replace with Settings namespace/key/value inputs (T02-005).
 *
 * The reboot action declares a typed target and the Play Store action declares a
 * required package; both are driven here through the production composable so
 * the editor keeps writing exactly the keys the runtime reads.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h900dp")
class ActionConfigEditorDeclaredFieldsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun string(resId: Int): String = context.getString(resId)

    private fun option(type: ActionType) = ActionOption(
        titleRes = R.string.action_reboot,
        subtitleRes = R.string.action_reboot_sub,
        icon = Icons.Filled.Tune,
        actionType = type,
        category = ActionCategory.SYSTEM
    )

    private fun editor(type: ActionType, onConfigChange: (Map<String, String>) -> Unit) {
        composeRule.setContent {
            ActionConfigEditor(
                option = option(type),
                config = emptyMap(),
                onConfigChange = onConfigChange,
                onPickApp = {}
            )
        }
    }

    @Test
    fun rebootOffersTheDeclaredTargetsAndWritesTheChosenOne() {
        var latest: Map<String, String>? = null
        editor(ActionType.SYSTEM_REBOOT) { latest = it }

        composeRule.onNodeWithText(string(R.string.reboot_mode)).assertIsDisplayed()
        RebootMode.selectable.forEach { mode ->
            composeRule.onAllNodesWithText(mode).onFirst().assertExists()
        }

        composeRule.onAllNodesWithText(RebootMode.BOOTLOADER).onFirst().performClick()

        assertEquals(RebootMode.BOOTLOADER, latest?.get("mode"))
    }

    @Test
    fun rebootDoesNotAskForSettingsNamespaceOrKey() {
        editor(ActionType.SYSTEM_REBOOT) {}

        composeRule.onAllNodesWithText(string(R.string.setting_namespace)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.setting_key)).assertCountEquals(0)
    }

    @Test
    fun playStoreAsksForThePackageAndNotForASetting() {
        var latest: Map<String, String>? = null
        editor(ActionType.SYSTEM_OPEN_PLAY_STORE_APP) { latest = it }

        composeRule.onNodeWithText(string(R.string.package_name)).assertExists()
        composeRule.onNodeWithText(string(R.string.choose_from_installed)).assertIsDisplayed()
        composeRule.onAllNodesWithText(string(R.string.setting_namespace)).assertCountEquals(0)
        assertNull(latest)
        assertNotNull(context.getString(R.string.setting_namespace))
    }
}
