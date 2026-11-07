package com.metcode

/** SYNOP present weather ww -> METAR present weather group. Null = nothing to report. */
object MetarWeather {
    fun fromSynopWw(ww: Int?, visM: Int? = null): String? {
        val g = base(ww) ?: return null
        if (visM == null) return g
        return when (g) {
            "FU", "HZ", "DU", "IC" -> if (visM <= 5000) g else null
            "BR" -> if (visM in 1000..5000) g else null
            "FG" -> if (visM < 1000) g else null
            else -> g
        }
    }

    private fun base(ww: Int?): String? = when (ww) {
        null -> null
        4 -> "FU"; 5 -> "HZ"; 6 -> "DU"; 7 -> "BLDU"; 8 -> "PO"; 9 -> "VCDS"
        10 -> "BR"; 11 -> "BCFG"; 12 -> "MIFG"; 15, 16 -> "VCSH"
        17 -> "TS"; 18 -> "SQ"; 19 -> "FC"
        30, 31, 32 -> "DS"; 33, 34, 35 -> "+DS"
        36, 37 -> "DRSN"; 38, 39 -> "BLSN"
        40 -> "VCFG"; 41 -> "BCFG"; in 42..47 -> "FG"; 48, 49 -> "FZFG"
        50, 51 -> "-DZ"; 52, 53 -> "DZ"; 54, 55 -> "+DZ"; 56 -> "-FZDZ"; 57 -> "FZDZ"; 58 -> "-DZRA"; 59 -> "DZRA"
        60, 61 -> "-RA"; 62, 63 -> "RA"; 64, 65 -> "+RA"; 66 -> "-FZRA"; 67 -> "FZRA"; 68 -> "-RASN"; 69 -> "RASN"
        70, 71 -> "-SN"; 72, 73 -> "SN"; 74, 75 -> "+SN"; 76, 78 -> "IC"; 77 -> "SG"; 79 -> "PL"
        80 -> "-SHRA"; 81 -> "SHRA"; 82 -> "+SHRA"; 83 -> "-SHRASN"; 84 -> "SHRASN"
        85 -> "-SHSN"; 86 -> "SHSN"; 87 -> "-SHGS"; 88 -> "SHGS"; 89 -> "-SHGR"; 90 -> "SHGR"
        95 -> "TSRA"; 96 -> "TSGR"; 97 -> "+TSRA"; 98 -> "TSDS"; 99 -> "+TSGR"
        else -> null
    }
}