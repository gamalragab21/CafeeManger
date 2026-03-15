package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.SupplierAnalytics
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*

@Composable
fun SupplierAnalyticsSection(
    state: SectionState<SupplierAnalytics>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = stringResource(Res.string.supplier_analytics),
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        description = stringResource(Res.string.supplier_analytics_hint),
    ) { data ->
        // Row 1: Total Suppliers, Active, Total POs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = stringResource(Res.string.total_suppliers),
                value = formatNumber(data.totalSuppliers),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.active_suppliers_count),
                value = formatNumber(data.activeSuppliers),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.total_purchase_orders),
                value = formatNumber(data.totalPurchaseOrders),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Row 2: Total Spent, Avg Order Value
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = stringResource(Res.string.total_spent),
                value = formatCurrency(data.totalSpent),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.avg_po_value),
                value = formatCurrency(data.averageOrderValue),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Row 3: Pending, Received
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = stringResource(Res.string.pending_po),
                value = formatNumber(data.pendingOrders),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.received_po),
                value = formatNumber(data.receivedOrders),
                modifier = Modifier.weight(1f),
            )
        }

        // Top Suppliers
        if (data.topSuppliers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.top_suppliers_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            data.topSuppliers.forEachIndexed { index, supplier ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Store,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${index + 1}. ${supplier.supplierName}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${supplier.totalOrders} ${stringResource(Res.string.orders_suffix)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatCurrency(supplier.totalSpent),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // Top Purchased Items
        if (data.topItems.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.top_purchased_items),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            data.topItems.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Inventory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${index + 1}. ${item.itemName}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${String.format(java.util.Locale.US, "%.1f", item.totalQuantity)} ${item.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatCurrency(item.totalCost),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // Monthly Purchase Trend
        if (data.monthlyTrend.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.monthly_purchase_trend),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            SimpleBarChart(
                items = data.monthlyTrend.map { it.month to it.total },
            )
        }
    }
}
