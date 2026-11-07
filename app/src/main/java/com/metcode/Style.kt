package com.metcode

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView

/** Reads "key = value" lines from a text file in assets (lines starting with # are comments). */
object TextConfig {
    private val cache = HashMap<String, Map<String, String>>()

    fun read(c: Context, file: String): Map<String, String> = cache.getOrPut(file) {
        val m = LinkedHashMap<String, String>()
        try {
            c.assets.open(file).bufferedReader(Charsets.UTF_8).use { r ->
                for (raw in r.lineSequence()) {
                    val l = raw.trimStart('\uFEFF').trim()
                    if (l.isEmpty() || l.startsWith("#")) continue
                    val i = l.indexOf('=')
                    if (i > 0) m[l.substring(0, i).trim()] = l.substring(i + 1).trim()
                }
            }
        } catch (_: Exception) {}
        m
    }
}

/** Font and font sizes, from assets/style.txt. Fonts are .ttf/.otf files in assets/fonts. */
object Style {
    private val fonts = HashMap<String, Typeface?>()

    fun size(c: Context, key: String, def: Float): Float = TextConfig.read(c, "style.txt")[key]?.toFloatOrNull() ?: def

    /** The font chosen for the app language (null = the phone font). */
    fun typeface(c: Context): Typeface? {
        val name = TextConfig.read(c, "style.txt")[if (Lang.isFa(c)) "font_fa" else "font_en"] ?: "default"
        if (name.isEmpty() || name.equals("default", true)) return null
        return fonts.getOrPut(name) { try { Typeface.createFromAsset(c.assets, "fonts/$name") } catch (_: Exception) { null } }
    }

    fun font(c: Context, tv: TextView) {
        val tf = typeface(c) ?: return
        tv.setTypeface(tf, tv.typeface?.style ?: Typeface.NORMAL)
    }

    /** Text typed in boxes and items of dropdown lists. */
    fun styleInput(c: Context, tv: TextView): TextView {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size(c, "size_input", 14f))
        font(c, tv)
        return tv
    }
}
