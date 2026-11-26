package com.metcode

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One saved report. [fields] keeps the entries that "Complete report based on last report" copies into the next report. */
class Report(
    val id: Long, var station: String, var year: Int, var month: Int, var day: Int, var hour: Int,
    var synop: String, var metar: String, var dry: Double?, var qfe: Double?, var fields: JSONObject,
    var kind: String = "synop"      // "synop" (SYNOP and METAR) or "minmax" (MinMx report)
) {
    val sortKey: Long get() = ((year * 100L + month) * 100L + day) * 100L + hour
    fun sameTime(o: Report) = kind == o.kind && station == o.station && year == o.year && month == o.month && day == o.day && hour == o.hour
}

/** Saved reports, kept in app storage (reports.json): they stay after closing the app and after updating it. */
object ReportStore {
    private fun file(c: Context) = File(c.filesDir, "reports.json")

    @Synchronized
    fun load(c: Context): MutableList<Report> {
        val out = mutableListOf<Report>()
        try {
            val f = file(c)
            if (!f.exists()) return out
            val a = JSONArray(f.readText(Charsets.UTF_8))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Report(
                    o.getLong("id"), o.getString("station"), o.getInt("y"), o.getInt("m"), o.getInt("d"), o.getInt("h"),
                    o.optString("synop"), o.optString("metar"),
                    if (o.has("dry")) o.getDouble("dry") else null, if (o.has("qfe")) o.getDouble("qfe") else null,
                    o.optJSONObject("f") ?: JSONObject(), o.optString("k", "synop")
                ))
            }
        } catch (_: Exception) {}
        return out
    }

    @Synchronized
    private fun saveAll(c: Context, list: List<Report>) {
        val a = JSONArray()
        for (r in list) {
            val o = JSONObject()
            o.put("id", r.id).put("station", r.station).put("y", r.year).put("m", r.month).put("d", r.day).put("h", r.hour)
            o.put("synop", r.synop).put("metar", r.metar).put("f", r.fields).put("k", r.kind)
            r.dry?.let { o.put("dry", it) }
            r.qfe?.let { o.put("qfe", it) }
            a.put(o)
        }
        val tmp = File(c.filesDir, "reports.json.tmp")
        tmp.writeText(a.toString(), Charsets.UTF_8)
        tmp.renameTo(file(c))
    }

    /** Adds the report; an older report of the same station and time is replaced. */
    fun upsert(c: Context, r: Report) {
        val list = load(c)
        list.removeAll { it.sameTime(r) || it.id == r.id }
        list.add(r)
        saveAll(c, list)
    }

    fun delete(c: Context, id: Long) { val l = load(c); l.removeAll { it.id == id }; saveAll(c, l) }

    fun find(list: List<Report>, station: String, y: Int, m: Int, d: Int, h: Int): Report? =
        list.firstOrNull { it.kind == "synop" && it.station == station && it.year == y && it.month == m && it.day == d && it.hour == h }

    /** The newest report of a station. */
    fun last(list: List<Report>, station: String): Report? = list.filter { it.kind == "synop" && it.station == station }.maxByOrNull { it.sortKey }
}
