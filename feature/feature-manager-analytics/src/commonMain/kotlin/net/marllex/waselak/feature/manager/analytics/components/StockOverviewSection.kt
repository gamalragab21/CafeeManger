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
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*

@Composable
fun StockOverviewSection(
    state: SectionState<StockOverview>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = stringResource(Res.string.stock_overview),
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        description = stringResource(Res.string.stock_overview_hint),
    ) { data ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(label = stringResource(Res.string.stock_value), value = formatCurrency(data.totalStockValue), modifier = Modifier.weight(1f))
            KpiCard(label = stringResource(Res.string.selling_value), value = formatCurrency(data.totalSellingValue), modifier = Modifier.weight(1f))
            KpiCard(label = stringResource(Res.string.profit_potential), value = formatCurrency(data.potentialProfit), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(label = stringResource(Res.string.total_items), value = data.totalItems.toString(), modifier = Modifier.weight(1f))
            KpiCard(label = stringResource(Res.string.low_stock), value = data.lowStockItems.size.toString(), modifier = Modifier.weight(1f))
            KpiCard(label = stringResource(Res.string.out_of_stock), value = data.outOfStockItems.size.toString(), modifier = Modifier.weight(1f))
        }

        // Out of stock items
        if (data.outOfStockItems.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.out_of_stock),
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
            Text(stringResource(Res.string.low_stock), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
            Text(stringResource(Res.string.stock_movement_14_days), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SimpleBarChart(
                items = data.movementSummary.takeLast(7).map {
                    it.date.takeLast(5) to (it.added - it.deducted).toDouble()
                },
            )
        }
    }
}
