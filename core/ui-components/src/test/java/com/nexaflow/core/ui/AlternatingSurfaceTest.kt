package com.nexaflow.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AlternatingSurfaceTest {

    @Test
    fun repeatedItemsAlternateGrayAndDarkGrayFromTheirIndex() {
        assertEquals(AlternatingSurfaceTone.GRAY, alternatingSurfaceTone(0))
        assertEquals(AlternatingSurfaceTone.DARK_GRAY, alternatingSurfaceTone(1))
        assertEquals(AlternatingSurfaceTone.GRAY, alternatingSurfaceTone(2))
        assertEquals(AlternatingSurfaceTone.DARK_GRAY, alternatingSurfaceTone(3))
    }

    @Test
    fun alternationRepeatsForLaterItems() {
        assertEquals(AlternatingSurfaceTone.GRAY, alternatingSurfaceTone(8))
        assertEquals(AlternatingSurfaceTone.DARK_GRAY, alternatingSurfaceTone(9))
    }
}
