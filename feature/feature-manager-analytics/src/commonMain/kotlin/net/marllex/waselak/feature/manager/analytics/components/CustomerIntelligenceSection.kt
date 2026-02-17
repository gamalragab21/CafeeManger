package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.CustomerIntelligence
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState

@Composable
fun CustomerIntelligenceSection(
    state: SectionState<CustomerIntelligence>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = "Customer Intelligence",
        state = state,
        onRetry = onRetry,
        modifier = modifier,
    ) { data ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(label = "Total", value = formatNumber(data.totalCustomers), modifier = Modifier.weight(1f))
            KpiCard(label = "New %", value = "${String.format("%.1f", data.newCustomersPercent)}%", modifier = Modifier.weight(1f))
            KpiCard(label = "Returning %", value = "${String.format("%.1f", data.returningCustomersPercent)}%", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(label = "Avg Spend", value = formatCurrency(data.averageSpend), modifier = Modifier.weight(1f))
            KpiCard(label = "Lifetime Value", value = formatCurrency(data.lifetimeValue), modifier = Modifier.weight(1f))
        }

        // Top Customers
        if (data.topCustomers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Top Customers", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            data.topCustomers.take(5).forEachIndexed { index, customer ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${index + 1}. ${customer.customerName}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${customer.orderCount} orders • ${formatCurrency(customer.totalSpent)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Frequency Buckets
        if (data.frequencyBuckets.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Order Frequency", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            val labelMap = mapOf(
                "1_order" to "1 order",
                "2_5_orders" to "2-5 orders",
                "6_10_orders" to "6-10 orders",
                "11_plus_orders" to "11+ orders",
            )
            SimpleBarChart(
                items = data.frequencyBuckets.map { (key, value) ->
                    (labelMap[key] ?: key) to value.toDouble()
                },
            )
        }
    }
}
