package net.marllex.waselak.core.common.extensions

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

fun Instant.toLocalDateTimeKt(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime {
    return toLocalDateTime(timeZone)
}

/** Returns today's date as a "yyyy-MM-dd" string in the system default timezone. */
fun todayDateString(): String =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

/** Returns the current time as an "HH:mm" string in the system default timezone. */
fun currentTimeString(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
}

fun LocalDateTime.formatAsDateTime(): String {
    val month = monthNumber.toString().padStart(2, '0')
    val day = dayOfMonth.toString().padStart(2, '0')
    val hour = this.hour.toString().padStart(2, '0')
    val minute = this.minute.toString().padStart(2, '0')
    return "$year-$month-$day $hour:$minute"
}

fun LocalDateTime.formatAsDate(): String {
    val month = monthNumber.toString().padStart(2, '0')
    val day = dayOfMonth.toString().padStart(2, '0')
    return "$year-$month-$day"
}

fun LocalDateTime.formatAsTime(): String {
    val hour = this.hour.toString().padStart(2, '0')
    val minute = this.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}

fun LocalDate.formatAsDate(): String {
    val month = monthNumber.toString().padStart(2, '0')
    val day = dayOfMonth.toString().padStart(2, '0')
    return "$year-$month-$day"
}

fun Instant.formatAsDateTime(): String {
    return toLocalDateTimeKt().formatAsDateTime()
}

fun Instant.formatAsDate(): String {
    return toLocalDateTimeKt().formatAsDate()
}

fun Instant.formatAsTime(): String {
    return toLocalDateTimeKt().formatAsTime()
}

fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

fun Instant.toEpochMillis(): Long = toEpochMilliseconds()

fun LocalDate.startOfDay(timeZone: TimeZone = TimeZone.currentSystemDefault()): Instant {
    return atStartOfDayIn(timeZone)
}

fun LocalDate.endOfDay(timeZone: TimeZone = TimeZone.currentSystemDefault()): Instant {
    return atTime(23, 59, 59).toInstant(timeZone)
}

// Short month names for display
private val monthNames = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private val dayNames = arrayOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
)

fun Long.formatEpochMs(pattern: String = "yyyy-MM-dd HH:mm"): String {
    val dt = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return formatLocalDateTime(dt, pattern)
}

fun formatLocalDateTime(dt: LocalDateTime, pattern: String): String {
    val month2 = dt.monthNumber.toString().padStart(2, '0')
    val day2 = dt.dayOfMonth.toString().padStart(2, '0')
    val h24 = dt.hour.toString().padStart(2, '0')
    val min2 = dt.minute.toString().padStart(2, '0')
    val monthShort = monthNames.getOrElse(dt.monthNumber - 1) { "???" }
    val h12 = if (dt.hour == 0) 12 else if (dt.hour > 12) dt.hour - 12 else dt.hour
    val amPm = if (dt.hour < 12) "AM" else "PM"
    val dayOfWeekName = dayNames.getOrElse(dt.dayOfWeek.ordinal) { "???" }

    return when (pattern) {
        "yyyy-MM-dd HH:mm" -> "${dt.year}-$month2-$day2 $h24:$min2"
        "yyyy-MM-dd" -> "${dt.year}-$month2-$day2"
        "HH:mm" -> "$h24:$min2"
        "hh:mm a" -> "${h12.toString().padStart(2, '0')}:$min2 $amPm"
        "MMM dd" -> "$monthShort $day2"
        "MMM dd, yyyy" -> "$monthShort $day2, ${dt.year}"
        "MMM dd, HH:mm" -> "$monthShort $day2, $h24:$min2"
        "MMM dd, hh:mm a" -> "$monthShort $day2, ${h12.toString().padStart(2, '0')}:$min2 $amPm"
        "MMM dd, yyyy HH:mm" -> "$monthShort $day2, ${dt.year} $h24:$min2"
        "MMM dd, yyyy hh:mm a" -> "$monthShort $day2, ${dt.year} ${h12.toString().padStart(2, '0')}:$min2 $amPm"
        "EEEE" -> dayOfWeekName
        else -> "${dt.year}-$month2-$day2 $h24:$min2"
    }
}

fun LocalDateTime.formatAs12HourTime(): String {
    val h12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val amPm = if (hour < 12) "AM" else "PM"
    return "${h12.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} $amPm"
}
