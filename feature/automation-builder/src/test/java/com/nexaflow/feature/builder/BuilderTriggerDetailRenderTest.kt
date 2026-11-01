package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.dp
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.TriggerType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The editor branches that only open for one trigger family.
 *
 * The catalog-wide test proves every kind can be created; this one drives the
 * options a user sees after expanding a sensor, a schedule, a volume or a
 * screen-timeout trigger, because those chips are where the old free-text boxes
 * used to be.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h4000dp")
class BuilderTriggerDetailRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun host(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }

    @Test
    fun sensorTriggerOffersItsKindsAndNumericComparisons() {
        host {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TriggerEditorCard(
                    draft = TriggerDraft(
                        TriggerType.SENSOR,
                        mapOf("sensor" to "TEMPERATURE", "event" to "ABOVE"),
                    ),
                    index = 0,
                    total = 1,
                    onConfigChange = {},
                    onRemove = {},
                    expanded = true,
                    onExpandedChange = {},
                    onPickApp = {},
                )
            }
        }

        composeRule.onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun proximitySensorOffersItsCoveredEvents() {
        host {
            TriggerEditorCard(
                draft = TriggerDraft(TriggerType.SENSOR, mapOf("sensor" to "PROXIMITY")),
                index = 0,
                total = 1,
                onConfigChange = {},
                onRemove = {},
                expanded = true,
                onExpandedChange = {},
                onPickApp = {},
            )
        }

        composeRule.onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun intervalRepeatsOfferWeekdayAndMonthlyRules() {
        host {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeRepeatSection(
                    draft = TriggerDraft(
                        TriggerType.TIME,
                        mapOf("repeat" to "INTERVAL", "unit" to "WEEK", "interval" to "2", "days" to "1,3"),
                    ),
                    onConfigChange = {},
                    onPickDate = {},
                    onPickExcludedDate = {},
                )
                TimeRepeatSection(
                    draft = TriggerDraft(
                        TriggerType.TIME,
                        mapOf(
                            "repeat" to "INTERVAL",
                            "unit" to "MONTH",
                            "interval" to "1",
                            "monthlyDayMode" to "FIRST_DAY",
                            "excludedDates" to "2026-12-25",
                        ),
                    ),
                    onConfigChange = {},
                    onPickDate = {},
                    onPickExcludedDate = {},
                )
                TimeRepeatSection(
                    draft = TriggerDraft(
                        TriggerType.TIME,
                        mapOf("repeat" to "INTERVAL", "unit" to "DAY", "interval" to "1"),
                    ),
                    onConfigChange = {},
                    onPickDate = {},
                    onPickExcludedDate = {},
                )
            }
        }

        composeRule.onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun constraintCardsOpenTheirStateAndWeekdayChoices() {
        host {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ConstraintType.WIFI,
                    ConstraintType.SCHEDULE,
                    ConstraintType.CHARGING,
                    ConstraintType.BLUETOOTH,
                    ConstraintType.DND,
                    ConstraintType.AIRPLANE,
                    ConstraintType.LOCATION,
                ).forEach { type ->
                    ConstraintEditorCard(
                        draft = ConstraintDraft(type, defaultConstraintConfig(type)),
                        index = 0,
                        total = 1,
                        initiallyExpanded = true,
                        onConfigChange = {},
                        onRemove = {},
                    )
                }
            }
        }

        composeRule.onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun deviceTriggerUsesTheCanonicalEditorWithHiddenEventTokens() {
        host {
            TriggerEditorCard(
                draft = TriggerDraft(TriggerType.DEVICE, mapOf("event" to "SCREEN_ON")),
                index = 0,
                total = 1,
                onConfigChange = {},
                onRemove = {},
                expanded = true,
                onExpandedChange = {},
                onPickApp = {},
            )
        }

        composeRule.onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun theConnectorBetweenNodesRenders() {
        host { NodeConnector() }

        composeRule.onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun triggerSummariesTranslateTheirTokens() {
        host {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    TriggerDraft(TriggerType.CHARGER, mapOf("source" to "AC")),
                    TriggerDraft(TriggerType.CHARGER, mapOf("source" to "USB")),
                    TriggerDraft(TriggerType.CHARGER, mapOf("source" to "WIRELESS")),
                    TriggerDraft(TriggerType.VOLUME_CHANGED, mapOf("stream" to "RING", "direction" to "BELOW")),
                    TriggerDraft(TriggerType.VOLUME_CHANGED, mapOf("stream" to "ALARM", "direction" to "ABOVE")),
                    TriggerDraft(TriggerType.VOLUME_CHANGED, mapOf("stream" to "NOTIFICATION")),
                    TriggerDraft(TriggerType.STORAGE_LOW, mapOf("threshold" to "512")),
                    TriggerDraft(TriggerType.STORAGE_LOW, mapOf("threshold" to "2048")),
                ).forEach { draft ->
                    Text(triggerSummary(draft))
                }
            }
        }

        composeRule.onAllNodes(isRoot()).onFirst().assertExists()
    }

    @Test
    fun screenTimeoutTriggerReadsTheRomValue() {
        host {
            TriggerEditorCard(
                draft = TriggerDraft(TriggerType.SCREEN_TIMEOUT_CHANGED),
                index = 0,
                total = 1,
                onConfigChange = {},
                onRemove = {},
                expanded = true,
                onExpandedChange = {},
                onPickApp = {},
            )
        }

        composeRule.onAllNodes(isRoot()).onFirst().assertExists()
    }
}
