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

@Composable
fun PeakTimeSection(
    state: SectionState<PeakTimeAnalysis>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = "Peak Time Analysis",
        state = state,
        onRetry = onRetry,
        modifier = modifier,
    ) { data ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = "Busiest Hour",
                value = "${data.busiestHour}:00",
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = "Busiest Day",
                value = data.busiestDay,
                modifier = Modifier.weight(1f),
            )
        }

        if (data.hourlyData.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Hourly Orders", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SimpleBarChart(
                items = data.hourlyData
                    .filter { it.orderCount > 0 }
                    .map { "${it.hour}h" to it.orderCount.toDouble() },
            )
        }

        if (data.heatmap.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Weekly Heatmap", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            HeatmapChart(
                data = data.heatmap.map { Triple(it.dayOfWeek, it.hour, it.orderCount) },
            )
        }

        if (data.dayOfWeek.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Revenue by Day", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SimpleBarChart(
                items = data.dayOfWeek.map { it.name to it.revenue },
            )
        }
    }
}
