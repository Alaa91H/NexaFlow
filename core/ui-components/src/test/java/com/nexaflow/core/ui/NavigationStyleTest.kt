package com.nexaflow.core.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the adaptive navigation decision (F2) and the spacing tokens (F1). */
class NavigationStyleTest {

    @Test
    fun compactWidth_usesBottomBar_onTopLevel() {
        assertEquals(
            NavigationStyle.BOTTOM_BAR,
            navigationStyleFor(WindowWidthSizeClass.Compact, isTopLevel = true)
        )
    }

    @Test
    fun mediumWidth_usesBottomBar_onTopLevel() {
        assertEquals(
            NavigationStyle.BOTTOM_BAR,
            navigationStyleFor(WindowWidthSizeClass.Medium, isTopLevel = true)
        )
    }

    @Test
    fun expandedWidth_usesRail_onTopLevel() {
        assertEquals(
            NavigationStyle.RAIL,
            navigationStyleFor(WindowWidthSizeClass.Expanded, isTopLevel = true)
        )
    }

    @Test
    fun nonTopLevel_neverShowsChrome_evenExpanded() {
        assertEquals(
            NavigationStyle.NONE,
            navigationStyleFor(WindowWidthSizeClass.Expanded, isTopLevel = false)
        )
        assertEquals(
            NavigationStyle.NONE,
            navigationStyleFor(WindowWidthSizeClass.Compact, isTopLevel = false)
        )
    }

    @Test
    fun spacingTokens_follow4dpGrid() {
        // M3 spacing scale is a 4dp grid.
        val expected = listOf(4, 8, 12, 16, 20, 24, 32, 40, 48, 56)
        val actual = listOf(
            Dimens.Space1, Dimens.Space2, Dimens.Space3, Dimens.Space4,
            Dimens.Space5, Dimens.Space6, Dimens.Space8, Dimens.Space10,
            Dimens.Space12, Dimens.Space14
        )
        expected.zip(actual).forEach { (dp, token) ->
            assertTrue(
                "token must equal ${dp}dp but was $token",
                token.value == dp.toFloat()
            )
        }
    }

    @Test
    fun componentDimensions_arePositive() {
        assertTrue(Dimens.IconBadgeSmall > 0)
        assertTrue(Dimens.IconBadgeLarge > Dimens.IconBadgeSmall)
        assertTrue(Dimens.RowIconSize == 40)
        assertTrue(Dimens.NavigationRailWidth.value == 80f)
        assertTrue(Dimens.MaxContentWidth.value > 0f)
    }
}
