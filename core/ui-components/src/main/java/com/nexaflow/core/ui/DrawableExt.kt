package com.nexaflow.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Renders any [Drawable] (e.g. an app icon loaded from
 * [android.content.pm.PackageManager]) into a Compose [ImageBitmap].
 * Returns null when the drawable cannot be rasterized.
 */
fun Drawable.toImageBitmapOrNull(): ImageBitmap? {
    return when (this) {
        is BitmapDrawable -> bitmap?.asImageBitmap()
        else -> runCatching {
            val width = intrinsicWidth.coerceAtLeast(1)
            val height = intrinsicHeight.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}
