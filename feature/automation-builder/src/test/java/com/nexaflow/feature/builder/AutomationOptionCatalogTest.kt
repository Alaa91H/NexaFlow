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
