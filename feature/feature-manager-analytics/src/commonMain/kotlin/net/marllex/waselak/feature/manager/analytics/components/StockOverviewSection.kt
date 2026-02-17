package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.StockOverview
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState

@Composable
fun StockOverviewSection(
    state: SectionState<StockOverview>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = "Stock Overview",
        state = state,
        onRetry = onRetry,
        modifier = modifier,
    ) { data ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(label = "Stock Value", value = formatCurrency(data.totalStockValue), modifier = Modifier.weight(1f))
            KpiCard(label = "Selling Value", value = formatCurrency(data.totalSellingValue), modifier = Modifier.weight(1f))
            KpiCard(label = "Profit Potential", value = formatCurrency(data.potentialProfit), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(label = "Total Items", value = data.totalItems.toString(), modifier = Modifier.weight(1f))
            KpiCard(label = "Low Stock", value = data.lowStockItems.size.toString(), modifier = Modifier.weight(1f))
            KpiCard(label = "Out of Stock", value = data.outOfStockItems.size.toString(), modifier = Modifier.weight(1f))
        }

        // Out of stock items
        if (data.outOfStockItems.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Out of Stock",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            data.outOfStockItems.take(5).forEach { item ->
                Text("• ${item.itemName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        // Low stock items
        if (data.lowStockItems.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Low Stock", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            data.lowStockItems.take(5).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("• ${item.itemName}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${item.quantity}/${item.minQuantity} ${item.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Stock movement
        if (data.movementSummary.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Stock Movement (14 days)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SimpleBarChart(
                items = data.movementSummary.takeLast(7).map {
                    it.date.takeLast(5) to (it.added - it.deducted).toDouble()
                },
            )
        }
    }
}
