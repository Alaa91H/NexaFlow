package com.nexaflow.feature.builder

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationOptionCatalogTest {

    @Test
    fun commonTriggers_keepRecommendationOrder_andNeverAddUnsupportedTypes() {
        val supported = setOf(
            TriggerType.APPLICATION,
            TriggerType.TIME,
            TriggerType.WEBHOOK,
            TriggerType.CHARGER
        )

        val result = AutomationOptionCatalog.commonTriggers(supported)

        assertEquals(
            listOf(TriggerType.TIME, TriggerType.APPLICATION, TriggerType.CHARGER),
            result
        )
        assertFalse(result.contains(TriggerType.BATTERY))
        assertFalse(result.contains(TriggerType.WEBHOOK))
    }

    @Test
    fun allSupportedNonCommonTriggers_remainAvailableFromBrowse() {
        val supported = listOf(
            TriggerType.TIME,
            TriggerType.WEBHOOK,
            TriggerType.ROM_SETTING,
            TriggerType.NOTIFICATION
        )

        val result = AutomationOptionCatalog.browseTriggers(supported)

        assertEquals(
            listOf(TriggerType.WEBHOOK, TriggerType.ROM_SETTING, TriggerType.NOTIFICATION),
            result
        )
        assertTrue(result.all { it in supported })
    }

    @Test
    fun commonActions_reusesExistingOptionMetadataInRecurringRoutineOrder() {
        val media = option(ActionType.SYSTEM_MEDIA_PLAY_PAUSE)
        val dnd = option(ActionType.SYSTEM_DND)
        val timer = option(ActionType.SYSTEM_SET_TIMER)
        val root = option(ActionType.ADVANCED_ROOT)

        val result = AutomationOptionCatalog.commonActions(listOf(root, timer, dnd, media))

        assertEquals(listOf(media, dnd, timer), result)
        assertTrue(result.all { it === media || it === dnd || it === timer })
    }

    @Test
    fun recurringRoutineCategory_usesCentralOrder_andKeepsNativeOptionMetadata() {
        val media = option(ActionType.SYSTEM_MEDIA_PLAY_PAUSE).copy(category = ActionCategory.MEDIA)
        val timer = option(ActionType.SYSTEM_SET_TIMER).copy(category = ActionCategory.SYSTEM)
        val playUpdate = option(ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS).copy(category = ActionCategory.APPS)
        val update = option(ActionType.SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS).copy(category = ActionCategory.SYSTEM)
        val root = option(ActionType.ADVANCED_ROOT)

        val result = optionsForActionCategory(
            ActionCategory.ROUTINES,
            listOf(root, update, playUpdate, timer, media)
        )

        assertEquals(listOf(media, timer, playUpdate, update), result)
        assertEquals(ActionCategory.MEDIA, result.first().category)
        assertEquals(ActionCategory.APPS, result[2].category)
        assertTrue(result.filterIndexed { index, _ -> index != 0 && index != 2 }.all { it.category == ActionCategory.SYSTEM })
    }

    @Test
    fun advancedActions_neverAppearInCommonList_butStayBrowsable() {
        val root = option(ActionType.ADVANCED_ROOT)
        val dnd = option(ActionType.SYSTEM_DND)

        val common = AutomationOptionCatalog.commonActions(listOf(root, dnd))
        val browse = AutomationOptionCatalog.browseActions(listOf(root, dnd))

        assertEquals(listOf(dnd), common)
        assertEquals(listOf(root), browse)
        assertEquals(OptionTier.ADVANCED, AutomationOptionCatalog.actionTier(ActionType.ADVANCED_ROOT))
    }

    private fun option(type: ActionType) = ActionOption(
        titleRes = android.R.string.ok,
        subtitleRes = android.R.string.ok,
        icon = Icons.Filled.Settings,
        actionType = type,
        category = ActionCategory.SYSTEM
    )
}
