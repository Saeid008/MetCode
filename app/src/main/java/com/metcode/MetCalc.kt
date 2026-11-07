package com.metcode

import kotlin.math.*

/**
 * Faithful port of the original app's calculation formulas (from the
 * decompiled TwetKt.with_TWET / RHKt.with_RH), with one addition: pCor,
 * the barometer's own instrument/calibration correction (from the card
 * attached to the barometer), which the original app did not have as a
 * separate input. It is added directly into the pressure correction,
 * alongside the temperature/gravity/altitude correction the app already
 * computed.
 *
 * All physical constants below are unchanged from the original app.
 */
object MetCalc {

private const val A1 = 1.8181E-4
private const val B1 = 1.84E-5
private const val ETA = 1.0E-5
private const val VA = 34.0
private const val R_GAS = 287.05
private const val Pn = 1013.246
private const val LOG10E = 0.434294481903252 // log10(e), used to convert ln -> log10 like the original

private fun oneDec(x: Double) = Math.round(x * 10.0) / 10.0
private fun twoDec(x: Double) = Math.round(x * 100.0) / 100.0

data class MetResult(
    val qfeCorrection: Double,
    val qfe: Double,
    val vapourPressure: Double,
    val qff: Double,
    val qnh: Double,
    val qnhInHg: Double,
    val h850: Double,
    val dewPointC: Double,
    val relativeHumidity: Long,
    val wetBulbC: Double // echoes input when known from wet bulb directly, or solved value when starting from RH
)

/**
 * Pressure reduction (temperature + gravity + altitude correction) exactly
 * as in the original app, plus the instrument correction pCor.
 * Returns Pair(correctionValue, correctedPressure=QFE).
 */
private fun qfeCorrection(pressHpa: Double, heightM: Double, latRad: Double, attachedTempC: Double, pCor: Double): Pair<Double, Double> {
    val tCor = ((-1.0 * pressHpa) * (A1 - B1) * attachedTempC) / (1.0 + (A1 * attachedTempC)) -
        ((VA * 1.333224) * (A1 - (3 * ETA))) * attachedTempC
    val gCor = (((1.0 - (cos(2 * latRad) * 0.0026373)) + ((cos(2 * latRad) * 5.9E-6) * cos(2 * latRad))) * 0.9999500339055641 - 1.0) * pressHpa
    val hCor = (-2.007E-7) * heightM * pressHpa
    val hpCor = (heightM / 2.0) * (-1.14E-7) * pressHpa
    val correction = gCor + hCor + hpCor + tCor + pCor
    val qfe = pressHpa + correction
    return Pair(correction, qfe)
}

/** H850: geopotential height of the 850 hPa level above the station, same formula as the original. */
private fun h850(qfe: Double, heightM: Double, latRad: Double, tv: Double): Double {
    val gfi = 978.0356 * ((1.0 + (sin(latRad) * 0.0052885) * sin(latRad)) - (sin(2 * latRad) * 5.9E-6) * sin(2 * latRad))
    val gfir = gfi / 100.0
    val r1 = (2.0 * gfir) / (((1.0e-6 * 3.085462) + (1.0e-9 * 2.27 * cos(latRad * 2))) - (2.0 * 1.0e-12 * cos(4 * latRad)))
    val s = (gfir * r1) / 9.80665
    val hgpm = (s * heightM) / (r1 + heightM)
    val dhtop = ((R_GAS * tv) * ln(qfe / 850.0)) / 9.80665 + hgpm
    return oneDec(dhtop)
}

/** QFF: pressure reduced to mean sea level, same formula as the original. */
private fun qff(qfe: Double, heightM: Double, latRad: Double, tv: Double): Double {
    val gfi1 = 9.780356 * ((1.0 + (sin(latRad) * 0.0052885) * sin(latRad)) - (sin(2 * latRad) * 5.9E-6) * sin(2 * latRad))
    val value = qfe * exp((gfi1 * heightM) / (R_GAS * (tv + 0.00325 * heightM)))
    return oneDec(value)
}

/** QNH (ICAO standard atmosphere reduction), same formula as the original. */
private fun qnh(qfe: Double, heightM: Double): Pair<Double, Double> {
    val base = qfe - 0.3
    val powerBase = 1.0 + ((Math.pow(1013.25, 0.190284) * 0.0065) / 288.0) * (heightM / Math.pow(base, 0.190284))
    val value = base * Math.pow(powerBase, 5.255302600323726)
    val inHg = value / 33.86389
    return Pair(oneDec(value), twoDec(inHg))
}

/** Dew point via Newton-Raphson against vapour pressure, same iteration as the original (100 steps). */
private fun dewPointFromVapourPressure(vapourPressure: Double): Double {
    var kdew = 273.16
    var i = 0
    while (i < 100) {
        val t = (((((((373.16 / kdew) - 1.0) * (-7.90298)) +
            ((LOG10E * 5.02808) * ln(373.16 / kdew))) -
            ((Math.pow(10.0, (1.0 - (kdew / 373.16)) * 11.344) - 1.0) * 1.3816E-7)) +
            ((Math.pow(10.0, ((373.16 / kdew) - 1.0) * (-3.49149)) - 1.0) * 0.0081328)) +
            (ln(Pn) * LOG10E)) -
            (ln(vapourPressure) * LOG10E)
        val ftT = ((2949.0424324 / kdew.pow(2.0)) - ((LOG10E * 5.02808) / kdew)) +
            (Math.pow(10.0, -7.0) * 1.3816 * 0.03039982849179976 * ln(10.0) * Math.pow(10.0, (1.0 - (kdew / 373.16)) * 11.344)) +
            (((ln(10.0) * 10.596098316635523) * Math.pow(10.0, ((373.16 / kdew) - 1.0) * (-3.49149))) / kdew.pow(2.0))
        kdew -= t / ftT
        i++
    }
    return oneDec(kdew - 273.16)
}

/** Relative humidity (%) from vapour pressure and dry-bulb temperature, saturation vapour pressure per the original. */
private fun relativeHumidityFromVapourPressure(vapourPressure: Double, dryBulbC: Double): Long {
    val tk = dryBulbC + 273.16
    val a1v = (373.16 / tk) - 1.0
    val a2v = 1.0 - (tk / 373.16)
    val a3v = a2v * 11.344
    val a4v = a1v * (-3.49149)
    val satVapourAtDry = Math.pow(
        10.0,
        ((((-7.90298) * a1v) + ((LOG10E * 5.02808) * ln(a1v + 1.0))) -
            ((Math.pow(10.0, a3v) - 1.0) * (Math.pow(10.0, -7.0) * 1.3816))) +
            ((Math.pow(10.0, a4v) - 1.0) * 0.0081328) +
            (LOG10E * ln(1013.25))
    )
    val rh = (vapourPressure / satVapourAtDry) * 100.0
    return Math.round(rh)
}

/** Virtual temperature term used by QFF/H850, same formula as the original. */
private fun virtualTemp(dryBulbC: Double, t12C: Double, vapourPressure: Double, qfe: Double): Double {
    return (((dryBulbC + t12C) / 2.0) + 273.15) / (1.0 - ((0.378 * vapourPressure) / qfe))
}

/**
 * Starting point: wet-bulb temperature known directly (data logger / psychrometer reading).
 * This mirrors TwetKt.with_TWET exactly, plus pCor.
 */
fun computeFromWetBulb(
    pressHpa: Double,
    heightM: Double,
    latDeg: Double,
    latMin: Double,
    attachedTempC: Double,
    pCor: Double,
    t12C: Double,
    dryBulbC: Double,
    wetBulbC: Double
): MetResult {
    val latRad = (latDeg + (latMin / 60.0)) * (PI / 180.0)
    val (corr, qfeRaw) = qfeCorrection(pressHpa, heightM, latRad, attachedTempC, pCor)
    val correction = oneDec(corr)
    val qfeVal = oneDec(qfeRaw)

    val vapourPressure: Double
    if (wetBulbC < 0.1) {
        val x = (273.16 / (wetBulbC + 273.16)) - 1.0
        vapourPressure = Math.pow(
            10.0,
            (((-9.09718 * x) - (ln(x + 1.0) * 3.56654 * LOG10E)) + ((1.0 - (1.0 / (1.0 + x))) * 0.876793)) +
                (ln(6.1071) * LOG10E)
        ) - (((dryBulbC - wetBulbC) * 7.2E-4) * qfeRaw)
    } else {
        val tk = wetBulbC + 273.16
        vapourPressure = Math.pow(
            10.0,
            ((((((373.16 / tk) - 1.0) * (-7.90298)) +
                ((LOG10E * 5.02808) * ln(((373.16 / tk) - 1.0) + 1.0))) -
                ((Math.pow(10.0, -7.0) * 1.3816) * (Math.pow(10.0, (1.0 - (tk / 373.16)) * 11.344) - 1.0))) +
                ((Math.pow(10.0, ((373.16 / tk) - 1.0) * (-3.49149)) - 1.0) * 0.0081328)) +
                (LOG10E * ln(Pn))
        ) - (((dryBulbC - wetBulbC) * 7.99E-4) * qfeRaw)
    }
    val vapourPressureA = oneDec(vapourPressure)

    val tv = virtualTemp(dryBulbC, t12C, vapourPressure, qfeRaw)
    val qffVal = qff(qfeRaw, heightM, latRad, tv)
    val (qnhVal, qnhInHg) = qnh(qfeRaw, heightM)
    val h850Val = h850(qfeRaw, heightM, latRad, tv)
    val dewPoint = dewPointFromVapourPressure(vapourPressure)
    val rh = relativeHumidityFromVapourPressure(vapourPressure, dryBulbC)

    return MetResult(correction, qfeVal, vapourPressureA, qffVal, qnhVal, qnhInHg, h850Val, dewPoint, rh, oneDec(wetBulbC))
}

/**
 * Starting point: relative humidity known directly (humidity sensor), wet
 * bulb is back-solved. This mirrors RHKt.with_RH exactly, plus pCor.
 */
fun computeFromRH(
    pressHpa: Double,
    heightM: Double,
    latDeg: Double,
    latMin: Double,
    attachedTempC: Double,
    pCor: Double,
    t12C: Double,
    dryBulbC: Double,
    rhPercent: Double
): MetResult {
    val latRad = (latDeg + (latMin / 60.0)) * (PI / 180.0)
    val (corr, qfeRaw) = qfeCorrection(pressHpa, heightM, latRad, attachedTempC, pCor)
    val correction = oneDec(corr)
    val qfeVal = oneDec(qfeRaw)

    val tk = dryBulbC + 273.16
    val a1v = (373.16 / tk) - 1.0
    val a2v = 1.0 - (tk / 373.16)
    val a3v = a2v * 11.344
    val a4v = a1v * (-3.49149)
    val satVapourAtDry = Math.pow(
        10.0,
        ((((-7.90298) * a1v) + ((LOG10E * 5.02808) * ln(a1v + 1.0))) -
            ((Math.pow(10.0, a3v) - 1.0) * (Math.pow(10.0, -7.0) * 1.3816))) +
            ((Math.pow(10.0, a4v) - 1.0) * 0.0081328) +
            (LOG10E * ln(1013.25))
    )
    val vapourPressure = (rhPercent * satVapourAtDry) / 100.0
    val vapourPressureA = oneDec(vapourPressure)

    // Newton-Raphson solve for wet-bulb temperature consistent with this vapour pressure
    var kwet = 0.0
    if (dryBulbC < 0.1) {
        repeat(100) {
            val t = vapourPressure - (Math.pow(
                10.0,
                ((((273.16 / (kwet + 273.16)) - 1.0) * (-9.09718)) -
                    ((ln(273.16 / (kwet + 273.16)) * 3.56654) * LOG10E) +
                    ((1.0 - (1.0 / (273.16 / (kwet + 273.16)))) * 0.876793)) +
                    (ln(6.1071) * LOG10E)
            ) - (((dryBulbC - kwet) * 7.2E-4) * qfeVal))
            val ftT = ((-Math.pow(
                10.0,
                ((((-2484.985689) / (kwet + 273.16)) + 9.883015031) - ((ln(273.16 / (kwet + 273.16)) * 3.56654) / ln(10.0))) -
                    (0.00320981476 * kwet)
            )) * (((2484.985689 / (kwet + 273.16).pow(2.0)) + (3.56654 / ((kwet + 273.16) * ln(10.0)))) - 0.00320981476)) * ln(10.0) -
                (7.2E-4 * qfeVal)
            kwet -= t / ftT
        }
    } else {
        repeat(100) {
            val t = vapourPressure - (Math.pow(
                10.0,
                ((((((373.16 / (kwet + 273.16)) - 1.0) * (-7.90298)) +
                    ((LOG10E * 5.02808) * ln(((373.16 / (kwet + 273.16)) - 1.0) + 1.0))) -
                    ((Math.pow(10.0, -7.0) * 1.3816) * (Math.pow(10.0, (1.0 - ((kwet + 273.16) / 373.16)) * 11.344) - 1.0))) +
                    ((Math.pow(10.0, ((373.16 / (kwet + 273.16)) - 1.0) * (-3.49149)) - 1.0) * 0.0081328)) +
                    (LOG10E * ln(Pn))
            ) - (((dryBulbC - kwet) * 7.99E-4) * qfeVal))
            val ftT2 = ((-Math.pow(
                10.0,
                (((((-2949.076017) / (kwet + 273.16)) + 7.894847338) + ((ln(373.16 / (kwet + 273.16)) * 5.02808) / ln(10.0))) -
                    ((Math.pow(10.0, -7.0) * 1.3816) * Math.pow(10.0, 3.039982848 - (kwet * 0.0303998285)))) +
                    (Math.pow(10.0, ((-1302.884408) / (kwet + 273.16)) + 3.49149) * 0.0081328) +
                    (ln(Pn) / ln(10.0))
            )) * (((2949.076017 / (kwet + 273.16).pow(2.0)) - (5.02808 / ((kwet + 273.16) * ln(10.0)))) +
                ((Math.pow(10.0, 3.039982848 - (0.0303998285 * kwet)) * (Math.pow(10.0, -9.0) * 4.200040306)) * ln(10.0)) +
                (((Math.pow(10.0, ((-1302.884408) / (kwet + 273.16)) + 3.49149) * 10.59609831) * ln(10.0)) / (kwet + 273.16).pow(2.0)))) * ln(10.0) -
                (qfeVal * 7.99E-4)
            kwet -= t / ftT2
        }
    }
    val wetBulb = oneDec(kwet)

    val tv = virtualTemp(dryBulbC, t12C, vapourPressure, qfeVal)
    val qffVal = qff(qfeVal, heightM, latRad, tv)
    val (qnhVal, qnhInHg) = qnh(qfeVal, heightM)
    val h850Val = h850(qfeVal, heightM, latRad, tv)
    val dewPoint = dewPointFromVapourPressure(vapourPressure)

    return MetResult(correction, qfeVal, vapourPressureA, qffVal, qnhVal, qnhInHg, h850Val, dewPoint, Math.round(rhPercent), wetBulb)
}


fun computeFromWetBulbGivenQFE(
    qfeGiven: Double,
    heightM: Double,
    latDeg: Double,
    latMin: Double,
    t12C: Double,
    dryBulbC: Double,
    wetBulbC: Double
): MetResult {
    val latRad = (latDeg + (latMin / 60.0)) * (PI / 180.0)
    val qfeRaw = qfeGiven
    val correction = 0.0 // QFE was entered directly, no barometer correction applied
    val qfeVal = oneDec(qfeRaw)

    val vapourPressure: Double
    if (wetBulbC < 0.1) {
        val x = (273.16 / (wetBulbC + 273.16)) - 1.0
        vapourPressure = Math.pow(
            10.0,
            (((-9.09718 * x) - (ln(x + 1.0) * 3.56654 * LOG10E)) + ((1.0 - (1.0 / (1.0 + x))) * 0.876793)) +
                (ln(6.1071) * LOG10E)
        ) - (((dryBulbC - wetBulbC) * 7.2E-4) * qfeRaw)
    } else {
        val tk = wetBulbC + 273.16
        vapourPressure = Math.pow(
            10.0,
            ((((((373.16 / tk) - 1.0) * (-7.90298)) +
                ((LOG10E * 5.02808) * ln(((373.16 / tk) - 1.0) + 1.0))) -
                ((Math.pow(10.0, -7.0) * 1.3816) * (Math.pow(10.0, (1.0 - (tk / 373.16)) * 11.344) - 1.0))) +
                ((Math.pow(10.0, ((373.16 / tk) - 1.0) * (-3.49149)) - 1.0) * 0.0081328)) +
                (LOG10E * ln(Pn))
        ) - (((dryBulbC - wetBulbC) * 7.99E-4) * qfeRaw)
    }
    val vapourPressureA = oneDec(vapourPressure)

    val tv = virtualTemp(dryBulbC, t12C, vapourPressure, qfeRaw)
    val qffVal = qff(qfeRaw, heightM, latRad, tv)
    val (qnhVal, qnhInHg) = qnh(qfeRaw, heightM)
    val h850Val = h850(qfeRaw, heightM, latRad, tv)
    val dewPoint = dewPointFromVapourPressure(vapourPressure)
    val rh = relativeHumidityFromVapourPressure(vapourPressure, dryBulbC)

    return MetResult(correction, qfeVal, vapourPressureA, qffVal, qnhVal, qnhInHg, h850Val, dewPoint, rh, oneDec(wetBulbC))
}


fun computeFromRHGivenQFE(
    qfeGiven: Double,
    heightM: Double,
    latDeg: Double,
    latMin: Double,
    t12C: Double,
    dryBulbC: Double,
    rhPercent: Double
): MetResult {
    val latRad = (latDeg + (latMin / 60.0)) * (PI / 180.0)
    val qfeRaw = qfeGiven
    val correction = 0.0 // QFE was entered directly, no barometer correction applied
    val qfeVal = oneDec(qfeRaw)

    val tk = dryBulbC + 273.16
    val a1v = (373.16 / tk) - 1.0
    val a2v = 1.0 - (tk / 373.16)
    val a3v = a2v * 11.344
    val a4v = a1v * (-3.49149)
    val satVapourAtDry = Math.pow(
        10.0,
        ((((-7.90298) * a1v) + ((LOG10E * 5.02808) * ln(a1v + 1.0))) -
            ((Math.pow(10.0, a3v) - 1.0) * (Math.pow(10.0, -7.0) * 1.3816))) +
            ((Math.pow(10.0, a4v) - 1.0) * 0.0081328) +
            (LOG10E * ln(1013.25))
    )
    val vapourPressure = (rhPercent * satVapourAtDry) / 100.0
    val vapourPressureA = oneDec(vapourPressure)

    // Newton-Raphson solve for wet-bulb temperature consistent with this vapour pressure
    var kwet = 0.0
    if (dryBulbC < 0.1) {
        repeat(100) {
            val t = vapourPressure - (Math.pow(
                10.0,
                ((((273.16 / (kwet + 273.16)) - 1.0) * (-9.09718)) -
                    ((ln(273.16 / (kwet + 273.16)) * 3.56654) * LOG10E) +
                    ((1.0 - (1.0 / (273.16 / (kwet + 273.16)))) * 0.876793)) +
                    (ln(6.1071) * LOG10E)
            ) - (((dryBulbC - kwet) * 7.2E-4) * qfeVal))
            val ftT = ((-Math.pow(
                10.0,
                ((((-2484.985689) / (kwet + 273.16)) + 9.883015031) - ((ln(273.16 / (kwet + 273.16)) * 3.56654) / ln(10.0))) -
                    (0.00320981476 * kwet)
            )) * (((2484.985689 / (kwet + 273.16).pow(2.0)) + (3.56654 / ((kwet + 273.16) * ln(10.0)))) - 0.00320981476)) * ln(10.0) -
                (7.2E-4 * qfeVal)
            kwet -= t / ftT
        }
    } else {
        repeat(100) {
            val t = vapourPressure - (Math.pow(
                10.0,
                ((((((373.16 / (kwet + 273.16)) - 1.0) * (-7.90298)) +
                    ((LOG10E * 5.02808) * ln(((373.16 / (kwet + 273.16)) - 1.0) + 1.0))) -
                    ((Math.pow(10.0, -7.0) * 1.3816) * (Math.pow(10.0, (1.0 - ((kwet + 273.16) / 373.16)) * 11.344) - 1.0))) +
                    ((Math.pow(10.0, ((373.16 / (kwet + 273.16)) - 1.0) * (-3.49149)) - 1.0) * 0.0081328)) +
                    (LOG10E * ln(Pn))
            ) - (((dryBulbC - kwet) * 7.99E-4) * qfeVal))
            val ftT2 = ((-Math.pow(
                10.0,
                (((((-2949.076017) / (kwet + 273.16)) + 7.894847338) + ((ln(373.16 / (kwet + 273.16)) * 5.02808) / ln(10.0))) -
                    ((Math.pow(10.0, -7.0) * 1.3816) * Math.pow(10.0, 3.039982848 - (kwet * 0.0303998285)))) +
                    (Math.pow(10.0, ((-1302.884408) / (kwet + 273.16)) + 3.49149) * 0.0081328) +
                    (ln(Pn) / ln(10.0))
            )) * (((2949.076017 / (kwet + 273.16).pow(2.0)) - (5.02808 / ((kwet + 273.16) * ln(10.0)))) +
                ((Math.pow(10.0, 3.039982848 - (0.0303998285 * kwet)) * (Math.pow(10.0, -9.0) * 4.200040306)) * ln(10.0)) +
                (((Math.pow(10.0, ((-1302.884408) / (kwet + 273.16)) + 3.49149) * 10.59609831) * ln(10.0)) / (kwet + 273.16).pow(2.0)))) * ln(10.0) -
                (qfeVal * 7.99E-4)
            kwet -= t / ftT2
        }
    }
    val wetBulb = oneDec(kwet)

    val tv = virtualTemp(dryBulbC, t12C, vapourPressure, qfeVal)
    val qffVal = qff(qfeVal, heightM, latRad, tv)
    val (qnhVal, qnhInHg) = qnh(qfeVal, heightM)
    val h850Val = h850(qfeVal, heightM, latRad, tv)
    val dewPoint = dewPointFromVapourPressure(vapourPressure)

    return MetResult(correction, qfeVal, vapourPressureA, qffVal, qnhVal, qnhInHg, h850Val, dewPoint, Math.round(rhPercent), wetBulb)
}


/**
 * Barometer reduction only (temperature + gravity + altitude corrections).
 * Returns Pair(correction "P Cor", QFE), both unrounded.
 */
fun barometerCorrection(pressHpa: Double, heightM: Double, latDeg: Double, latMin: Double, barTempC: Double): Pair<Double, Double> {
    val latRad = (latDeg + (latMin / 60.0)) * (PI / 180.0)
    return qfeCorrection(pressHpa, heightM, latRad, barTempC, 0.0)
}

/** QNH (hPa) and QNH (inHg, 2 decimals) from QFE. */
fun qnhFromQfe(qfe: Double, heightM: Double): Pair<Double, Double> = qnh(qfe, heightM)

} // end object MetCalc
