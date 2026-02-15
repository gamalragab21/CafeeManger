package net.marllex.waselak.core.common.utils

object CurrencyFormatter {

    fun format(amount: Double): String {
        return "${"%.2f".format(amount)} EGP"
    }

    fun formatArabic(amount: Double): String {
        return "${"%.2f".format(amount)} ج.م"
    }
}
