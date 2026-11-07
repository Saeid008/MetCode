package com.metcode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

/** Background photos from app/src/main/assets/splash: a different one each time, in file-name order. */
object SplashPhoto {

    /** Next photo, or null when the folder has no photos. */
    fun next(c: Context): Bitmap? {
        return try {
            val names = (c.assets.list("splash") ?: emptyArray())
                .filter { val n = it.lowercase(); n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") }
                .sorted()
            if (names.isEmpty()) return null
            val p = c.getSharedPreferences("metcal_prefs", Context.MODE_PRIVATE)
            val i = p.getInt("splash_i", 0) % names.size
            p.edit().putInt("splash_i", (i + 1) % names.size).apply()
            val path = "splash/" + names[i]
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            val target = c.resources.displayMetrics.widthPixels.coerceAtLeast(720)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= target) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            c.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) { null }
    }

    /** Photo drawable, or the blue gradient when there are no photos. */
    fun background(c: Context): Drawable {
        val b = next(c)
        return if (b != null) BitmapDrawable(c.resources, b)
        else GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF0D47A1.toInt(), 0xFF42A5F5.toInt()))
    }
}
