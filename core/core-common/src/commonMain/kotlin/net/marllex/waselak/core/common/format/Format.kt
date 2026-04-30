package net.marllex.waselak.core.common.format

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Multiplatform replacement for `kFormat(...)` from JVM.
 *
 * Kotlin/Native doesn't expose `java.util.Formatter`, so any commonMain
 * code that called `kFormat("%.2f", value)` would compile fine on
 * Android and JVM-desktop but fail with `Unresolved reference 'format'`
 * on iOS.
 *
 * This polyfill supports the limited set of patterns the project actually
 * uses — `%[,][.N](f|d|s)`. Anything else falls back to `args.toString()`
 * with a leading literal section so we don't crash; if a new format string
 * shows up we'll see it during dev and add a case.
 *
 * Usage in commonMain:
 * ```kotlin
 * import net.marllex.waselak.core.common.format.kFormat
 * Text(kFormat("%.2f", price))           // "12.34"
 * Text(kFormat("%,d", count))            // "1,234,567"
 * Text(kFormat("%,.2f EGP", revenue))    // "1,234.50 EGP"
 * ```
 *
 * On JVM the actual just delegates to `kFormat(Locale.US, ...)` so
 * behaviour matches what the existing call sites expect (Locale.US so
 * the decimal separator is always a dot, regardless of the device locale).
 */
fun kFormat(format: String, vararg args: Any?): String {
    val sb = StringBuilder(format.length + args.size * 4)
    var i = 0
    var argIdx = 0
    while (i < format.length) {
        val c = format[i]
        if (c != '%') { sb.append(c); i++; continue }
        // Skip the trailing `%` if it's the last char (malformed but be lenient).
        if (i == format.length - 1) { sb.append(c); i++; continue }
        val next = format[i + 1]
        // Literal "%%" → "%"
        if (next == '%') { sb.append('%'); i += 2; continue }

        // Parse [,] [.N] [f|d|s]
        var j = i + 1
        var grouping = false
        if (format[j] == ',') { grouping = true; j++ }
        var precision = -1
        if (j < format.length && format[j] == '.') {
            j++
            var p = 0
            while (j < format.length && format[j] in '0'..'9') {
                p = p * 10 + (format[j] - '0')
                j++
            }
            precision = p
        }
        if (j >= format.length) { sb.append(format, i, format.length); break }
        val type = format[j]
        val arg = args.getOrNull(argIdx)
        argIdx++

        when (type) {
            'f' -> {
                val v = (arg as? Number)?.toDouble() ?: 0.0
                sb.append(formatDecimal(v, precision.takeIf { it >= 0 } ?: 6, grouping))
            }
            'd' -> {
                val v = (arg as? Number)?.toLong() ?: 0L
                sb.append(formatInteger(v, grouping))
            }
            's' -> sb.append(arg?.toString() ?: "null")
            else -> sb.append(arg?.toString() ?: "null") // unsupported, but never throw
        }
        i = j + 1
    }
    return sb.toString()
}

/**
 * Format a double with a fixed number of decimals. Mirrors what
 * `kFormat("%.${decimals}f", value)` produced on JVM:
 *   - Half-up rounding (5.555 → 5.56 with 2 decimals)
 *   - Optional thousands grouping (5,432.10)
 *   - Always emits exactly `decimals` fractional digits, even trailing zeros
 */
private fun formatDecimal(value: Double, decimals: Int, grouping: Boolean): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
    val negative = value < 0
    val absValue = abs(value)
    val multiplier = 10.0.pow(decimals)
    // Half-up; Long is enough for any number that fits in a Double's
    // integer range when multiplied by up to 10^9.
    val scaled = (absValue * multiplier).roundToLong()
    val intPart = scaled / multiplier.toLong()
    val fracPart = scaled - intPart * multiplier.toLong()
    val intStr = if (grouping) groupThousands(intPart) else intPart.toString()
    val out = if (decimals == 0) intStr
    else "$intStr.${fracPart.toString().padStart(decimals, '0')}"
    return if (negative) "-$out" else out
}

private fun formatInteger(value: Long, grouping: Boolean): String {
    if (!grouping) return value.toString()
    val negative = value < 0
    val abs = if (negative) -value else value
    val out = groupThousands(abs)
    return if (negative) "-$out" else out
}

/** Insert commas every three digits from the right. "1234567" → "1,234,567". */
private fun groupThousands(value: Long): String {
    val s = value.toString()
    if (s.length <= 3) return s
    val sb = StringBuilder(s.length + s.length / 3)
    val firstGroup = s.length % 3
    if (firstGroup > 0) {
        sb.append(s, 0, firstGroup)
        if (firstGroup < s.length) sb.append(',')
    }
    var i = firstGroup
    while (i < s.length) {
        sb.append(s, i, i + 3)
        if (i + 3 < s.length) sb.append(',')
        i += 3
    }
    return sb.toString()
}
