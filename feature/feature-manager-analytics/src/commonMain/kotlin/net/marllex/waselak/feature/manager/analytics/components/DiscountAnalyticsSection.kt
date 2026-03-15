package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.DiscountAnalytics
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*

@Composable
fun DiscountAnalyticsSection(
    state: SectionState<DiscountAnalytics>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = stringResource(Res.string.discount_analytics),
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        description = stringResource(Res.string.discount_analytics_hint),
    ) { data ->
        // KPI cards row 1: discounted orders, total discount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = stringResource(Res.string.orders_with_discount),
                value = formatNumber(data.totalOrdersWithDiscount),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.total_discount_given),
                value = formatCurrency(data.totalDiscountGiven),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        // KPI cards row 2: avg per order, discount rate
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = stringResource(Res.string.avg_discount_per_order),
                value = formatCurrency(data.averageDiscountPerOrder),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.discount_rate_label),
                value = "${String.format(java.util.Locale.US, "%.1f", data.discountRate)}%",
                modifier = Modifier.weight(1f),
            )
        }

        // Donut chart for breakdown by type
        if (data.breakdown.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.discount_breakdown),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            val typeLabels = mapOf(
                "MANUAL" to stringResource(Res.string.discount_type_manual),
                "OFFER" to stringResource(Res.string.discount_type_offer),
                "POINTS" to stringResource(Res.string.discount_type_points),
            )
            DonutChart(
                segments = data.breakdown.map { (typeLabels[it.type] ?: it.type) to it.totalAmount.toFloat() },
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.tertiary,
                ),
            )
        }

        // Daily trend bar chart
        if (data.dailyTrend.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.discount_daily_trend),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            SimpleBarChart(
                items = data.dailyTrend.map {
                    it.date to (it.manualDiscount + it.offerDiscount + it.pointsDiscount)
                },
            )
        }
    }
}
