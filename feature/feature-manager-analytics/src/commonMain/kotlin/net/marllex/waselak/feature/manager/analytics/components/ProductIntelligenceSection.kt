package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.ProductIntelligence
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState

@Composable
fun ProductIntelligenceSection(
    state: SectionState<ProductIntelligence>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = "Product Intelligence",
        state = state,
        onRetry = onRetry,
        modifier = modifier,
    ) { data ->
        // Top Selling
        if (data.topSelling.isNotEmpty()) {
            Text("Top Selling", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            data.topSelling.take(5).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(item.itemName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${item.quantitySold} sold", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Text(formatCurrency(item.revenue), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Revenue by Category
        if (data.revenueByCategory.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Revenue by Category", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            SimpleBarChart(
                items = data.revenueByCategory.map { it.categoryName to it.revenue },
            )
        }

        // Low Margin Warnings
        if (data.lowMarginWarnings.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "⚠ Low Margin Items",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            data.lowMarginWarnings.take(5).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(item.itemName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        "${String.format("%.1f", item.profitMargin)}% margin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
