package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.InstallmentAnalytics
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState
import org.jetbrains.compose.resources.stringResource
import waselak.core.core_ui.generated.resources.Res as CoreRes
import waselak.core.core_ui.generated.resources.*

@Composable
fun InstallmentAnalyticsSection(
    state: SectionState<InstallmentAnalytics>,
    onRetry: () -> Unit,
) {
    SectionContainer(
        title = stringResource(CoreRes.string.installment_analytics),
        state = state,
        onRetry = onRetry,
        initiallyExpanded = true,
    ) { data ->
        InstallmentAnalyticsContent(data)
    }
}

@Composable
private fun InstallmentAnalyticsContent(data: InstallmentAnalytics) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Plan counts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InstallmentKpiCard(
                label = stringResource(CoreRes.string.total_plans),
                value = "${data.totalPlans}",
                color = Color(0xFF2196F3),
                modifier = Modifier.weight(1f),
            )
            InstallmentKpiCard(
                label = stringResource(CoreRes.string.active_plans),
                value = "${data.activePlans}",
                color = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InstallmentKpiCard(
                label = stringResource(CoreRes.string.completed_plans),
                value = "${data.completedPlans}",
                color = Color(0xFF2196F3),
                modifier = Modifier.weight(1f),
            )
            InstallmentKpiCard(
                label = stringResource(CoreRes.string.defaulted_plans),
                value = "${data.defaultedPlans}",
                color = if (data.defaultedPlans > 0) Color(0xFFF44336) else Color(0xFF4CAF50),
                modifier = Modifier.weight(1f),
            )
        }

        // Revenue
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InstallmentKpiCard(
                label = stringResource(CoreRes.string.total_installment_revenue),
                value = "%.2f".format(data.totalRevenue),
                color = Color(0xFF2196F3),
                modifier = Modifier.weight(1f),
            )
            InstallmentKpiCard(
                label = stringResource(CoreRes.string.collected_revenue),
                value = "%.2f".format(data.collectedRevenue),
                color = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InstallmentKpiCard(
                label = stringResource(CoreRes.string.pending_revenue),
                value = "%.2f".format(data.pendingRevenue),
                color = Color(0xFFFF9800),
                modifier = Modifier.weight(1f),
            )
            InstallmentKpiCard(
                label = stringResource(CoreRes.string.overdue_revenue),
                value = "%.2f".format(data.overdueRevenue),
                color = if (data.overdueRevenue > 0) Color(0xFFF44336) else Color(0xFF4CAF50),
                modifier = Modifier.weight(1f),
            )
        }

        // Late fees
        InstallmentKpiCard(
            label = stringResource(CoreRes.string.late_fees_collected),
            value = "%.2f".format(data.lateFeesCollected),
            color = if (data.lateFeesCollected > 0) Color(0xFFFF9800) else Color(0xFF4CAF50),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InstallmentKpiCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
