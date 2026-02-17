package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.TimePeriod

@Composable
fun GlobalFilterBar(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val periods = listOf(
        TimePeriod.TODAY to "Today",
        TimePeriod.YESTERDAY to "Yesterday",
        TimePeriod.THIS_WEEK to "This Week",
        TimePeriod.LAST_7_DAYS to "Last 7 Days",
        TimePeriod.LAST_14_DAYS to "Last 14 Days",
        TimePeriod.THIS_MONTH to "This Month",
        TimePeriod.LAST_MONTH to "Last Month",
        TimePeriod.LAST_3_MONTHS to "3 Months",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        periods.forEach { (period, label) ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(20.dp),
            )
        }
    }
}
