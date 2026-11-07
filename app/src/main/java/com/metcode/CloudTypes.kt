package com.metcode

enum class CloudCategory { CL, CM, CH }

data class CloudTypeInfo(val category: CloudCategory, val codeFigure: Int)

/**
 * Legend for the cloud_types dropdown, matching AWSPlus's own labels.
 * CL = Table 0513, CM = Table 0515, CH = Table 0509.
 */
object CloudTypes {
    val legend: Map<String, CloudTypeInfo> = mapOf(
        "CU1" to CloudTypeInfo(CloudCategory.CL, 1),
        "CU2" to CloudTypeInfo(CloudCategory.CL, 2),
        "CB3" to CloudTypeInfo(CloudCategory.CL, 3),
        "SC4" to CloudTypeInfo(CloudCategory.CL, 4),
        "SC5" to CloudTypeInfo(CloudCategory.CL, 5),
        "ST6" to CloudTypeInfo(CloudCategory.CL, 6),
        "FS,FC7" to CloudTypeInfo(CloudCategory.CL, 7),
        "CU,SC8" to CloudTypeInfo(CloudCategory.CL, 8),
        "CB9" to CloudTypeInfo(CloudCategory.CL, 9),

        "AS1" to CloudTypeInfo(CloudCategory.CM, 1),
        "AS2" to CloudTypeInfo(CloudCategory.CM, 2),
        "NS2" to CloudTypeInfo(CloudCategory.CM, 2),
        "AC3" to CloudTypeInfo(CloudCategory.CM, 3),
        "AC4" to CloudTypeInfo(CloudCategory.CM, 4),
        "AC5" to CloudTypeInfo(CloudCategory.CM, 5),
        "AC6" to CloudTypeInfo(CloudCategory.CM, 6),
        "AC7" to CloudTypeInfo(CloudCategory.CM, 7),
        "AC,AS7" to CloudTypeInfo(CloudCategory.CM, 7),
        "AC8" to CloudTypeInfo(CloudCategory.CM, 8),
        "AC9" to CloudTypeInfo(CloudCategory.CM, 9),

        "CI1" to CloudTypeInfo(CloudCategory.CH, 1),
        "CI2" to CloudTypeInfo(CloudCategory.CH, 2),
        "CI3" to CloudTypeInfo(CloudCategory.CH, 3),
        "CI4" to CloudTypeInfo(CloudCategory.CH, 4),
        "CS5" to CloudTypeInfo(CloudCategory.CH, 5),
        "CS6" to CloudTypeInfo(CloudCategory.CH, 6),
        "CS7" to CloudTypeInfo(CloudCategory.CH, 7),
        "CS8" to CloudTypeInfo(CloudCategory.CH, 8),
        "CC9" to CloudTypeInfo(CloudCategory.CH, 9)
    )

    /** "TR" or "-"/"/" -> special; otherwise 0-8 oktas. */
    fun amountToOktas(value: String): Int? = when (value) {
        "TR" -> 1 // WMO convention: trace cloud still reports Nh=1, not 0
        "-", "/" -> null
        else -> value.toIntOrNull()
    }

    data class CloudLayer(val amount: String, val typeLabel: String, val heightM: Int?)

    data class DerivedCloudGroup(val nh: Int, val cl: Int, val cm: Int, val ch: Int)

    /**
     * Derives Nh/CL/CM/CH from the entered layers: Nh is the oktas of the
     * layer with the greatest amount that has a recognised type; CL/CM/CH
     * each take the code figure of whichever entered layer falls in that
     * category (highest amount wins if more than one layer shares a
     * category). Returns null fields (as '/') when no layer provides that
     * category. This covers the common 1-2 layer case; unusual combinations
     * of many layers across the same category may need manual review.
     */
    fun derive(layers: List<CloudLayer>): DerivedCloudGroup {
        var bestNh = 0
        var bestNhAmount = -1
        val byCategory = mutableMapOf<CloudCategory, Pair<Int, Int>>() // category -> (codeFigure, amount)

        for (layer in layers) {
            val oktas = amountToOktas(layer.amount) ?: continue
            if (oktas <= 0) continue // a 0-okta layer is no cloud
            val info = legend[layer.typeLabel] ?: continue
            if (oktas > bestNhAmount) {
                bestNhAmount = oktas
                bestNh = oktas
            }
            val existing = byCategory[info.category]
            if (existing == null || oktas > existing.second) {
                byCategory[info.category] = Pair(info.codeFigure, oktas)
            }
        }

        return DerivedCloudGroup(
            nh = bestNh,
            cl = byCategory[CloudCategory.CL]?.first ?: 0,
            cm = byCategory[CloudCategory.CM]?.first ?: 0,
            ch = byCategory[CloudCategory.CH]?.first ?: 0
        )
    }

    /** Table 1511 cloud genus digit used in the 8NsChshs group. */
    fun genusDigit(label: String): Int? = when {
        label.startsWith("CU,SC") -> 8
        label.startsWith("CU") -> 8
        label.startsWith("CB") -> 9
        label.startsWith("SC") -> 6
        label.startsWith("ST") || label.startsWith("FS") -> 7
        label.startsWith("AS") -> 4
        label.startsWith("NS") -> 5
        label.startsWith("AC") -> 3
        label.startsWith("CI") -> 0
        label.startsWith("CS") -> 2
        label.startsWith("CC") -> 1
        else -> null
    }

    /** Height code h (first IRIXhVV digit) for a cloud base height in metres. */
    fun hCodeForHeight(m: Int): String = when {
        m < 50 -> "0"
        m < 100 -> "1"
        m < 200 -> "2"
        m < 300 -> "3"
        m < 600 -> "4"
        m < 1000 -> "5"
        m < 1500 -> "6"
        m < 2000 -> "7"
        m < 2500 -> "8"
        else -> "9"
    }

    /** hshs code (Table hshs) for a height in metres. */
    fun hshsCode(m: Int): Int = when {
        m < 30 -> 0
        m <= 1500 -> m / 30
        m < 1800 -> 50
        m <= 9000 -> 50 + m / 300
        else -> minOf(89, 80 + (m - 9000) / 1500)
    }
}
