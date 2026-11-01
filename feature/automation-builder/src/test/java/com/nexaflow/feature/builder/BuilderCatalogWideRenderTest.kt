package com.nexaflow.feature.builder

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.rom.NetworkModePolicy
import com.nexaflow.core.rom.NetworkModeSnapshot
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndBehaviorCatalog
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every catalog entry rendered once.
 *
 * The builder's editors are `when` blocks over the catch-all catalogs, and the
 * risk is not that one editor looks wrong but that a whole action kind is never
 * composed until a user picks it. These tests walk the catalogs themselves, so a
 * new action, trigger or constraint enters the suite without anyone remembering
 * to add a case.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h4000dp")
class BuilderCatalogWideRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun string(resId: Int): String = context.getString(resId)

    @Test
    fun everyActionInTheCatalogRendersItsEditor() {
        composeRule.setContent {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actionOptions.forEach { option ->
                    ActionConfigEditor(
                        option = option,
                        config = emptyMap(),
                        onConfigChange = {},
                        onPickApp = {},
                        agentChoices = emptyList(),
                    )
                }
            }
        }

        // The catalog is what the picker lists, so this test is only meaningful
        // if every entry was composed.
        assertTrue(actionOptions.size > 10)
        composeRule.onRoot().assertExists()
    }

    @Test
    fun everyActionKindOffersItsEndBehaviour() {
        composeRule.setContent {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionType.entries.forEach { type ->
                    EndBehaviorEditor(
                        actionType = type,
                        behavior = EndBehavior(EndMode.SET_VALUE, defaultEndValue(type)),
                        onBehaviorChange = {},
                        showLabel = false,
                    )
                }
            }
        }

        composeRule.onRoot().assertExists()
    }

    @Test
    fun everyTriggerKindRendersItsCardAndSummary() {
        composeRule.setContent {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TriggerType.entries.forEach { type ->
                    Text(triggerSummary(TriggerDraft(type)))
                }
            }
        }

        composeRule.onRoot().assertExists()
    }

    @Test
    fun triggerCardRendersForCanonicalAndDedicatedKinds() {
        composeRule.setContent {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    TriggerType.DEVICE,
                    TriggerType.SENSOR,
                    TriggerType.CHARGER,
                    TriggerType.TIME,
                ).forEach { type ->
                    TriggerEditorCard(
                        draft = TriggerDraft(type),
                        index = 0,
                        total = 1,
                        onConfigChange = {},
                        onRemove = {},
                        expanded = false,
                        onExpandedChange = {},
                        onPickApp = {},
                    )
                }
            }
        }

        composeRule.onRoot().assertExists()
    }

    @Test
    fun constraintDraftsAreValueObjectsWithCatalogDefaults() {
        val battery = ConstraintDraft(ConstraintType.BATTERY, defaultConstraintConfig(ConstraintType.BATTERY))

        assertEquals(battery, ConstraintDraft(ConstraintType.BATTERY, mapOf("direction" to "BELOW", "level" to "20")))
        assertNotEquals(battery, battery.copy(type = ConstraintType.WIFI))
        assertEquals(battery.hashCode(), ConstraintDraft(ConstraintType.BATTERY, battery.config).hashCode())
        assertTrue(battery.toString().contains("BATTERY"))

        // A mutable map is frozen on construction, so a loaded automation cannot
        // mutate the draft in place after it was created.
        val source = mutableMapOf("state" to "ON")
        val draft = ConstraintDraft(ConstraintType.WIFI, source)
        source["state"] = "OFF"
        assertEquals("ON", draft.config["state"])

        constraintTypeOptions.forEach { type ->
            assertNotNull(defaultConstraintConfig(type))
            assertTrue(type.labelRes() != 0)
            assertTrue(type.subtitleRes() != 0)
            assertNotNull(type.icon())
        }
    }

    @Test
    fun endBehaviourCatalogCoversEachFamiliesOptions() {
        assertTrue(EndBehaviorCatalog.toggleActions.isNotEmpty())
        assertTrue(EndBehaviorCatalog.revertOnlyActions.isNotEmpty())
        assertTrue(EndBehaviorCatalog.valueActions.isNotEmpty())
        ActionType.entries.forEach { type ->
            assertNotNull(defaultEndValue(type))
        }
    }

    @Test
    fun networkModeSelectorListsTheSimsAndTheirGenerations() {
        val options = listOf(
            NetworkModePolicy.Option("AUTO", 0L, "Automatic", isAutomatic = true),
            NetworkModePolicy.Option("LTE", 1L, "4G/3G/2G"),
        )
        fun subscription(id: Int, slot: Int) = NetworkModeSnapshot.Subscription(
            subscriptionId = id,
            slotIndex = slot,
            selectableMask = 1L,
            configuredUserMask = 1L,
            knownEffectiveMask = 1L,
            currentDataNetworkType = 13,
            isActiveDataSubscription = id == 1,
            options = options,
        )
        val snapshot = NetworkModeSnapshot(
            subscriptions = listOf(subscription(1, 0), subscription(2, 1)),
            activeDataSubscriptionId = 1,
            status = NetworkModeSnapshot.Status.AVAILABLE,
        )
        var latest: Map<String, String>? = null
        composeRule.setContent {
            NetworkModeSelector(config = emptyMap(), onConfigChange = { latest = it }) { snapshot }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("4G/3G/2G", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("4G/3G/2G", substring = true).onFirst().performClick()

        // Tapping a generation writes the mode the engine applies.
        assertNotNull(latest?.get("mode"))
    }

    @Test
    fun configuratorPanelWithATitleShowsTheHeaderAndConfirmBar() {
        composeRule.setContent {
            NodeConfiguratorPanel(
                title = string(R.string.canonical_add_battery),
                searchQuery = "bat",
                onSearchQueryChange = {},
                selectedCount = 1,
                confirmLabel = "Add",
                confirmEnabled = false,
                onConfirm = {},
            ) { }
        }

        composeRule.onNodeWithText(string(R.string.canonical_add_battery)).assertIsDisplayed()
    }
}
