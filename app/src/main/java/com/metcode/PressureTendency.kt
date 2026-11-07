package com.metcode

import kotlin.math.abs

/**
 * 3-hour pressure tendency (group 5appp) from QFE one hour apart: H-3, H-2, H-1 and H (now).
 * ppp = |QFE(H) - QFE(H-3)| in tenths of hPa. a is found from the shape of the 3 hours.
 * H-2 or H-1 may be missing (NIL); a is then taken from the points that exist.
 * Rule fitted to the AWSPlus report (25 of 28 hourly cases match; the rest are +-0.1 rounding cases).
 */
object PressureTendency {

    data class Result(val a: Int, val changeHpa: Double)

    private fun tenths(x: Double) = Math.round(x * 10.0).toInt()
    private fun sgn(x: Int) = Integer.signum(x)

    fun compute(pMinus3: Double, pMinus2: Double?, pMinus1: Double?, pNow: Double): Result {
        val pts = listOfNotNull(tenths(pMinus3), pMinus2?.let { tenths(it) }, pMinus1?.let { tenths(it) }, tenths(pNow))
        val net = pts.last() - pts.first()
        val steps = pts.zipWithNext { x, y -> y - x }
        val s = sgn(net)

        // 1 = rise, -1 = fall, 0 = steady. A 0.1 step that goes the same way as the net change,
        // when a bigger step (0.2 or more) in that direction exists, counts as steady.
        val cls = steps.map { d ->
            when {
                d == 0 -> 0
                abs(d) >= 2 -> sgn(d)
                s != 0 && sgn(d) == s && steps.any { abs(it) >= 2 && sgn(it) == s } -> 0
                else -> sgn(d)
            }
        }
        val up = cls.any { it > 0 }
        val down = cls.any { it < 0 }

        val a = when {
            !up && !down -> if (net >= 2) 2 else if (net <= -2) 7 else 4
            up && !down -> when {
                cls.last() == 0 -> 1
                cls.first() == 0 && steps.maxOf { abs(it) } >= 2 -> 3
                else -> 2
            }
            down && !up -> when {
                cls.last() == 0 -> 6
                cls.first() == 0 -> 8
                else -> 7
            }
            else -> {
                val first = cls.first { it != 0 }
                if (first > 0) (if (net >= 0) 0 else 8) else (if (net > 0) 3 else 5)
            }
        }
        return Result(a, abs(net) / 10.0)
    }
}