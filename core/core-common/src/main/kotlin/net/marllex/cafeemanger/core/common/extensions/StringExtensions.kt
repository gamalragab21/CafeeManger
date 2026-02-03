package net.marllex.cafeemanger.core.common.extensions

import java.text.DecimalFormat
import java.util.Locale

fun String.isValidEmail(): Boolean {
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\$".toRegex()
    return matches(emailRegex)
}

fun String.isValidPhone(): Boolean {
    val phoneRegex = "^[+]?[0-9]{10,15}\$".toRegex()
    return replace(" ", "").replace("-", "").matches(phoneRegex)
}

fun String.toTitleCase(): String {
    return split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}

fun String.maskPhone(): String {
    if (length < 4) return this
    return "${take(3)}${"*".repeat(length - 6)}${takeLast(3)}"
}

fun Double.formatAsCurrency(currencySymbol: String = "$"): String {
    val formatter = DecimalFormat("#,##0.00")
    return "$currencySymbol${formatter.format(this)}"
}

fun Double.formatAsPrice(): String {
    return DecimalFormat("#,##0.00").format(this)
}
