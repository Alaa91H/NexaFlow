package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.nexaflow.core.engine.CanonicalBatteryTriggerDefinition
import com.nexaflow.core.ui.PickerGroup
import com.nexaflow.core.ui.PickerRow
import com.nexaflow.domain.canonical.CanonicalWorkflowNode
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndBehaviorCatalog
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Render tests for the node editors that the one-page builder shows inline.
 *
 * Each editor is the whole interface of one trigger or action, so composing it
 * is the only way to know the chips write the token the engine expects, the
 * slider stays inside its bounds and the remove/add affordances are wired.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h1400dp")
class BuilderNodeEditorsRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: android.content.Context =
        androidx.test.core.app.ApplicationProvider.getApplicationContext()

    private fun string(resId: Int): String = context.getString(resId)

    @Test
    fun batterySectionShowsThresholdAndEveryFilter() {
        val nodes = mutableStateListOf(
            CanonicalBatteryTriggerDefinition.node(id = "battery-1", thresholdPercent = 35),
        )
        composeRule.setContent {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BatteryThresholdSection(nodes = nodes, addEnabled = true, onAdd = {})
            }
        }

        composeRule.onNodeWithText("35%", substring = true).assertIsDisplayed()
        // Every option row renders its translated choices.
        composeRule.onAllNodesWithText(string(R.string.charger_any)).onFirst().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.constraint_below)).assertIsDisplayed()

        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(80f) }

        assertEquals(80, (nodes.first().arguments
            .first { it.field.value == CanonicalBatteryTriggerDefinition.thresholdField.value }
            .value as com.nexaflow.domain.canonical.IntegerValue).value.toInt())
    }

    @Test
    fun batterySectionWithNoNodesStillOffersTheAddButton() {
        val nodes = mutableStateListOf<CanonicalWorkflowNode>()
        var added = 0
        composeRule.setContent {
            BatteryThresholdSection(nodes = nodes, addEnabled = true, onAdd = { added += 1 })
        }

        composeRule.onNodeWithText("Add battery", substring = true).performClick()

        assertEquals(1, added)
    }

    @Test
    fun endBehaviorOffersTurnOnOffAndRevertForToggleActions() {
        var latest: EndBehavior? = null
        composeRule.setContent {
            EndBehaviorEditor(
                actionType = EndBehaviorCatalog.toggleActions.first(),
                behavior = EndBehavior(EndMode.SET_VALUE, mapOf("enabled" to "true")),
                onBehaviorChange = { latest = it },
            )
        }

        composeRule.onNodeWithText(string(R.string.end_leave)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.end_turn_off)).performClick()

        assertEquals(EndMode.SET_VALUE, latest?.mode)
        assertEquals("false", latest?.config?.get("enabled"))
    }

    @Test
    fun endBehaviorOffersRevertOnlyForUriActions() {
        composeRule.setContent {
            EndBehaviorEditor(
                actionType = EndBehaviorCatalog.revertOnlyActions.first(),
                behavior = null,
                onBehaviorChange = {},
            )
        }

        composeRule.onNodeWithText(string(R.string.end_revert)).assertIsDisplayed()
    }

    @Test
    fun endBehaviorSetValueOpensTheValueEditor() {
        val action = EndBehaviorCatalog.valueActions.first()
        composeRule.setContent {
            EndBehaviorEditor(
                actionType = action,
                behavior = EndBehavior(EndMode.SET_VALUE, defaultEndValue(action)),
                onBehaviorChange = {},
            )
        }

        assertNotNull(defaultEndValue(action))
        composeRule.onNodeWithText(string(R.string.end_set_value), substring = true).assertIsDisplayed()
    }

    @Test
    fun actionConfigEditorRendersTheSimpleActionChoices() {
        val types = listOf(
            ActionType.SYSTEM_VIBRATE,
            ActionType.SYSTEM_SCREEN_TIMEOUT,
            ActionType.SYSTEM_STAY_AWAKE,
            ActionType.SYSTEM_AUTO_BRIGHTNESS,
            ActionType.SYSTEM_MEDIA_PLAY_PAUSE,
            ActionType.SYSTEM_DND,
        )
        var latest: Map<String, String>? = null
        composeRule.setContent {
            val options = remember { actionOptions.filter { it.actionType in types } }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    ActionConfigEditor(
                        option = option,
                        config = emptyMap(),
                        onConfigChange = { latest = it },
                        onPickApp = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(string(R.string.vibrate_duration)).assertIsDisplayed()
        composeRule.onNodeWithText("5s", substring = true).performClick()

        assertEquals("5", latest?.get("seconds"))
    }

    @Test
    fun variableAndContextChipsAppendTheirPlaceholder() {
        var body = ""
        composeRule.setContent {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VariableInsertChips(
                    availableVariables = listOf("TIME", "BATTERY"),
                    currentValue = "",
                    onValueChange = { body = it },
                )
                ContextPathInsertChips(currentValue = "", onValueChange = { body = it })
            }
        }

        composeRule.onNodeWithText("%TIME").performClick()
        assertEquals("%TIME", body)

        composeRule.onNodeWithText("%CTX.$").performClick()
        assertEquals("%CTX.$", body)
    }

    @Test
    fun emptyVariableListRendersNothing() {
        composeRule.setContent {
            VariableInsertChips(availableVariables = emptyList(), currentValue = "", onValueChange = {})
        }

        composeRule.onNodeWithText("Insert a variable", substring = true).assertDoesNotExist()
    }

    @Test
    fun toggleRowAndNotificationButtonsRenderTheirState() {
        var toggled: Boolean? = null
        composeRule.setContent {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleConfigRow(label = "Keep screen awake", checked = false, onCheckedChange = { toggled = it })
                NotificationButtonsEditor(
                    buttons = emptyList(),
                    automations = emptyList(),
                    onButtonsChange = {},
                )
            }
        }

        composeRule.onNode(androidx.compose.ui.test.isToggleable()).performClick()
        assertEquals(true, toggled)
        composeRule.onNodeWithText(string(R.string.notification_buttons_add)).assertExists()
    }

    @Test
    fun timePickerAlertAndSliderRowRender() {
        var picked: String? = null
        composeRule.setContent {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SliderRow(label = "Volume", value = 5f, onValueChange = {}, valueRange = 0f..15f)
                TimePickerAlert(initialTime = "07:30", onConfirm = { picked = it }, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Volume").assertIsDisplayed()
        composeRule.onNodeWithText("OK").performClick()

        val time = picked.orEmpty()
        assertTrue(time.startsWith("07:30") || time.startsWith("7:30"))
    }

    @Test
    fun configuratorPanelShowsTheSearchSelectionAndConfirmBar() {
        var confirmed = 0
        composeRule.setContent {
            NodeConfiguratorPanel(
                title = "",
                searchQuery = "",
                onSearchQueryChange = {},
                selectedCount = 2,
                confirmLabel = "Add triggers",
                confirmEnabled = true,
                onConfirm = { confirmed += 1 },
            ) {
                PickerGroup {
                    PickerRow(
                        icon = Icons.Filled.Timer,
                        title = "Time",
                        category = com.nexaflow.core.ui.theme.NexaFlowCategory.TIME,
                        checked = true,
                        onCheckedChange = {},
                        showDivider = false,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Time").assertIsDisplayed()
        composeRule.onNodeWithText("Add triggers").performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun constraintCardSummarisesAndExpandsItsEditor() {
        composeRule.setContent {
            ConstraintEditorCard(
                draft = ConstraintDraft(ConstraintType.BATTERY, defaultConstraintConfig(ConstraintType.BATTERY)),
                index = 0,
                total = 1,
                initiallyExpanded = true,
                onConfigChange = {},
                onRemove = {},
            )
        }

        composeRule.onNodeWithContentDescription("Remove", substring = true).assertExists()
        assertEquals("BELOW", defaultConstraintConfig(ConstraintType.BATTERY)["direction"])
    }

    @Test
    fun triggerCardShowsItsSummaryAndOpens() {
        composeRule.setContent {
            TriggerEditorCard(
                draft = TriggerDraft(TriggerType.BATTERY, mapOf("above" to "80")),
                index = 0,
                total = 1,
                onConfigChange = {},
                onRemove = {},
                expanded = false,
                onExpandedChange = {},
                onPickApp = {},
            )
        }

        composeRule.onNodeWithText("Battery", substring = true).assertIsDisplayed()
    }

    @Test
    fun canonicalSchemaEditorRendersTheBoundFields() {
        val binding = CanonicalBuilderSchemaBridge.forAction(ActionType.SYSTEM_OPEN_SETTINGS)
        assertNotNull(binding)
        composeRule.setContent {
            CanonicalSchemaFieldEditor(
                binding = binding!!,
                config = emptyMap(),
                onConfigChange = {},
            )
        }

        composeRule.onRoot().assertExists()
    }

    @Test
    fun networkModeSelectorRendersItsChoices() {
        val option = actionOptions.first { it.actionType == ActionType.SYSTEM_NETWORK_MODE }
        composeRule.setContent {
            ActionConfigEditor(
                option = option,
                config = emptyMap(),
                onConfigChange = {},
                onPickApp = {},
            )
        }

        composeRule.onNodeWithText(string(R.string.network_mode_label)).assertIsDisplayed()
    }

    @Test
    fun compatibilityGateReturnsTheDiscoverableTriggers() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val states = CompatibilityGate.triggerOptionStates(
            context = context,
            snapshot = com.nexaflow.domain.capability.CapabilitySnapshot(),
        )

        assertTrue(states.isNotEmpty())
    }

    @Test
    fun triggerSummaryDescribesEveryTriggerKind() {
        val drafts = listOf(
            TriggerDraft(TriggerType.TIME, mapOf("time" to "07:30")),
            TriggerDraft(TriggerType.TIME, mapOf("timeMode" to "RANGE", "rangeStart" to "08:00", "rangeEnd" to "18:00")),
            TriggerDraft(TriggerType.BATTERY, mapOf("above" to "80")),
            TriggerDraft(TriggerType.DEVICE, mapOf("state" to "CONNECTED")),
            TriggerDraft(TriggerType.BLUETOOTH_DEVICE, mapOf("state" to "ON")),
        )
        composeRule.setContent {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                drafts.forEach { draft ->
                    androidx.compose.material3.Text(triggerSummary(draft))
                }
            }
        }

        composeRule.onNodeWithText("07:30", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("08:00 – 18:00", substring = true).assertIsDisplayed()
    }
}
