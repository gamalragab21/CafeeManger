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
import net.marllex.waselak.core.model.CreditAnalytics
import org.jetbrains.compose.resources.stringResource
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@Composable
fun CreditAnalyticsSection(data: CreditAnalytics) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(Res.string.credit_analytics),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CreditKpiCard(
                label = stringResource(Res.string.total_outstanding),
                value = "${"%.2f".format(data.totalOutstanding)}",
                color = if (data.totalOutstanding > 0) Color(0xFFF44336) else Color(0xFF4CAF50),
                modifier = Modifier.weight(1f),
            )
            CreditKpiCard(
                label = stringResource(Res.string.credit_utilization),
                value = "${"%.1f".format(data.utilizationPercent)}%",
                color = when {
                    data.utilizationPercent > 80 -> Color(0xFFF44336)
                    data.utilizationPercent > 50 -> Color(0xFFFF9800)
                    else -> Color(0xFF4CAF50)
                },
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CreditKpiCard(
                label = stringResource(Res.string.total_charges_label),
                value = "${"%.2f".format(data.totalCharges)}",
                color = Color(0xFFF44336),
                modifier = Modifier.weight(1f),
            )
            CreditKpiCard(
                label = stringResource(Res.string.total_payments_label),
                value = "${"%.2f".format(data.totalPayments)}",
                color = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CreditKpiCard(
                label = stringResource(Res.string.credit_orders),
                value = "${data.creditOrdersCount}",
                color = Color(0xFF2196F3),
                modifier = Modifier.weight(1f),
            )
            CreditKpiCard(
                label = stringResource(Res.string.credit_revenue),
                value = "${"%.2f".format(data.creditOrdersRevenue)}",
                color = Color(0xFF2196F3),
                modifier = Modifier.weight(1f),
            )
        }

        if (data.topDebtors.isNotEmpty()) {
            Text(
                stringResource(Res.string.top_debtors),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            data.topDebtors.forEach { debtor ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(debtor.customerName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            debtor.customerPhone?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${"%.2f".format(debtor.balance)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF44336),
                            )
                            Text(
                                "/ ${"%.2f".format(debtor.creditLimit)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditKpiCard(
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
