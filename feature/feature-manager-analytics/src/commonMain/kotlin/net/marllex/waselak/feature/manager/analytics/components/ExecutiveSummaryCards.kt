package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.ExecutiveSummary
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState

@Composable
fun ExecutiveSummaryCards(
    state: SectionState<ExecutiveSummary>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = "Executive Summary",
        state = state,
        onRetry = onRetry,
        modifier = modifier,
    ) { data ->
        // Row 1: Revenue, Orders, AOV
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = "Revenue",
                value = formatCurrency(data.current.totalRevenue),
                changePercent = data.revenueChangePercent,
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = "Orders",
                value = formatNumber(data.current.totalOrders),
                changePercent = data.ordersChangePercent,
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = "Avg Order",
                value = formatCurrency(data.current.averageOrderValue),
                changePercent = data.aovChangePercent,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        // Row 2: Tax, Delivery Fees, Active Orders, Attendance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = "Tax",
                value = formatCurrency(data.current.totalTax),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = "Delivery Fees",
                value = formatCurrency(data.current.totalDeliveryFees),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = "Active Orders",
                value = data.activeOrders.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = "Attendance Today",
                value = data.attendanceToday.toString(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.weight(2f))
        }
    }
}
