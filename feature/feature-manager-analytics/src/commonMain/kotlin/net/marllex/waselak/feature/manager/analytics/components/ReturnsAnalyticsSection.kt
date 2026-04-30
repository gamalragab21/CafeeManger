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
import net.marllex.waselak.core.model.ReturnsAnalytics
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private fun fmt(v: Double): String {
    val whole = v.toLong()
    val frac = ((v - whole) * 100).toLong()
    return "$whole.${frac.toString().padStart(2, '0')}"
}

@Composable
fun ReturnsAnalyticsSection(data: ReturnsAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.returns_and_exchanges),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))

            // Summary cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryChip(
                    label = stringResource(Res.string.refunds),
                    value = "${data.totalRefunds}",
                    amount = fmt(data.totalRefundedAmount),
                    color = Color(0xFFE53935),
                    modifier = Modifier.weight(1f),
                )
                SummaryChip(
                    label = stringResource(Res.string.exchanges),
                    value = "${data.totalExchanges}",
                    amount = fmt(data.totalExchangedAmount),
                    color = Color(0xFFFF9800),
                    modifier = Modifier.weight(1f),
                )
                SummaryChip(
                    label = stringResource(Res.string.items),
                    value = "${data.totalReturnedItems}",
                    amount = "",
                    color = Color(0xFF7B1FA2),
                    modifier = Modifier.weight(1f),
                )
            }

            // Returned items breakdown
            if (data.returnedItemsBreakdown.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.returned_items),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))

                data.returnedItemsBreakdown.take(10).forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.itemName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(Res.string.items_count_short, item.totalQuantity),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            fmt(item.totalAmount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE53935),
                        )
                    }
                }
            }

            // Exchange items
            if (data.exchangeItems.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.exchange_replacements),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF9800),
                )
                Spacer(Modifier.height(8.dp))

                data.exchangeItems.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.itemName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "x${item.quantity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            fmt(item.price),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF9800),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(
    label: String,
    value: String,
    amount: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (amount.isNotBlank()) {
                Text(amount, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}
