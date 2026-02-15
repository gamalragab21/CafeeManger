package net.marllex.waselak.core.common.utils

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object CurrencyFormatter {

    /** Format with 2 decimal places + " EGP" suffix, e.g. "123.45 EGP" */
    fun format(amount: Double): String {
        return "${formatDecimal(amount, 2)} EGP"
    }

    /** Format with 2 decimal places + " ج.م" suffix */
    fun formatArabic(amount: Double): String {
        return "${formatDecimal(amount, 2)} ج.م"
    }

    /**
     * General-purpose decimal formatter.
     * [decimals] = 0 → "123", 1 → "123.5", 2 → "123.45"
     */
    fun formatDecimal(value: Double, decimals: Int = 2): String {
        if (decimals <= 0) {
            return value.roundToLong().toString()
        }
        val factor = pow10(decimals)
        val rounded = (abs(value) * factor).roundToLong()
        val intPart = rounded / factor
        val fracPart = rounded % factor
        val sign = if (value < 0) "-" else ""
        return "$sign$intPart.${fracPart.toString().padStart(decimals, '0')}"
    }

    private fun pow10(n: Int): Long {
        var result = 1L
        repeat(n) { result *= 10 }
        return result
    }
}
