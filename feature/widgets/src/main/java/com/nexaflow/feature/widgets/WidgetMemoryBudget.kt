package com.nexaflow.feature.widgets

/**
 * Android 17 enforces a combined Bitmap+Icon memory budget on every
 * RemoteViews payload (home-screen widgets, Quick Settings tiles): an update
 * whose bitmaps exceed `1.5 x (screenWidth x screenHeight x 4)` bytes throws a
 * fatal `IllegalArgumentException` instead of dropping the frame. A single
 * oversized dynamically rendered icon can therefore crash the launcher shell.
 *
 * NexaFlow's only dynamic-bitmap payload today is the Quick Settings tile
 * icon rendered in [TaskTileService]. Every generated bitmap must route
 * through [guard] so a future larger canvas, denser config, or a device with
 * a tiny display can never push the payload over the platform limit.
 *
 * The math is pure so it stays unit-testable without Robolectric; callers
 * supply the measured display size.
 */
object WidgetMemoryBudget {

    /**
     * The platform budget in bytes: 1.5 x (screenW x screenH x 4), matching
     * Android 17's RemoteViews Bitmap+Icon limit for an ARGB_8888 full-screen
     * frame times 1.5.
     */
    fun budgetBytes(screenWidthPx: Int, screenHeightPx: Int): Long {
        if (screenWidthPx <= 0 || screenHeightPx <= 0) return 0L
        return 3L * screenWidthPx * screenHeightPx * 2L
    }

    /**
     * In-memory bytes of a bitmap with the given config and size, using the
     * same per-pixel sizes as [android.graphics.Bitmap.getAllocationByteCount]
     * for the configs NexaFlow renders (ARGB_8888 default, RGB_565 and
     * ALPHA_8 covered defensively).
     */
    fun bitmapBytes(configName: String?, width: Int, height: Int): Long {
        if (width <= 0 || height <= 0) return 0L
        val bytesPerPixel = when (configName) {
            "RGB_565" -> 2L
            "ALPHA_8" -> 1L
            // ARGB_8888, HARDWARE and anything unknown: assume the 4-byte
            // worst case so the guard never under-counts.
            else -> 4L
        }
        return bytesPerPixel * width * height
    }

    /**
     * Largest scale factor in (0, 1] that shrinks the bitmap enough to fit
     * [budget]. Returns 1.0 when the bitmap already fits, and a floor of
     * [MIN_SCALE] so an absurdly over-budget bitmap still stays legible
     * (callers must instead reduce config or dimensions upstream).
     */
    fun fitScale(bitmapBytes: Long, budget: Long): Float {
        if (budget <= 0) return 1.0f
        if (bitmapBytes <= budget) return 1.0f
        // Bytes scale with (scale x scale) for uniform downscales.
        val scale = kotlin.math.sqrt(budget.toDouble() / bitmapBytes.toDouble())
        return scale.toFloat().coerceIn(MIN_SCALE, 1.0f)
    }

    /**
     * Verifies an already-created bitmap against the budget and, when it does
     * not fit, returns a downscaled ARGB_8888 copy that does. Returns the
     * original bitmap untouched (same instance) whenever it fits, so the
     * happy path allocates nothing.
     */
    fun guard(
        bitmap: android.graphics.Bitmap,
        screenWidthPx: Int,
        screenHeightPx: Int
    ): android.graphics.Bitmap {
        val budget = budgetBytes(screenWidthPx, screenHeightPx)
        val used = bitmapBytes(bitmap.config?.name, bitmap.width, bitmap.height)
        if (used <= budget) return bitmap
        val scale = fitScale(used, budget)
        val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(MIN_DIMENSION_PX)
        val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(MIN_DIMENSION_PX)
        val scaled = android.graphics.Bitmap.createBitmap(
            scaledWidth,
            scaledHeight,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(scaled)
        canvas.drawBitmap(
            bitmap,
            null,
            android.graphics.RectF(0f, 0f, scaledWidth.toFloat(), scaledHeight.toFloat()),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        )
        return scaled
    }

    private const val MIN_SCALE = 0.05f
    private const val MIN_DIMENSION_PX = 24
}
