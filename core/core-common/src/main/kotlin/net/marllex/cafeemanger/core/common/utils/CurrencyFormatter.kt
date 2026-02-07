package net.marllex.cafeemanger.core.common.utils

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    
    fun format(amount: Double): String {
        return String.format("%.2f EGP", amount)
    }
    
    fun formatArabic(amount: Double): String {
        return String.format("%.2f ج.م", amount)
    }
}
