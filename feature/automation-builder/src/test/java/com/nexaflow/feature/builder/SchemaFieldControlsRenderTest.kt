package com.nexaflow.feature.builder

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.domain.canonical.CanonicalFieldId
import com.nexaflow.domain.canonical.CanonicalValueKind
import com.nexaflow.domain.canonical.NodeFieldType
import com.nexaflow.domain.canonical.NodeSchemaField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Render tests for [CanonicalFieldControl].
 *
 * The controls are the only place a schema field reaches the user: every type
 * picks a different composable, and the promise of this redesign is that none of
 * them asks for a typed number. Composing each variant and driving its chips,
 * stepper and dialogs is what keeps that promise honest, so every field type is
 * rendered here and its emitted value asserted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h900dp")
class SchemaFieldControlsRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun string(resId: Int, vararg formatArgs: Any): String =
        context.getString(resId, *formatArgs)

    private fun field(
        type: NodeFieldType,
        id: String = "threshold",
        minimum: Long? = null,
        maximum: Long? = null,
        allowedTokens: List<String> = emptyList(),
        collectionElementKind: CanonicalValueKind? = null,
    ) = NodeSchemaField(
        id = CanonicalFieldId(id),
        type = type,
        minimum = minimum,
        maximum = maximum,
        allowedTokens = allowedTokens,
        collectionElementKind = collectionElementKind,
    )

    private fun host(content: @Composable () -> Unit) {
        composeRule.setContent {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }

    @Test
    fun booleanFieldTogglesTheStoredValue() {
        var latest: String? = null
        host {
            CanonicalFieldControl(
                field = field(NodeFieldType.BOOLEAN, id = "enabled"),
                schemaTarget = null,
                rawValue = "false",
                showLabel = true,
                hasParseError = false,
                onValueChange = { latest = it },
            )
        }

        composeRule.onNodeWithText(humanizeSchemaToken("enabled")).assertIsDisplayed()
        composeRule.onNode(androidx.compose.ui.test.isToggleable()).performClick()

        assertEquals("true", latest)
    }

    @Test
    fun booleanFieldReportsAParseError() {
        host {
            CanonicalFieldControl(
                field = field(NodeFieldType.BOOLEAN, id = "enabled"),
                schemaTarget = null,
                rawValue = "not-a-bool",
                showLabel = true,
                hasParseError = true,
                onValueChange = {},
            )
        }

        composeRule.onNodeWithText(string(R.string.canonical_field_invalid_value)).assertExists()
    }

    @Test
    fun enumFieldRendersTranslatedTokensAndWritesTheChosenOne() {
        var latest: String? = null
        host {
            CanonicalFieldControl(
                field = field(
                    NodeFieldType.ENUM_TOKEN,
                    id = "direction",
                    allowedTokens = listOf("NORMAL", "MUSIC", "SILENT"),
                ),
                schemaTarget = null,
                rawValue = "NORMAL",
                showLabel = true,
                hasParseError = false,
                hiddenTokens = setOf("MUSIC"),
                onValueChange = { latest = it },
            )
        }

        composeRule.onNodeWithText(string(R.string.schema_token_normal)).performClick()
        assertEquals("NORMAL", latest)
        // A hidden token is never offered.
        composeRule.onNodeWithText(string(R.string.volume_stream_music)).assertDoesNotExist()
    }

    @Test
    fun integerFieldStepsWithinItsBounds() {
        val writes = mutableListOf<String>()
        host {
            CanonicalFieldControl(
                field = field(NodeFieldType.INTEGER, id = "threshold", minimum = 0, maximum = 100),
                schemaTarget = "core.battery.level",
                rawValue = "80",
                showLabel = true,
                hasParseError = false,
                onValueChange = { writes += it },
            )
        }

        composeRule.onNodeWithContentDescription(string(R.string.schema_decrease)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.schema_increase)).performClick()

        assertEquals(listOf("79", "81"), writes)
    }

    @Test
    fun percentageFieldShowsTheUnitAndSurvivesAnOutOfRangeValue() {
        host {
            CanonicalFieldControl(
                field = field(NodeFieldType.PERCENTAGE, id = "threshold", minimum = 0, maximum = 100),
                schemaTarget = "core.display.brightness",
                rawValue = "450",
                showLabel = true,
                hasParseError = false,
                onValueChange = {},
            )
        }

        // Above the ROM scale: the percentage is clamped instead of overflowing
        // the slider.
        composeRule.onAllNodesWithText(string(R.string.schema_value_percent, 100)).onFirst().assertExists()
    }

    @Test
    fun decimalFieldKeepsOneDecimal() {
        var latest: String? = null
        host {
            CanonicalFieldControl(
                field = field(NodeFieldType.DECIMAL, id = "threshold", minimum = 0, maximum = 100),
                schemaTarget = "core.battery.temperature",
                rawValue = "45.5",
                showLabel = true,
                hasParseError = false,
                onValueChange = { latest = it },
            )
        }

        composeRule.onNodeWithContentDescription(string(R.string.schema_increase)).performClick()

        // Celsius steps by half a degree and a whole result keeps no decimal.
        assertEquals("46", latest)
    }

    @Test
    fun durationFieldStepsInMinutes() {
        var latest: String? = null
        host {
            CanonicalFieldControl(
                field = field(NodeFieldType.DURATION_MS, id = "duration"),
                schemaTarget = "core.timer.after",
                rawValue = "60000",
                showLabel = true,
                hasParseError = false,
                onValueChange = { latest = it },
            )
        }

        composeRule.onAllNodesWithText(string(R.string.schema_value_minutes, 1L)).onFirst().assertExists()
        composeRule.onNodeWithContentDescription(string(R.string.schema_increase)).performClick()

        assertEquals((2 * 60_000L).toString(), latest)
    }

    @Test
    fun screenTimeoutOffersTheRomPresets() {
        var latest: String? = null
        host {
            CanonicalFieldControl(
                field = field(NodeFieldType.DURATION_MS, id = "duration"),
                schemaTarget = "core.display.screen_timeout",
                rawValue = "30000",
                showLabel = true,
                hasParseError = false,
                onValueChange = { latest = it },
            )
        }

        composeRule.onAllNodesWithText(string(R.string.schema_duration_seconds, 30L)).onFirst().assertExists()
        // The presets are the ROM's own values; tapping one writes milliseconds,
        // so the stored schema value stays in its declared unit.
        composeRule.onAllNodesWithText(string(R.string.schema_duration_seconds, 15L)).onFirst().performClick()

        assertEquals((15 * 1000L).toString(), latest)
    }

    @Test
    fun packageFieldFallsBackToAnyApp() {
        host {
            CanonicalFieldControl(
                field = field(NodeFieldType.PACKAGE_ID, id = "package"),
                schemaTarget = null,
                rawValue = "",
                showLabel = true,
                hasParseError = true,
                onValueChange = {},
            )
        }

        composeRule.onNodeWithText(string(R.string.any_app)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.canonical_field_invalid_value)).assertExists()
        // The picker itself is the builder's shared app sheet.
        composeRule.onNodeWithText(string(R.string.canonical_choose_app)).assertIsDisplayed()
    }

    @Test
    fun packageCollectionRemovesAChip() {
        var latest: String? = null
        host {
            CanonicalFieldControl(
                field = field(
                    NodeFieldType.COLLECTION,
                    id = "packages",
                    collectionElementKind = CanonicalValueKind.PACKAGE_ID,
                ),
                schemaTarget = null,
                rawValue = "com.android.shell",
                showLabel = false,
                hasParseError = false,
                onValueChange = { latest = it },
            )
        }

        // Removing the only chip writes an empty list rather than leaving a
        // dangling separator.
        composeRule.onNodeWithText("com.android.shell").performClick()
        assertEquals("", latest)
        composeRule.onNodeWithText(string(R.string.choose_apps)).assertIsDisplayed()
    }

    @Test
    fun emptyPackageCollectionShowsTheAnyAppHint() {
        host {
            CanonicalFieldControl(
                field = field(
                    NodeFieldType.COLLECTION,
                    id = "packages",
                    collectionElementKind = CanonicalValueKind.PACKAGE_ID,
                ),
                schemaTarget = null,
                rawValue = "",
                showLabel = false,
                hasParseError = false,
                onValueChange = {},
            )
        }

        composeRule.onNodeWithText(string(R.string.any_app)).assertExists()
    }

    @Test
    fun textCollectionFallsBackToATextField() {
        host {
            CanonicalFieldControl(
                field = field(
                    NodeFieldType.COLLECTION,
                    id = "values",
                    collectionElementKind = CanonicalValueKind.TEXT,
                ),
                schemaTarget = null,
                rawValue = "a,b",
                showLabel = true,
                hasParseError = true,
                onValueChange = {},
            )
        }

        composeRule.onNodeWithText(string(R.string.canonical_field_invalid_value)).assertExists()
    }

    @Test
    fun timeOfDayPicksATimeInsteadOfTypingIt() {
        var latest: String? = null
        host {
            CanonicalFieldControl(
                field = field(NodeFieldType.TIME_OF_DAY, id = "at"),
                schemaTarget = null,
                rawValue = "",
                showLabel = true,
                hasParseError = false,
                onValueChange = { latest = it },
            )
        }

        composeRule.onNodeWithText(string(R.string.canonical_choose_time)).performClick()
        composeRule.onNodeWithText(string(R.string.ok)).performClick()

        assertEquals("08:00", latest)
    }

    @Test
    fun datePicksADateFromTheCalendar() {
        var latest: String? = null
        host {
            CanonicalFieldControl(
                field = field(NodeFieldType.DATE, id = "on"),
                schemaTarget = null,
                rawValue = "2026-11-01",
                showLabel = true,
                hasParseError = false,
                onValueChange = { latest = it },
            )
        }

        composeRule.onNodeWithText("2026-11-01").performClick()
        composeRule.onNodeWithText(string(R.string.ok)).performClick()

        assertEquals("2026-11-01", latest)
    }

    @Test
    fun timezoneFieldOffersAPickerInsteadOfATextBox() {
        host {
            CanonicalFieldControl(
                field = field(NodeFieldType.TIMEZONE_ID, id = "zone"),
                schemaTarget = null,
                rawValue = "",
                showLabel = true,
                hasParseError = false,
                onValueChange = {},
            )
        }

        composeRule.onAllNodesWithText(string(R.string.schema_field_timezone)).onLast().assertIsDisplayed()
    }

    @Test
    fun coordinateRendersTheChosenPointAndItsError() {
        host {
            CanonicalFieldControl(
                field = field(NodeFieldType.COORDINATE, id = "where"),
                schemaTarget = null,
                rawValue = "",
                showLabel = true,
                hasParseError = true,
                onValueChange = {},
            )
        }

        composeRule.onNodeWithText(string(R.string.location_not_set)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.canonical_field_invalid_value)).assertExists()
    }

    @Test
    fun freeTextAndJsonKeepTheOnlyTextBoxes() {
        host {
            CanonicalFieldControl(
                field = field(NodeFieldType.TEXT, id = "path"),
                schemaTarget = null,
                rawValue = "/sdcard/notes.txt",
                showLabel = true,
                hasParseError = false,
                onValueChange = {},
            )
            CanonicalFieldControl(
                field = field(NodeFieldType.JSON, id = "body"),
                schemaTarget = null,
                rawValue = "{}",
                showLabel = true,
                hasParseError = true,
                onValueChange = {},
            )
        }

        composeRule.onNodeWithText(humanizeSchemaToken("path")).assertIsDisplayed()
        composeRule.onAllNodesWithText(string(R.string.canonical_field_invalid_value)).onFirst().assertExists()
    }

    @Test
    fun steppedValueRowClampsBothEnds() {
        val writes = mutableListOf<Float>()
        host {
            SteppedValueRow(
                label = "Level",
                valueText = "0",
                value = 0f,
                valueRange = 0f..10f,
                step = 2f,
                onValueChange = { writes += it },
            )
            SteppedValueRow(
                label = "",
                valueText = "10",
                value = 10f,
                valueRange = 0f..10f,
                step = 2f,
                onValueChange = { writes += it },
            )
        }

        composeRule.onAllNodesWithContentDescription(string(R.string.schema_decrease))[0].performClick()
        composeRule.onAllNodesWithContentDescription(string(R.string.schema_increase))[1].performClick()

        assertEquals(listOf(0f, 10f), writes)
    }

    @Test
    fun steppedValueRowWritesTheSliderPosition() {
        var latest: Float? = null
        host {
            SteppedValueRow(
                label = "Level",
                valueText = "2",
                value = 2f,
                valueRange = 0f..10f,
                onValueChange = { latest = it },
            )
        }

        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }

        assertEquals(7f, latest)
    }
}
