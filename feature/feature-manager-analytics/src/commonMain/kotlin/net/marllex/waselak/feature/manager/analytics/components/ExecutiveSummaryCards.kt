package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.ExecutiveSummary
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExecutiveSummaryCards(
    state: SectionState<ExecutiveSummary>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = stringResource(Res.string.executive_summary),
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
                label = stringResource(Res.string.revenue),
                value = formatCurrency(data.current.totalRevenue),
                changePercent = data.revenueChangePercent,
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.orders),
                value = formatNumber(data.current.totalOrders),
                changePercent = data.ordersChangePercent,
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.avg_order),
                value = formatCurrency(data.current.averageOrderValue),
                changePercent = data.aovChangePercent,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        // Row 2: Delivery Fees, Active Orders, Attendance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = stringResource(Res.string.delivery_fees),
                value = formatCurrency(data.current.totalDeliveryFees),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.active_orders),
                value = data.activeOrders.toString(),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.attendance_today),
                value = data.attendanceToday.toString(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
