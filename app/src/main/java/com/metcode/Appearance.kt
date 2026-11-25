package com.metcode

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Dark mode choice: "off", "on" or "system" (use the device settings). */
object Appearance {
    private fun prefs(c: Context) = c.getSharedPreferences("metcode_prefs", Context.MODE_PRIVATE)
    fun get(c: Context): String = prefs(c).getString("night", "system") ?: "system"
    fun set(c: Context, v: String) { prefs(c).edit().putString("night", v).commit() }
    fun apply(c: Context) {
        AppCompatDelegate.setDefaultNightMode(when (get(c)) {
            "on" -> AppCompatDelegate.MODE_NIGHT_YES
            "off" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
    }
}
