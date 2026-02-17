package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.DeliveryPerformanceV2
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState

@Composable
fun DeliveryPerformanceSection(
    state: SectionState<List<DeliveryPerformanceV2>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = "Delivery Performance",
        state = state,
        onRetry = onRetry,
        modifier = modifier,
    ) { data ->
        if (data.isEmpty()) {
            Text("No delivery data available", style = MaterialTheme.typography.bodyMedium)
            return@SectionContainer
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Driver", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
            Text("Completed", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Fees", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Revenue", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        HorizontalDivider()

        data.sortedByDescending { it.ordersCompleted }.forEachIndexed { index, driver ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(driver.driverName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.5f))
                Text(driver.ordersCompleted.toString(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(formatCurrency(driver.feesCollected), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    formatCurrency(driver.revenue),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            if (index < data.size - 1) HorizontalDivider(thickness = 0.5.dp)
        }
    }
}
