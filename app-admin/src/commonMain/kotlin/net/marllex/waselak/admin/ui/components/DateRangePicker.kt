package net.marllex.waselak.admin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.*

/**
 * Predefined date range periods for analytics filtering.
 */
enum class DateRangePeriod(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    LAST_7_DAYS("Last 7 Days"),
    THIS_MONTH("This Month"),
    LAST_30_DAYS("Last 30 Days"),
    LAST_90_DAYS("Last 90 Days"),
    ALL_TIME("All Time"),
}

data class DateRange(
    val from: Instant?,
    val to: Instant?,
    val period: DateRangePeriod = DateRangePeriod.LAST_30_DAYS
) {
    companion object {
        fun forPeriod(period: DateRangePeriod): DateRange {
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val todayDate = now.toLocalDateTime(tz).date
            val todayStart = LocalDateTime(todayDate, LocalTime(0, 0)).toInstant(tz)
            val tomorrowStart = LocalDateTime(todayDate.plus(1, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(tz)

            return when (period) {
                DateRangePeriod.TODAY -> DateRange(from = todayStart, to = tomorrowStart, period = period)
                DateRangePeriod.YESTERDAY -> {
                    val yesterdayStart = LocalDateTime(todayDate.minus(1, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(tz)
                    DateRange(from = yesterdayStart, to = todayStart, period = period)
                }
                DateRangePeriod.THIS_WEEK -> {
                    val daysSinceMonday = (todayDate.dayOfWeek.ordinal) // Monday=0
                    val weekStart = LocalDateTime(todayDate.minus(daysSinceMonday, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(tz)
                    DateRange(from = weekStart, to = tomorrowStart, period = period)
                }
                DateRangePeriod.LAST_7_DAYS -> {
                    val from = LocalDateTime(todayDate.minus(7, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(tz)
                    DateRange(from = from, to = tomorrowStart, period = period)
                }
                DateRangePeriod.THIS_MONTH -> {
                    val monthStart = LocalDateTime(LocalDate(todayDate.year, todayDate.month, 1), LocalTime(0, 0)).toInstant(tz)
                    DateRange(from = monthStart, to = tomorrowStart, period = period)
                }
                DateRangePeriod.LAST_30_DAYS -> {
                    val from = LocalDateTime(todayDate.minus(30, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(tz)
                    DateRange(from = from, to = tomorrowStart, period = period)
                }
                DateRangePeriod.LAST_90_DAYS -> {
                    val from = LocalDateTime(todayDate.minus(90, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(tz)
                    DateRange(from = from, to = tomorrowStart, period = period)
                }
                DateRangePeriod.ALL_TIME -> DateRange(from = null, to = null, period = period)
            }
        }

        val DEFAULT = forPeriod(DateRangePeriod.LAST_30_DAYS)
    }

    fun toQueryParams(): String {
        val params = mutableListOf<String>()
        from?.let { params.add("from=${it.toEpochMilliseconds()}") }
        to?.let { params.add("to=${it.toEpochMilliseconds()}") }
        return params.joinToString("&")
    }
}

/**
 * A compact date range picker showing filter chips for each predefined period.
 */
@Composable
fun DateRangeSelector(
    selectedPeriod: DateRangePeriod,
    onPeriodChanged: (DateRangePeriod) -> Unit,
    modifier: Modifier = Modifier,
    showAllPeriods: Boolean = false,
) {
    val periods = if (showAllPeriods) DateRangePeriod.entries
    else listOf(
        DateRangePeriod.TODAY,
        DateRangePeriod.LAST_7_DAYS,
        DateRangePeriod.LAST_30_DAYS,
        DateRangePeriod.LAST_90_DAYS,
        DateRangePeriod.ALL_TIME,
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        periods.forEach { period ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onPeriodChanged(period) },
                label = { Text(period.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
