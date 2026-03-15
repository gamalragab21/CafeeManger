package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.LoyaltyAnalytics
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*

@Composable
fun LoyaltyAnalyticsSection(
    state: SectionState<LoyaltyAnalytics>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = stringResource(Res.string.loyalty_analytics),
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        description = stringResource(Res.string.loyalty_analytics_hint),
    ) { data ->
        // KPI cards row 1: total earned, total redeemed, outstanding
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = stringResource(Res.string.total_points_earned),
                value = formatNumber(data.totalPointsEarned.toInt()),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.total_points_redeemed),
                value = formatNumber(data.totalPointsRedeemed.toInt()),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.outstanding_points),
                value = formatNumber(data.totalPointsOutstanding.toInt()),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        // KPI cards row 2: active customers, redemption rate, points revenue
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = stringResource(Res.string.active_loyalty_customers),
                value = formatNumber(data.activeLoyaltyCustomers),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.redemption_rate_label),
                value = "${String.format(java.util.Locale.US, "%.1f", data.redemptionRate)}%",
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.points_revenue_label),
                value = formatCurrency(data.pointsToRevenue),
                modifier = Modifier.weight(1f),
            )
        }

        // Daily earned vs redeemed trend
        if (data.dailyTrend.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.loyalty_trend),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            SimpleBarChart(
                items = data.dailyTrend.map { it.date to it.pointsEarned.toDouble() },
                barColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
