package com.nexaflow.feature.builder

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.schedule.TimeTriggerCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The schedule always supported an explicit IANA zone, but no builder surface
 * wrote the keys the scheduler reads (T02-006). These tests drive the real
 * control and assert the persisted config, including the safe behaviour for a
 * half-typed zone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h900dp")
class TimeZoneSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun string(resId: Int): String = context.getString(resId)

    private fun host(onDraft: (TriggerDraft) -> Unit = {}) {
        composeRule.setContent {
            var draft by remember { mutableStateOf(TriggerDraft(TriggerType.TIME)) }
            TimeZoneSection(
                draft = draft,
                onConfigChange = { updated ->
                    draft = updated
                    onDraft(updated)
                }
            )
        }
    }

    @Test
    fun defaultsToTheDeviceZone() {
        host()

        composeRule.onNodeWithText(string(R.string.time_zone_section)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.time_zone_device)).assertIsDisplayed()
        composeRule.onAllNodesWithText("Europe/Berlin").assertCountEquals(0)
    }

    @Test
    fun selectingAFixedZoneStartsFromAValidZoneAndPersistsIt() {
        var latest: TriggerDraft? = null
        host { latest = it }

        composeRule.onNodeWithText(string(R.string.time_zone_fixed)).performClick()

        val config = latest!!.config
        assertEquals(
            TimeTriggerCalculator.ZONE_POLICY_FIXED_IANA,
            config[TimeTriggerCalculator.ZONE_POLICY_KEY]
        )
        assertEquals(config[TimeTriggerCalculator.ZONE_ID_KEY], config[TimeTriggerCalculator.CANONICAL_ZONE_ID_KEY])
        assertEquals(
            config[TimeTriggerCalculator.ZONE_ID_KEY],
            TimeTriggerCalculator.resolveZone(config, java.time.ZoneId.of("UTC"))?.id
        )
    }

    @Test
    fun quickPickWritesTheZoneAndClearingReturnToTheDeviceZone() {
        var latest: TriggerDraft? = null
        host { latest = it }

        composeRule.onNodeWithText(string(R.string.time_zone_fixed)).performClick()
        composeRule.onAllNodesWithText("Europe/Berlin").onFirst().performClick()
        assertEquals("Europe/Berlin", latest!!.config[TimeTriggerCalculator.ZONE_ID_KEY])
        assertEquals(
            "Europe/Berlin",
            TimeTriggerCalculator.resolveZone(latest.config, java.time.ZoneId.of("UTC"))?.id
        )

        composeRule.onNodeWithText(string(R.string.time_zone_device)).performClick()
        val config = latest.config
        assertEquals(
            TimeTriggerCalculator.ZONE_POLICY_DEVICE_LOCAL,
            config[TimeTriggerCalculator.ZONE_POLICY_KEY]
        )
        assertEquals("", config[TimeTriggerCalculator.ZONE_ID_KEY])
        assertEquals("", config[TimeTriggerCalculator.CANONICAL_ZONE_ID_KEY])
        assertNull(TimeTriggerCalculator.resolveZone(config, java.time.ZoneId.of("UTC"))?.id?.takeIf { it == "Europe/Berlin" })
    }

    @Test
    fun anIncompleteZoneNeverBecomesAFixedZone() {
        var latest: TriggerDraft? = null
        host { latest = it }

        composeRule.onNodeWithText(string(R.string.time_zone_fixed)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("Mars/Olympus")

        val config = latest!!.config
        assertEquals(
            TimeTriggerCalculator.ZONE_POLICY_DEVICE_LOCAL,
            config[TimeTriggerCalculator.ZONE_POLICY_KEY]
        )
        // The draft keeps the typed text so the user can finish it.
        assertEquals("Mars/Olympus", config[TimeTriggerCalculator.ZONE_ID_KEY])
        assertEquals("UTC", TimeTriggerCalculator.resolveZone(config, java.time.ZoneId.of("UTC"))?.id)
        composeRule.onNodeWithText(string(R.string.time_zone_invalid)).assertIsDisplayed()
    }
}
