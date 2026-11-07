package com.metcode

import android.content.Context

/**
 * Stores the SYNOP (IIiii) and METAR (ICAO) codes the observer enters for
 * each station. The app's station database has names/height/latitude but
 * never had official codes, so these are entered once per station and
 * remembered locally -- no attempt is made to guess or pre-fill them.
 */
class StationCodeStore(context: Context) {

    private val prefs = context.getSharedPreferences("station_codes", Context.MODE_PRIVATE)

    fun getSynopCode(stationName: String): String =
        prefs.getString(key(stationName, "synop"), "") ?: ""

    fun getMetarCode(stationName: String): String =
        prefs.getString(key(stationName, "metar"), "") ?: ""

    fun setSynopCode(stationName: String, code: String) {
        prefs.edit().putString(key(stationName, "synop"), code.trim()).apply()
    }

    fun setMetarCode(stationName: String, code: String) {
        prefs.edit().putString(key(stationName, "metar"), code.trim().uppercase()).apply()
    }

    private fun key(stationName: String, field: String) = "$stationName|$field"
}
