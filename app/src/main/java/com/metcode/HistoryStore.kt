package com.metcode

import android.content.Context
import java.util.Calendar
import java.util.TimeZone

/**
 * Remembers the dry-bulb temperature and QFE of every report you generate,
 * per station and UTC hour, so T12 and the three earlier pressures needed for
 * the pressure tendency can be filled in automatically later.
 */
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    data class Entry(val temp: Double?, val qfe: Double?)

    /** UTC calendar for day-of-month [day] / [hour] in the current UTC month, shifted by [hoursBack]. */
    fun timeKey(day: Int, hour: Int, hoursBack: Int): Pair<String, Calendar> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.MINUTE, 30)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.DAY_OF_MONTH, day.coerceIn(1, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        cal.add(Calendar.HOUR_OF_DAY, -hoursBack)
        val key = "%04d%02d%02d%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.HOUR_OF_DAY)
        )
        return Pair(key, cal)
    }

    fun save(station: String, day: Int, hour: Int, temp: Double?, qfe: Double?) {
        val (key, _) = timeKey(day, hour, 0)
        prefs.edit().putString("$station|$key", "${temp ?: ""};${qfe ?: ""}").apply()
    }

    fun load(station: String, day: Int, hour: Int, hoursBack: Int): Entry? {
        val (key, _) = timeKey(day, hour, hoursBack)
        val raw = prefs.getString("$station|$key", null) ?: return null
        val parts = raw.split(";")
        return Entry(parts.getOrNull(0)?.toDoubleOrNull(), parts.getOrNull(1)?.toDoubleOrNull())
    }

    /** UTC hour (0-23) of the time [hoursBack] before the report. */
    fun hourOf(day: Int, hour: Int, hoursBack: Int): Int = timeKey(day, hour, hoursBack).second.get(Calendar.HOUR_OF_DAY)

    /** Human readable "dd HH:00 UTC" label for the time [hoursBack] before the report. */
    fun label(day: Int, hour: Int, hoursBack: Int): String {
        val (_, cal) = timeKey(day, hour, hoursBack)
        return "%02d/%02d %02d:00 UTC".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.HOUR_OF_DAY))
    }
}
