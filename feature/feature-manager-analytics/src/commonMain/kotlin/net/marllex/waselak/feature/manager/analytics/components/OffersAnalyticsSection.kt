package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.OffersAnalytics
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*

@Composable
fun OffersAnalyticsSection(
    state: SectionState<OffersAnalytics>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = stringResource(Res.string.offers_analytics),
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        description = stringResource(Res.string.offers_analytics_hint),
    ) { data ->
        // KPI cards row 1: total offers, active, total uses
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = stringResource(Res.string.total_offers_count),
                value = formatNumber(data.totalOffers),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.active_offers_count),
                value = formatNumber(data.activeOffers),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.total_offer_uses),
                value = formatNumber(data.totalOfferUses),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        // KPI cards row 2: avg discount per use
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = stringResource(Res.string.avg_discount_per_use),
                value = formatCurrency(data.averageDiscountPerUse),
                modifier = Modifier.weight(1f),
            )
        }

        // Usage Trend chart
        if (data.offerUsageTrend.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.offer_usage_trend),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            SimpleBarChart(
                items = data.offerUsageTrend.map { it.date to it.usageCount.toDouble() },
            )
        }

        // Top Offers list
        if (data.topOffers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.top_offers),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            val usesSuffix = stringResource(Res.string.uses_label)
            data.topOffers.take(5).forEachIndexed { index, offer ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${index + 1}. ${offer.offerName}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${offer.usageCount} $usesSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
