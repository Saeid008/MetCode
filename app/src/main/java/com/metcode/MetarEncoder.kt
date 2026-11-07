package com.metcode

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * METAR (FM-15) group encoders, validated against the worked example in
 * the station's METAR manual (wind/visibility/cloud/temp-dewpoint/QNH all
 * matched exactly). Present/past weather phenomena are pass-through --
 * the observer selects them from a list, this does not infer them.
 */
object MetarEncoder {

    /**
     * speedMs/gustMs are in m/s and may have decimals (2.7 m/s = 5.25 kt = "05KT"). METAR wind is reported in knots -- verified against a real
     * station example: 2 m/s -> "04KT". Converts and rounds here so callers
     * never pass raw m/s into the code string by mistake.
     */
    fun windGroup(dirDeg: Int, speedMs: Double, gustMs: Double?, variable: Boolean = false): String {
        val speedKt = (speedMs * 1.943844).roundToInt()
        val gustKt = gustMs?.let { (it * 1.943844).roundToInt() }
        val dirStr = if (variable) "VRB" else "%03d".format(dirDeg)
        val g = if (gustKt != null && gustKt > speedKt) "G%02d".format(gustKt) else ""
        return "$dirStr%02dKT$g".format(speedKt)
    }

    fun visibilityGroup(visM: Int): String {
        if (visM >= 10000) return "9999"
        val v = when {
            visM < 800 -> (visM / 50) * 50
            visM < 5000 -> (visM / 100) * 100
            else -> (visM / 1000) * 1000
        }
        return "%04d".format(v)
    }

    fun cloudAmountCode(octas: Int): String = when {
        octas <= 0 -> "SKC"
        octas <= 2 -> "FEW"
        octas <= 4 -> "SCT"
        octas <= 7 -> "BKN"
        else -> "OVC"
    }

    /**
     * Cloud group, height in feet. METAR only ever writes a type suffix for
     * CB (cumulonimbus) or TCU (towering cumulus) -- every other cloud type
     * (plain CU, SC, ST, AS, AC, CI, etc.) gets no suffix at all. Confirmed
     * against a real station example: CU1 -> "FEW035" with no suffix.
     * Pass metarCloudSuffix(synopCloudTypeLabel) for cloudType.
     */
    fun cloudHeightGroup(amountCode: String, heightFt: Int, cloudType: String = ""): String {
        val h = if (heightFt <= 10000) heightFt / 100 else (heightFt / 1000) * 10
        return "%s%03d%s".format(amountCode, h, cloudType)
    }

    /** Cloud group with the height given in metres: units of 30 m up to 3000 m, then units of 300 m. */
    fun cloudHeightGroupMeters(amountCode: String, heightM: Int, cloudType: String = ""): String {
        val h = if (heightM <= 3000) heightM / 30 else (heightM / 300) * 10
        return "%s%03d%s".format(amountCode, h, cloudType)
    }

    /**
     * Maps a SYNOP-style cloud type label (e.g. "CB9", "CU1", "AC3") to the
     * METAR suffix it should carry -- "CB" or "TCU" only; everything else
     * returns "". Towering cumulus is normally its own selectable type
     * (commonly labelled "TCU" or "CU,SC8"-style in some lists); adjust the
     * matching below if your cloud dropdown labels it differently.
     */
    fun metarCloudSuffix(synopCloudTypeLabel: String): String {
        val label = synopCloudTypeLabel.uppercase()
        return when {
            label.startsWith("CB") -> "CB"
            label == "CU2" -> "TCU" // cumulus congestus = towering cumulus
            label.contains("TCU") -> "TCU"
            else -> ""
        }
    }

    fun tempDewpointGroup(tC: Int, tdC: Int): String {
        fun fmt(x: Int): String {
            val sign = if (x < 0) "M" else ""
            return "$sign%02d".format(abs(x))
        }
        return "${fmt(tC)}/${fmt(tdC)}"
    }

    /** QNH group: truncates (does not round) the decimal part, per the manual. */
    fun qnhGroup(qnhHpa: Double): String {
        val value = qnhHpa.toInt() // truncation toward zero, matching the manual's rule
        return "Q%04d".format(value)
    }

    /**
     * QNH-in-inches group: "A" + inHg*100, truncated to 4 digits. Optional --
     * only appended when the observer's checkbox for it is on. Confirmed
     * against a real station example: 30.14 inHg -> "A3014".
     */
    fun qnhInchGroup(qnhInHg: Double): String {
        val value = (qnhInHg * 100).toInt() // truncation, matching qnhGroup's convention
        return "A%04d".format(value)
    }

    /** CAVOK applies when visibility >=10km, no cloud below 5000ft/minimum sector altitude, no significant weather. */
    fun isCavok(visM: Int, lowestCloudFt: Int?, significantWeather: Boolean): Boolean {
        return visM >= 10000 && (lowestCloudFt == null || lowestCloudFt >= 5000) && !significantWeather
    }

    /** Vertical visibility group, e.g. VV002 (hundreds of feet). */
    fun verticalVisibilityGroup(vvM: Int): String = "VV%03d".format(vvM / 30)
}
