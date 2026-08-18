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
    fun commonActions_reusesExistingOptionMetadataInRecommendationOrder() {
        val brightness = option(ActionType.SYSTEM_BRIGHTNESS)
        val dnd = option(ActionType.SYSTEM_DND)
        val root = option(ActionType.ADVANCED_ROOT)

        val result = AutomationOptionCatalog.commonActions(listOf(brightness, dnd, root))

        assertEquals(listOf(dnd, brightness), result)
        assertTrue(result.all { it === dnd || it === brightness })
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
