package com.nexaflow.feature.builder

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationOptionCatalogTest {

    @Test
    fun `common discovery surface stays intentionally small`() {
        assertTrue(AutomationOptionCatalog.commonTriggerOrder.size in 6..8)
        assertTrue(AutomationOptionCatalog.commonActionOrder.size in 6..8)
        assertEquals(
            AutomationOptionCatalog.commonTriggerOrder.distinct().size,
            AutomationOptionCatalog.commonTriggerOrder.size,
        )
        assertEquals(
            AutomationOptionCatalog.commonActionOrder.distinct().size,
            AutomationOptionCatalog.commonActionOrder.size,
        )
    }

    @Test
    fun `every legacy-compatible option has exactly one discovery tier`() {
        TriggerType.entries.forEach { type ->
            assertTrue(AutomationOptionCatalog.tierFor(type) in OptionTier.entries)
        }
        ActionType.entries.forEach { type ->
            assertTrue(AutomationOptionCatalog.tierFor(type) in OptionTier.entries)
        }
        assertTrue(
            AutomationOptionCatalog.commonTriggerOrder.all {
                AutomationOptionCatalog.tierFor(it) == OptionTier.COMMON
            },
        )
        assertTrue(
            AutomationOptionCatalog.commonActionOrder.all {
                AutomationOptionCatalog.tierFor(it) == OptionTier.COMMON
            },
        )
    }

    @Test
    fun `high risk and raw automation surfaces are advanced`() {
        assertEquals(OptionTier.ADVANCED, AutomationOptionCatalog.tierFor(ActionType.ADVANCED_ROOT))
        assertEquals(OptionTier.ADVANCED, AutomationOptionCatalog.tierFor(ActionType.ADVANCED_SHIZUKU))
        assertEquals(OptionTier.ADVANCED, AutomationOptionCatalog.tierFor(ActionType.SYSTEM_HTTP_REQUEST))
        assertEquals(OptionTier.ADVANCED, AutomationOptionCatalog.tierFor(ActionType.SYSTEM_SET_SETTING))
        assertEquals(OptionTier.ADVANCED, AutomationOptionCatalog.tierFor(TriggerType.WEBHOOK))
        assertEquals(OptionTier.ADVANCED, AutomationOptionCatalog.tierFor(TriggerType.ROM_SETTING))
    }

    @Test
    fun `options are filtered by category`() {
        val media = option(ActionType.SYSTEM_MEDIA_PLAY_PAUSE).copy(category = ActionCategory.MEDIA)
        val timer = option(ActionType.SYSTEM_SET_TIMER).copy(category = ActionCategory.SYSTEM)
        val playUpdate = option(ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS).copy(category = ActionCategory.APPS)

        val result = optionsForActionCategory(
            ActionCategory.MEDIA,
            listOf(timer, media, playUpdate)
        )

        assertEquals(listOf(media), result)
        assertEquals(ActionCategory.MEDIA, result.first().category)
    }

    private fun option(type: ActionType) = ActionOption(
        titleRes = android.R.string.ok,
        subtitleRes = android.R.string.ok,
        icon = Icons.Filled.Settings,
        actionType = type,
        category = ActionCategory.SYSTEM
    )
}
