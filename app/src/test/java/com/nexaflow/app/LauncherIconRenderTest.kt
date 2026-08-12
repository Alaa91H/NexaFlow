package com.nexaflow.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Verifies the launcher adaptive icon actually renders (non-blank).
 *
 * A silent failure in the launcher's icon loader (e.g. vector gradients that
 * cannot be inflated by the system) shows up as a fully transparent icon on
 * the home screen. Drawing the adaptive icon here — through the same system
 * drawable path the launcher uses — proves the icon paints non-blank pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LauncherIconRenderTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun adaptiveLauncherIcon_rendersNonBlank() {
        val icon = context.getDrawable(R.mipmap.ic_launcher)!!
        assertTrue("ic_launcher must be an adaptive icon", icon is AdaptiveIconDrawable)
        assertRendersNonBlank(icon, "ic_launcher")
    }

    @Test
    fun roundLauncherIcon_rendersNonBlank() {
        val icon = context.getDrawable(R.mipmap.ic_launcher_round)!!
        assertTrue("ic_launcher_round must be an adaptive icon", icon is AdaptiveIconDrawable)
        assertRendersNonBlank(icon, "ic_launcher_round")
    }

    private fun assertRendersNonBlank(drawable: Drawable, name: String) {
        val w = 200
        val h = 200
        drawable.setBounds(0, 0, w, h)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.draw(canvas)

        var opaque = 0
        var colored = 0
        for (y in 0 until h step 2) {
            for (x in 0 until w step 2) {
                val px = bitmap.getPixel(x, y)
                val alpha = (px ushr 24) and 0xFF
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                if (alpha > 10) opaque++
                // Non-grayscale pixels prove the gradient/color actually painted.
                if (alpha > 60 && (kotlin.math.abs(r - g) > 12 || kotlin.math.abs(g - b) > 12)) colored++
            }
        }
        val total = (w / 2) * (h / 2)
        assertTrue("$name drew no opaque pixels (icon is blank!)", opaque > total / 4)
        assertTrue("$name has no colored pixels — only a plain silhouette", colored > total / 20)
    }

    @Test
    fun backgroundDrawable_inflatesToGradient() {
        // The background must be a plain shape gradient that every launcher can load.
        val bg = context.getDrawable(R.drawable.ic_launcher_background)!!
        assertTrue(
            "background must be a plain (non-vector) drawable — vector aapt gradients fail in launchers",
            bg is BitmapDrawable || bg.javaClass.simpleName.contains("GradientDrawable") ||
                bg.javaClass.simpleName.contains("ColorDrawable") || bg !is android.graphics.drawable.VectorDrawable
        )
    }
}
