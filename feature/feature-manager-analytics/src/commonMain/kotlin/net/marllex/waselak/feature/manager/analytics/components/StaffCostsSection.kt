package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.StaffCostsAnalytics
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*

@Composable
fun StaffCostsSection(
    state: SectionState<StaffCostsAnalytics>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = stringResource(Res.string.staff_costs),
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        description = stringResource(Res.string.staff_costs_hint),
    ) { data ->
        // Row 1: Total Salaries, Total Overtime, Total Compensation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = stringResource(Res.string.total_salaries),
                value = formatCurrency(data.totalSalaries),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.total_overtime),
                value = formatCurrency(data.totalOvertime),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.total_compensation),
                value = formatCurrency(data.totalCompensation),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Row 2: Paid, Unpaid, Workers Count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiCard(
                label = stringResource(Res.string.paid_salaries),
                value = formatCurrency(data.paidAmount),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.unpaid_salaries),
                value = formatCurrency(data.unpaidAmount),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.workers_count),
                value = formatNumber(data.workersCount),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Overtime percentage indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(Res.string.overtime_percentage),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "${String.format(java.util.Locale.US, "%.1f", data.overtimePercentage)}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (data.overtimePercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

        // Paid vs Unpaid visual indicator
        if (data.totalCompensation > 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val paidPercent = if (data.totalCompensation > 0) (data.paidAmount / data.totalCompensation * 100) else 0.0
                Icon(
                    if (paidPercent >= 100.0) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (paidPercent >= 100.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(Res.string.paid_percentage),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            "${String.format(java.util.Locale.US, "%.1f", paidPercent)}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (paidPercent >= 100.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (paidPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = if (paidPercent >= 100.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }

        // Overtime hours summary
        if (data.overtimeHours > 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(Res.string.overtime_hours_total, String.format(java.util.Locale.US, "%.1f", data.overtimeHours)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Top overtime workers
        if (data.topOvertimeWorkers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.top_overtime_workers),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            data.topOvertimeWorkers.forEachIndexed { index, worker ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${index + 1}. ${worker.workerName}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${String.format(java.util.Locale.US, "%.1f", worker.overtimeHours)}h",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatCurrency(worker.overtimeAmount),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
