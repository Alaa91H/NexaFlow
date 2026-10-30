package com.nexaflow.feature.builder

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drive tests for the TIME trigger body ([TimeTriggerConfig]).
 *
 * The single-time and time-range flows, and the recurrence editor they share,
 * used to live inline in `TriggerEditorCard` (NFA-19) and had no behaviour
 * test beyond compiling. These tests drive the real chips and fields and
 * assert the config the scheduler reads, including the overnight range hint.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h900dp")
class TriggerTimeEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun string(resId: Int, vararg formatArgs: Any): String =
        context.getString(resId, *formatArgs)

    private fun assertText(resId: Int, vararg formatArgs: Any) {
        composeRule.onAllNodesWithText(string(resId, *formatArgs)).onFirst().assertExists()
    }

    private fun host(
        initial: Map<String, String> = emptyMap(),
        onDraft: (TriggerDraft) -> Unit = {},
        onPickTime: (String) -> Unit = {},
        onPickDate: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            var draft by remember { mutableStateOf(TriggerDraft(TriggerType.TIME, initial)) }
            TimeTriggerConfig(
                draft = draft,
                onConfigChange = { updated ->
                    draft = updated
                    onDraft(updated)
                },
                onPickTime = onPickTime,
                onPickDate = onPickDate,
            )
        }
    }

    @Test
    fun singleModeShowsTheTimeFieldAndOpensThePicker() {
        val picks = mutableListOf<String>()
        host(onPickTime = { picks += it })

        assertText(R.string.trigger_time)
        composeRule.onAllNodesWithText("08:00").onFirst().performClick()

        assertEquals(listOf("time"), picks)
    }

    @Test
    fun switchingToRangeWritesBothEndpointDefaults() {
        var latest: TriggerDraft? = null
        host(onDraft = { latest = it })

        composeRule.onAllNodesWithText(string(R.string.time_range)).onFirst().performClick()
        composeRule.waitForIdle()

        assertEquals("RANGE", latest?.config?.get("timeMode"))
        assertEquals("08:00", latest?.config?.get("rangeStart"))
        assertEquals("18:00", latest?.config?.get("rangeEnd"))
    }

    @Test
    fun switchingBackToSingleKeepsTheRangeKeysUntouched() {
        var latest: TriggerDraft? = null
        host(
            initial = mapOf("timeMode" to "RANGE", "rangeStart" to "22:00", "rangeEnd" to "06:00"),
            onDraft = { latest = it },
        )

        composeRule.onAllNodesWithText(string(R.string.time_single)).onFirst().performClick()
        composeRule.waitForIdle()

        assertEquals("SINGLE", latest?.config?.get("timeMode"))
        assertEquals("22:00", latest?.config?.get("rangeStart"))
    }

    @Test
    fun overnightRangeSaysSoAndOpensTheEndPickers() {
        val picks = mutableListOf<String>()
        host(
            initial = mapOf("timeMode" to "RANGE", "rangeStart" to "22:00", "rangeEnd" to "06:00"),
            onPickTime = { picks += it },
        )

        assertText(R.string.range_overnight)
        composeRule.onAllNodesWithText("22:00").onFirst().performClick()
        composeRule.onAllNodesWithText("06:00").onFirst().performClick()

        assertEquals(listOf("rangeStart", "rangeEnd"), picks)
    }

    @Test
    fun sameDayRangeSaysItStaysOnOneDay() {
        host(initial = mapOf("timeMode" to "RANGE", "rangeStart" to "08:00", "rangeEnd" to "18:00"))

        assertText(R.string.range_same_day)
        composeRule.onAllNodesWithText(string(R.string.range_overnight)).assertCountEquals(0)
    }

    @Test
    fun onceRepeatDropsTheIntervalEndOptions() {
        var latest: TriggerDraft? = null
        host(
            initial = mapOf(
                "repeat" to "INTERVAL",
                "endMode" to "AFTER_OCCURRENCES",
                "endCount" to "3",
            ),
            onDraft = { latest = it },
        )

        composeRule.onAllNodesWithText(string(R.string.repeat_once)).onFirst().performClick()
        composeRule.waitForIdle()

        val config = latest!!.config
        assertEquals("ONCE", config["repeat"])
        assertFalse(config.containsKey("endMode"))
        assertFalse(config.containsKey("endCount"))
    }
}
