package net.marllex.cafeemanger.core.common.extensions

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Instant.toLocalDateTime(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime {
    return LocalDateTime.ofInstant(this, zoneId)
}

fun LocalDateTime.formatAsDateTime(pattern: String = "MMM dd, yyyy HH:mm"): String {
    return format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

fun LocalDateTime.formatAsDate(pattern: String = "MMM dd, yyyy"): String {
    return format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

fun LocalDateTime.formatAsTime(pattern: String = "HH:mm"): String {
    return format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

fun LocalDate.formatAsDate(pattern: String = "MMM dd, yyyy"): String {
    return format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

fun Instant.formatAsDateTime(pattern: String = "MMM dd, yyyy HH:mm"): String {
    return toLocalDateTime().formatAsDateTime(pattern)
}

fun Instant.formatAsDate(pattern: String = "MMM dd, yyyy"): String {
    return toLocalDateTime().formatAsDate(pattern)
}

fun Instant.formatAsTime(pattern: String = "HH:mm"): String {
    return toLocalDateTime().formatAsTime(pattern)
}

fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)

fun Instant.toEpochMillis(): Long = toEpochMilli()

fun LocalDate.startOfDay(): Instant {
    return atStartOfDay(ZoneId.systemDefault()).toInstant()
}

fun LocalDate.endOfDay(): Instant {
    return atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant()
}
