package net.marllex.waselak.feature.manager.analytics.components

import net.marllex.waselak.core.common.format.kFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.AnalyticsAlert
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.SectionState
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*

@Composable
fun AlertsSection(
    state: SectionState<List<AnalyticsAlert>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionContainer(
        title = stringResource(Res.string.alerts_and_risks),
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        description = stringResource(Res.string.alerts_hint),
        initiallyExpanded = true,
    ) { data ->
        if (data.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(Res.string.no_active_alerts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            return@SectionContainer
        }

        data.forEachIndexed { index, alert ->
            val isCritical = alert.severity == "CRITICAL"
            val containerColor = if (isCritical)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.secondaryContainer

            // Localize title based on alert type
            val localizedTitle = localizeAlertTitle(alert.type) ?: alert.title
            // Localize severity
            val localizedSeverity = localizeAlertSeverity(alert.severity)
            // Localize message
            val localizedMessage = localizeAlertMessage(alert.type, alert.value, alert.threshold) ?: alert.message

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = containerColor),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = localizedTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = localizedSeverity,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = localizedMessage,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (index < data.size - 1) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun localizeAlertTitle(type: String): String? = when (type) {
    "REVENUE_DROP" -> stringResource(Res.string.alert_title_revenue_drop)
    "HIGH_CANCELLATION" -> stringResource(Res.string.alert_title_high_cancellation)
    "HIGH_REFUND_RATE" -> stringResource(Res.string.alert_title_high_refund)
    "OUT_OF_STOCK" -> stringResource(Res.string.alert_title_out_of_stock)
    "LOW_STOCK" -> stringResource(Res.string.alert_title_low_stock)
    else -> null
}

@Composable
private fun localizeAlertSeverity(severity: String): String = when (severity) {
    "CRITICAL" -> stringResource(Res.string.alert_severity_critical)
    "WARNING" -> stringResource(Res.string.alert_severity_warning)
    else -> severity
}

@Composable
private fun localizeAlertMessage(type: String, value: Double, threshold: Double): String? = when (type) {
    "REVENUE_DROP" -> {
        // value = currentRevenue, threshold = previousRevenue * 0.7
        val dropPct = if (threshold > 0) {
            val previousRevenue = threshold / 0.7
            ((previousRevenue - value) / previousRevenue * 100.0)
        } else 0.0
        stringResource(Res.string.alert_msg_revenue_drop, kFormat("%.1f", dropPct))
    }
    "HIGH_CANCELLATION" -> stringResource(Res.string.alert_msg_high_cancellation, kFormat("%.1f", value))
    "HIGH_REFUND_RATE" -> stringResource(Res.string.alert_msg_high_refund, kFormat("%.1f", value))
    "OUT_OF_STOCK" -> stringResource(Res.string.alert_msg_out_of_stock, value.toInt())
    "LOW_STOCK" -> stringResource(Res.string.alert_msg_low_stock, value.toInt())
    else -> null
}
