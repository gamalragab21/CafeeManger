package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.PeakTimeAnalysis
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun PeakTimeSection(
    state: SectionState<PeakTimeAnalysis>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = stringResource(Res.string.peak_time_analysis),
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        description = stringResource(Res.string.peak_time_hint),
    ) { data ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = stringResource(Res.string.busiest_hour),
                value = "${data.busiestHour}:00",
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.busiest_day),
                value = data.busiestDay,
                modifier = Modifier.weight(1f),
            )
        }

        if (data.hourlyData.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.hourly_orders), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SimpleBarChart(
                items = data.hourlyData
                    .filter { it.orderCount > 0 }
                    .map { "${it.hour}h" to it.orderCount.toDouble() },
            )
        }

        if (data.heatmap.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.weekly_heatmap), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            HeatmapChart(
                data = data.heatmap.map { Triple(it.dayOfWeek, it.hour, it.orderCount) },
            )
        }

        if (data.dayOfWeek.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.revenue_by_day), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SimpleBarChart(
                items = data.dayOfWeek.map { it.name to it.revenue },
            )
        }
    }
}
