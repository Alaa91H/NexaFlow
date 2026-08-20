package com.nexaflow.feature.dashboard

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScheduledTaskCardColorTest {

    @Test
    fun lightCardsAlternateBetweenTwoDefinedGraySurfaces() {
        val first = scheduledTaskCardColor(index = 0, darkTheme = false)
        val second = scheduledTaskCardColor(index = 1, darkTheme = false)

        assertEquals(Color(0xFFE7E7E7), first)
        assertEquals(Color(0xFFD3D3D3), second)
        assertNotEquals(first, second)
        assertEquals(first, scheduledTaskCardColor(index = 2, darkTheme = false))
    }

    @Test
    fun darkCardsAlternateBetweenTwoDefinedDarkGraySurfaces() {
        val first = scheduledTaskCardColor(index = 0, darkTheme = true)
        val second = scheduledTaskCardColor(index = 1, darkTheme = true)

        assertEquals(Color(0xFF363636), first)
        assertEquals(Color(0xFF252525), second)
        assertNotEquals(first, second)
        assertEquals(second, scheduledTaskCardColor(index = 3, darkTheme = true))
    }
}
