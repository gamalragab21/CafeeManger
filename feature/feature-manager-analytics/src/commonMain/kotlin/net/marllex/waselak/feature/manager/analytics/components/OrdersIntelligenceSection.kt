package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.OrdersIntelligence
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun OrdersIntelligenceSection(
    state: SectionState<OrdersIntelligence>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = stringResource(Res.string.orders_intelligence),
        state = state,
        onRetry = onRetry,
        modifier = modifier,
    ) { data ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(label = stringResource(Res.string.total), value = formatNumber(data.totalOrders), modifier = Modifier.weight(1f))
            KpiCard(label = stringResource(Res.string.completed), value = formatNumber(data.completedOrders), modifier = Modifier.weight(1f))
            KpiCard(label = stringResource(Res.string.cancelled), value = formatNumber(data.cancelledOrders), modifier = Modifier.weight(1f))
        }

        if (data.channelBreakdown.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.channel_breakdown), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.error,
            )
            DonutChart(
                segments = data.channelBreakdown.map { it.channel to it.count.toFloat() },
                colors = colors,
            )
        }

        if (data.dailyTrend.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.daily_order_trend), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SimpleBarChart(
                items = data.dailyTrend.takeLast(14).map { it.date.takeLast(5) to it.total.toDouble() },
            )
        }
    }
}
