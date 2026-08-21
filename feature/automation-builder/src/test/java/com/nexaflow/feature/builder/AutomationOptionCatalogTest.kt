package com.nexaflow.feature.builder

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationOptionCatalogTest {

    @Test
    fun `catalog exposes no common promotion API above search`() {
        val declaredMethodNames = AutomationOptionCatalog::class.java.declaredMethods.map { it.name }

        assertFalse(declaredMethodNames.any { "common" in it.lowercase() })
        assertFalse(declaredMethodNames.any { "browse" in it.lowercase() })
    }

    @Test
    fun `recurring routine category keeps the central order and option metadata`() {
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

    private fun option(type: ActionType) = ActionOption(
        titleRes = android.R.string.ok,
        subtitleRes = android.R.string.ok,
        icon = Icons.Filled.Settings,
        actionType = type,
        category = ActionCategory.SYSTEM
    )
}
