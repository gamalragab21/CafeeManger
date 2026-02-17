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
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun GlobalFilterBar(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val periods = listOf(
        TimePeriod.TODAY to stringResource(Res.string.today),
        TimePeriod.YESTERDAY to stringResource(Res.string.yesterday),
        TimePeriod.THIS_WEEK to stringResource(Res.string.this_week),
        TimePeriod.LAST_7_DAYS to stringResource(Res.string.last_7_days),
        TimePeriod.LAST_14_DAYS to stringResource(Res.string.last_14_days),
        TimePeriod.THIS_MONTH to stringResource(Res.string.this_month),
        TimePeriod.LAST_MONTH to stringResource(Res.string.last_month),
        TimePeriod.LAST_3_MONTHS to stringResource(Res.string.last_3_months),
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
