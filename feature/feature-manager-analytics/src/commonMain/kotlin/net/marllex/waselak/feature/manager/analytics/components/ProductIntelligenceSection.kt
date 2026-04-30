package net.marllex.waselak.feature.manager.analytics.components

import net.marllex.waselak.core.common.format.kFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.ProductIntelligence
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*

@Composable
fun ProductIntelligenceSection(
    state: SectionState<ProductIntelligence>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = stringResource(Res.string.product_intelligence),
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        description = stringResource(Res.string.product_intelligence_hint),
    ) { data ->
        // Top Selling
        if (data.topSelling.isNotEmpty()) {
            Text(stringResource(Res.string.top_selling), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            data.topSelling.take(5).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(item.itemName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(stringResource(Res.string.item_sold_count, item.quantitySold), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Text(formatCurrency(item.revenue), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Revenue by Category
        if (data.revenueByCategory.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.revenue_by_category), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            SimpleBarChart(
                items = data.revenueByCategory.map { it.categoryName to it.revenue },
            )
        }

        // Low Margin Warnings
        if (data.lowMarginWarnings.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(Res.string.low_margin_items),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(4.dp))
            data.lowMarginWarnings.take(5).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(item.itemName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        "${kFormat("%.1f", item.profitMargin)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
