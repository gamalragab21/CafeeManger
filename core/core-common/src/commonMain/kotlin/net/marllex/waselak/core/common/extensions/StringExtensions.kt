package net.marllex.waselak.core.common.extensions

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
    val formatted = formatAsPrice()
    return "$currencySymbol$formatted"
}

fun Double.formatAsPrice(): String {
    val intPart = toLong()
    val decPart = ((this - intPart) * 100).toLong().let {
        if (it < 0) -it else it
    }
    val sign = if (this < 0) "-" else ""
    val absInt = if (intPart < 0) -intPart else intPart
    val intStr = absInt.toString()
    val grouped = buildString {
        for (i in intStr.indices) {
            if (i > 0 && (intStr.length - i) % 3 == 0) append(',')
            append(intStr[i])
        }
    }
    return "$sign$grouped.${decPart.toString().padStart(2, '0')}"
}
