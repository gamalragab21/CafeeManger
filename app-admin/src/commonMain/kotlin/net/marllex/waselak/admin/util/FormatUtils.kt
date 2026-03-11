package net.marllex.waselak.admin.util

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** Format a double to [decimals] decimal places (e.g. formatDecimal(3.1415, 2) -> "3.14") */
fun formatDecimal(value: Double, decimals: Int): String {
    if (decimals <= 0) return value.roundToLong().toString()
    val factor = 10.0.pow(decimals)
    val rounded = (value * factor).roundToLong()
    val intPart = rounded / factor.toLong()
    val fracPart = abs(rounded % factor.toLong())
    return "$intPart.${fracPart.toString().padStart(decimals, '0')}"
}

/** Format a double as a percentage string (e.g. formatPercent(85.6, 1) -> "85.6%") */
fun formatPercent(value: Double, decimals: Int = 1): String = "${formatDecimal(value, decimals)}%"

/** Pad an integer to [width] digits with leading zeros */
fun padZero(value: Int, width: Int): String = value.toString().padStart(width, '0')
