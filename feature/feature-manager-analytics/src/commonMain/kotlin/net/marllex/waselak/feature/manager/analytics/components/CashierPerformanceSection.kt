package net.marllex.waselak.feature.manager.analytics.components

import net.marllex.waselak.core.common.format.kFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.CashierPerformanceV2
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun CashierPerformanceSection(
    state: SectionState<List<CashierPerformanceV2>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = stringResource(Res.string.cashier_performance),
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        description = stringResource(Res.string.cashier_performance_hint),
    ) { data ->
        if (data.isEmpty()) {
            Text(stringResource(Res.string.no_cashier_data), style = MaterialTheme.typography.bodyMedium)
            return@SectionContainer
        }

        // Table header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.name), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
            Text(stringResource(Res.string.orders), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(stringResource(Res.string.revenue), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(stringResource(Res.string.cancel_percent), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        data.sortedByDescending { it.revenue }.forEachIndexed { index, cashier ->
            val rowBg = if (index % 2 == 0)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surface

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    cashier.cashierName,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1.5f),
                )
                Text(
                    cashier.orderCount.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatCurrency(cashier.revenue),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${kFormat("%.1f", cashier.cancellationRate)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cashier.cancellationRate > 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
