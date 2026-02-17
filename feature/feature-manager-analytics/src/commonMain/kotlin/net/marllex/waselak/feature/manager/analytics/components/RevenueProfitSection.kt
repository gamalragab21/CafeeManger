package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.RevenueProfit
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState

@Composable
fun RevenueProfitSection(
    state: SectionState<RevenueProfit>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = "Revenue & Profit",
        state = state,
        onRetry = onRetry,
        modifier = modifier,
    ) { data ->
        // Revenue metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(label = "Gross Revenue", value = formatCurrency(data.grossRevenue), modifier = Modifier.weight(1f))
            KpiCard(label = "Net Revenue", value = formatCurrency(data.netRevenue), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(label = "Tax", value = formatCurrency(data.totalTax), modifier = Modifier.weight(1f))
            KpiCard(label = "Delivery Fees", value = formatCurrency(data.totalDeliveryFees), modifier = Modifier.weight(1f))
        }

        // Payment method breakdown
        if (data.paymentMethods.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Payment Methods",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            data.paymentMethods.forEach { pm ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(pm.method, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${formatCurrency(pm.revenue)} (${pm.orderCount} orders)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Daily trend (simple text list since Vico charts require complex setup)
        if (data.dailyTrend.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Daily Revenue Trend",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            SimpleBarChart(
                items = data.dailyTrend.takeLast(14).map { it.date.takeLast(5) to it.revenue },
            )
        }
    }
}
