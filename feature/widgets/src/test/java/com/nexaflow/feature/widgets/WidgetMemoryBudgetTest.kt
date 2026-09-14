package com.nexaflow.feature.widgets

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the Android 17 RemoteViews Bitmap+Icon memory-budget math: an update
 * whose bitmaps exceed `1.5 x (screenW x screenH x 4)` bytes is a fatal
 * IllegalArgumentException, so every dynamically rendered bitmap must be
 * verified against the budget before it is attached to a tile or widget.
 *
 * The guard tests need real Bitmap allocation, so they run under Robolectric
 * at the app's targetSdk.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetMemoryBudgetTest {

    @Test
    fun `budget matches the platform formula`() {
        // 1.5 x (1080 x 2400 x 4) = 15_552_000 bytes.
        assertEquals(15_552_000L, WidgetMemoryBudget.budgetBytes(1080, 2400))
    }

    @Test
    fun `budget is zero for degenerate screens`() {
        assertEquals(0L, WidgetMemoryBudget.budgetBytes(0, 2400))
        assertEquals(0L, WidgetMemoryBudget.budgetBytes(1080, -1))
    }

    @Test
    fun `bitmap bytes use per-pixel config sizes`() {
        assertEquals(36_864L, WidgetMemoryBudget.bitmapBytes("ARGB_8888", 96, 96))
        assertEquals(18_432L, WidgetMemoryBudget.bitmapBytes("RGB_565", 96, 96))
        assertEquals(9_216L, WidgetMemoryBudget.bitmapBytes("ALPHA_8", 96, 96))
        // Unknown configs assume the 4-byte worst case; never under-count.
        assertEquals(36_864L, WidgetMemoryBudget.bitmapBytes("HARDWARE", 96, 96))
        assertEquals(36_864L, WidgetMemoryBudget.bitmapBytes(null, 96, 96))
    }

    @Test
    fun `fitScale is one when the bitmap already fits`() {
        val budget = WidgetMemoryBudget.budgetBytes(1080, 2400)
        assertEquals(1.0f, WidgetMemoryBudget.fitScale(36_864L, budget), 0.0f)
    }

    @Test
    fun `fitScale downscales square-root-wise for over-budget bitmaps`() {
        // Bitmap is 4x the budget → scale = sqrt(1/4) = 0.5.
        val scale = WidgetMemoryBudget.fitScale(4 * 15_552_000L, 15_552_000L)
        assertEquals(0.5f, scale, 0.0001f)
    }

    @Test
    fun `fitScale never under-cuts the legibility floor`() {
        // A bitmap 10,000x the budget would compute a near-zero scale.
        val scale = WidgetMemoryBudget.fitScale(155_520_000_000L, 15_552_000L)
        assertEquals(0.05f, scale, 0.0f)
    }

    @Test
    fun `fitScale treats a zero budget as no-op`() {
        assertEquals(1.0f, WidgetMemoryBudget.fitScale(999L, 0L), 0.0f)
    }

    @Test
    fun `guard returns the same instance when the bitmap fits`() {
        val fits = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val result = WidgetMemoryBudget.guard(fits, 1080, 2400)
        assertSame(fits, result)
    }

    @Test
    fun `guard downscales an over-budget bitmap to fit`() {
        // A 4000x4000 ARGB_8888 bitmap is 64 MB — far over any phone budget.
        val huge = Bitmap.createBitmap(4000, 4000, Bitmap.Config.ARGB_8888)
        val budget = WidgetMemoryBudget.budgetBytes(1080, 2400)
        val guarded = WidgetMemoryBudget.guard(huge, 1080, 2400)
        assertTrue("guarded bitmap must be smaller than the original", guarded !== huge)
        assertTrue(
            "guarded bitmap must fit the budget",
            WidgetMemoryBudget.bitmapBytes("ARGB_8888", guarded.width, guarded.height) <= budget
        )
    }
}
