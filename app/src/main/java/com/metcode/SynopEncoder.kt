package com.metcode

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * SYNOP (FM-12) group encoders. Each function matches a specific WMO code
 * group and was validated against the worked examples in the station's own
 * SYNOP manual before being ported here (see project notes). Fields that
 * are the observer's own judgement call (N, dd/ff, ww, cloud type, etc.)
 * are NOT computed -- they are passed through from what the observer
 * selects in the UI.
 */
object SynopEncoder {

    private fun signMag3(x: Double): String {
        val sn = if (x < 0) 1 else 0
        val mag = Math.round(abs(x) * 10)
        return "%d%03d".format(sn, mag)
    }

    fun yygg(day: Int, hour: Int, iw: Int) = "%02d%02d%d".format(day, hour, iw)

    fun iiiii(stationCode: String) = stationCode.trim()

    /** 1SnTTT -- dry-bulb (air) temperature. */
    fun group1_T(tC: Double) = "1" + signMag3(tC)

    /** 2SnTdTdTd -- dew point temperature. */
    fun group2_Td(tdC: Double) = "2" + signMag3(tdC)

    /** 29UUU -- relative humidity (used instead of 2SnTdTdTd in some encodings; here as a 29 group). */
    fun group29_RH(rhPercent: Long) = "29%03d".format(rhPercent)

    /** 3PoPoPoPo -- station level pressure (QFE), tenths of hPa, last 4 digits. */
    fun group3_QFE(qfeHpa: Double) = "3%04d".format((Math.round(qfeHpa * 10) % 10000))

    /** 4PPPP -- sea level pressure (QFF), tenths of hPa, last 4 digits. */
    fun group4_QFF(qffHpa: Double) = "4%04d".format((Math.round(qffHpa * 10) % 10000))

    /** 5appp -- 3-hour pressure tendency. a = characteristic code (0-8), delta = magnitude in hPa. */
    fun group5_tendency(aCode: Int, deltaHpa: Double): String {
        val ppp = Math.round(abs(deltaHpa) * 10)
        return "5%d%03d".format(aCode, ppp)
    }

    /** 1SnTwTwTw -- wet-bulb temperature (Section 5). */
    fun group1_Tw(twC: Double) = "1" + signMag3(twC)

    /** 3ESnTgTg -- state of ground (no snow, Table 1910) + minimum ground temperature (06Z). */
    fun group3_groundTemp(eCode: Int, tgC: Double) = "3%d%s".format(eCode, signMag3(tgC))

    /** 4E'SSS -- state of ground with snow (Table 1905) + snow depth code (Table 3119). */
    fun group4_groundSnow(ePrimeCode: Int, snowDepthCode: Int) = "4%d%03d".format(ePrimeCode, snowDepthCode)

    /**
     * Visibility code VV from metres, per the manual's table.
     * Verified against a real station SYNOP: 15 km -> VV=65, confirming
     * code = 50 + km for the 6-30 km range (not 50 + (km-5) as first coded).
     */
    fun visibilityCode(visM: Int): Int {
        val km = visM / 1000.0
        return when {
            visM < 100 -> 0
            visM <= 5000 -> visM / 100
            visM < 6000 -> 50 // codes 51-55 are not used
            visM <= 30000 -> (50 + km).toInt()
            visM <= 70000 -> 81 + ((visM - 30000) / 5000)
            else -> 89
        }
    }

    /** Snow depth code SSS per Table 3119 (cm input). */
    fun snowDepthCode(cm: Double?): Int {
        if (cm == null) return 997 // not covered
        return when {
            cm < 1 -> 0
            cm < 100 -> cm.roundToInt().coerceIn(1, 99)
            cm < 115 -> 996 // 1m or more (approx boundary; refine per local table if needed)
            else -> 996
        }
    }

    /** IRIXhVV -- precipitation/weather indicators, base height code h ('0'-'9' or '/') and visibility code VV. */
    fun groupIrixhvv(ir: Int, ix: Int, h: String, vv: Int) = "%d%d%s%02d".format(ir, ix, h, vv)

    /**
     * Nddff -- total cloud N ('0'-'9','/' or "TR"; TR is reported as 1 per the
     * WMO rule that any cloud below 1 okta is still coded 1), wind direction in
     * tens of degrees (99 = variable, 36 = north, 00 = calm) and speed in m/s.
     */
    fun groupNddff(n: String, ddDeg: Int?, variable: Boolean, ffMs: Int?): String {
        val nChar = if (n == "TR") "1" else n
        val ddCode = when {
            variable -> "99"
            ddDeg == null -> "//"
            ffMs == 0 -> "00"
            else -> {
                val tens = Math.round(ddDeg / 10.0).toInt()
                "%02d".format(if (tens == 0) 36 else if (tens > 36) tens - 36 else tens)
            }
        }
        val ffCode = if (ffMs == null) "//" else "%02d".format(ffMs.coerceIn(0, 99))
        return nChar + ddCode + ffCode
    }

    /** 6RRRTR -- precipitation amount + time-period code. */
    fun group6_precip(rrMm: Double?, trCode: Int): String? {
        if (rrMm == null) return null
        val rrr = when {
            rrMm < 0.1 -> 990 // trace -- adjust per manual's exact trace convention if different
            rrMm <= 0.9 -> 990 + Math.round(rrMm * 10).toInt().coerceIn(1, 9)
            rrMm <= 988.0 -> Math.round(rrMm).toInt()
            else -> 989
        }
        return "6%03d%d".format(rrr, trCode)
    }

    /** 7wwW1W2 -- present weather code (00-99) + two past-weather codes (0-9 each). Pass-through values. */
    fun group7_weather(wwCode: Int, w1Code: Int, w2Code: Int) = "7%02d%d%d".format(wwCode, w1Code, w2Code)

    /** 8NhCLCMCH -- cloud group. Pass-through values, each a single digit (or '/'). */
    fun group8_clouds(nh: Int, cl: Int, cm: Int, ch: Int) = "8%d%d%d%d".format(nh, cl, cm, ch)

    /** 9SPSPSPSP placeholder for special phenomena groups -- not yet implemented; extend as needed. */

    /** Section 5: 66666 soil-temperature group, depths in cm -> prefix digit. Returns null if no depths given. */
    fun section5_soilTemp(depthsCtoTemp: Map<Int, Double>): String? {
        val prefixes = mapOf(5 to "0", 10 to "1", 20 to "2", 30 to "3", 50 to "5", 100 to "9")
        val parts = listOf(5, 10, 20, 30, 50, 100).mapNotNull { depth ->
            depthsCtoTemp[depth]?.let { t -> prefixes[depth] + signMag3(t) }
        }
        return if (parts.isEmpty()) null else "66666 " + parts.joinToString(" ")
    }

    /** J5F24F24F24F24 -- radiation group (Iran: J5 fixed at 2, F24 = counter x 1.219 in J/cm^2). */
    fun radiationGroup(counterReading: Double): String {
        val jPerCm2 = Math.round(counterReading * 1.219)
        return "2%04d".format(jPerCm2 % 10000) // group indicator digit handled by caller ("J5")
    }

    /** 55SSS -- sunshine duration, tenths of an hour. */
    fun sunshineGroup(hoursOfSun: Double) = "55%03d".format(Math.round(hoursOfSun * 10))

    // ---------------------------------------------------------------- new groups

    /** 5appp, or 5//// when the tendency is not available. */
    fun groupTendency(a: Int?, changeHpa: Double?): String =
        if (a == null || a !in 0..8 || changeHpa == null) "5////"
        else "5%d%03d".format(a, Math.round(abs(changeHpa) * 10).toInt().coerceAtMost(999))

    /** 6RRRtR. rrMm null -> RRR is ///. 0 -> 000, below 0.1 -> trace 990, 0.1-0.9 -> 991-999. */
    fun groupPrecip(rrMm: Double?, tR: Int): String {
        if (rrMm == null) return "6///$tR"
        val rrr = when {
            rrMm <= 0.0 -> 0
            rrMm < 0.05 -> 990
            rrMm < 0.95 -> 990 + Math.round(rrMm * 10).toInt().coerceIn(1, 9)
            else -> Math.round(rrMm).toInt().coerceIn(1, 989)
        }
        return "6%03d%d".format(rrr, tR)
    }

    /** 7R24R24R24R24: 24 h precipitation in tenths of mm; trace is 9999. */
    fun group7Precip24(rrMm: Double?): String? {
        if (rrMm == null) return null
        if (rrMm > 0.0 && rrMm < 0.05) return "79999"
        return "7%04d".format(Math.round(rrMm * 10).toInt().coerceIn(0, 9998))
    }

    /** 1SnTxTxTx (Tmax) / 2SnTnTnTn (Tmin) in tenths of a degree. */
    fun groupTmax(tC: Double) = "1" + signMag3(tC)
    fun groupTmin(tC: Double) = "2" + signMag3(tC)

    /** 3ESnTgTg: ground state E ('0'-'9' or '/') and minimum ground temperature in whole degrees. */
    fun groupTg(e: String, tgC: Double?): String =
        if (tgC == null) "3${e}///"
        else "3%s%d%02d".format(e, if (tgC < 0) 1 else 0, Math.round(abs(tgC)).toInt().coerceAtMost(99))

    /** 4E'sss: snow state E' and snow depth code (001-996 cm, 997, 998, 999). */
    fun groupSnow(ePrime: String, sssCode: Int?): String =
        if (sssCode == null) "4${ePrime}///" else "4%s%03d".format(ePrime, sssCode.coerceIn(1, 999))

    /** 5EEEiE: evaporation in tenths of mm, iE=1 (USA pan with wire mesh) as used at Iranian stations. */
    fun groupEvaporation(mm: Double): String = "5%03d1".format(Math.round(mm * 10).toInt().coerceIn(0, 999))

    /** 55SSS: sunshine duration in tenths of an hour. */
    fun groupSunshine(hours: Double): String = "55%03d".format(Math.round(hours * 10).toInt().coerceIn(0, 999))

    /**
     * Fresh snow: time-reference group followed by 931SS.
     * The code input uses the AWSPlus style: 0-400 cm as is, 1097 (<0.1 cm), 1098 (>400 cm), 1099 (not measurable).
     * SS is the code table value: 0-400 cm -> value, 97/98/99 for the special cases.
     */
    const val FRESH_SNOW_TIME_GROUP = "90768" // 24 h reference period, as written in the manual (its example shows 90768)

    fun groupFreshSnow(input: Int): String {
        val ss = when {
            input >= 1097 -> input - 1000                              // 97, 98, 99 as typed
            input in 0..55 -> input                                    // whole cm
            input in 56..400 -> 50 + (input / 10.0).roundToInt()       // 56-90 = 60-400 cm in 10 cm steps
            input > 400 -> 98
            else -> 99
        }
        return "931%02d".format(ss)
    }

    /** 3ddff maximum wind / 4dgdgfgfg gust over the past 24 h (direction in degrees, speed in m/s). */
    fun groupMaxWind(ddDeg: Int, ffMs: Int) = "3%02d%02d".format(tens(ddDeg), ffMs.coerceIn(0, 99))
    fun groupGust24(ddDeg: Int, ffMs: Int) = "4%02d%02d".format(tens(ddDeg), ffMs.coerceIn(0, 99))

    private fun tens(deg: Int): Int {
        val t = Math.round(deg / 10.0).toInt()
        return if (t == 0) 36 else if (t > 36) t - 36 else t
    }

    /** 8NsChshs for one cloud layer: oktas, genus digit and height in metres. */
    fun groupCloudLayer(oktas: Int, genus: Int, heightM: Int): String =
        "8%d%d%02d".format(oktas.coerceIn(0, 9), genus, CloudTypes.hshsCode(heightM))

    /** 89/hshs: sky obscured, vertical visibility in metres. */
    fun groupVerticalVisibility(vvM: Int): String = "89/%02d".format(CloudTypes.hshsCode(vvM))

    /** 66666 block (soil temperature, depths 5/10/20/30/50/100 cm). Every depth is written, missing ones as ////. */
    fun soilTemperatureBlock(values: List<Double?>): String? {
        if (values.all { it == null }) return null
        val prefixes = listOf("0", "1", "2", "3", "5", "9")
        val groups = values.mapIndexed { i, v -> if (v == null) prefixes[i] + "////" else prefixes[i] + signMag3(v) }
        return "66666 " + groups.joinToString(" ")
    }

    /** 77777 block (soil moisture %, depths 5/10/20/30/50/70/100 cm). */
    fun soilMoistureBlock(values: List<Double?>): String? {
        if (values.all { it == null }) return null
        val prefixes = listOf("00", "10", "20", "30", "50", "70", "99")
        val groups = values.mapIndexed { i, v -> if (v == null) prefixes[i] + "///" else prefixes[i] + "%03d".format(Math.round(v).toInt().coerceIn(0, 999)) }
        return "77777 " + groups.joinToString(" ")
    }
}
