package net.marllex.waselak.admin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.marllex.waselak.admin.network.VendorAnalytics
import net.marllex.waselak.admin.viewmodel.AnalyticsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = koinViewModel()
) {
    val analytics by viewModel.analytics.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAnalytics()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && analytics == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (analytics != null) {
            val data = analytics!!
            val summary = data.summary
            val planDist = data.plan_distribution

            val activeText = stringResource(Res.string.active)
            val suspendedText = stringResource(Res.string.suspended)

            BoxWithConstraints {
                val isCompact = maxWidth < 600.dp

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    item {
                        Text(
                            text = stringResource(Res.string.analytics_overview),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Summary Stats Section
                    item {
                        Text(
                            text = stringResource(Res.string.summary),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (isCompact) {
                        // Phone: single column cards
                        item {
                            AnalyticsStatCard(
                                title = stringResource(Res.string.total_vendors),
                                value = "${summary.total_vendors}",
                                subtitle = "${summary.active_vendors} $activeText / ${summary.suspended_vendors} $suspendedText",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AnalyticsStatCard(
                                    title = stringResource(Res.string.orders_today),
                                    value = "${summary.orders_today}",
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsStatCard(
                                    title = stringResource(Res.string.orders_this_month),
                                    value = "${summary.orders_this_month}",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AnalyticsStatCard(
                                    title = stringResource(Res.string.revenue_today),
                                    value = "EGP ${"%.2f".format(summary.revenue_today)}",
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsStatCard(
                                    title = stringResource(Res.string.revenue_this_month),
                                    value = "EGP ${"%.2f".format(summary.revenue_this_month)}",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AnalyticsStatCard(
                                    title = stringResource(Res.string.users),
                                    value = "${summary.active_users}/${summary.total_users}",
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsStatCard(
                                    title = stringResource(Res.string.workers),
                                    value = "${summary.active_workers}/${summary.total_workers}",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        // Tablet/Desktop: paired rows
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AnalyticsStatCard(
                                    title = stringResource(Res.string.total_vendors),
                                    value = "${summary.total_vendors}",
                                    subtitle = "${summary.active_vendors} $activeText / ${summary.suspended_vendors} $suspendedText",
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsStatCard(
                                    title = stringResource(Res.string.orders_today),
                                    value = "${summary.orders_today}",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AnalyticsStatCard(
                                    title = stringResource(Res.string.revenue_today),
                                    value = "EGP ${"%.2f".format(summary.revenue_today)}",
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsStatCard(
                                    title = stringResource(Res.string.revenue_this_month),
                                    value = "EGP ${"%.2f".format(summary.revenue_this_month)}",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AnalyticsStatCard(
                                    title = stringResource(Res.string.orders_this_month),
                                    value = "${summary.orders_this_month}",
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsStatCard(
                                    title = stringResource(Res.string.users),
                                    value = "${summary.active_users} / ${summary.total_users}",
                                    subtitle = "$activeText / Total",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AnalyticsStatCard(
                                    title = stringResource(Res.string.workers),
                                    value = "${summary.active_workers} / ${summary.total_workers}",
                                    subtitle = "$activeText / Total",
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsStatCard(
                                    title = stringResource(Res.string.plan_distribution),
                                    value = "",
                                    subtitle = "Starter: ${planDist.starter} | Business: ${planDist.business} | Enterprise: ${planDist.enterprise}",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Vendor Breakdown Section
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.vendor_breakdown),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    if (isCompact) {
                        // Phone: card-based vendor display
                        items(data.vendors, key = { it.vendor_id }) { vendor ->
                            VendorAnalyticsCard(vendor)
                        }
                    } else {
                        // Table Header
                        item {
                            VendorTableHeader()
                        }

                        // Vendor rows
                        items(data.vendors, key = { it.vendor_id }) { vendor ->
                            VendorTableRow(vendor)
                        }
                    }

                    // Bottom spacing
                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.no_data),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.loadAnalytics() }) {
                    Text(stringResource(Res.string.retry))
                }
            }
        }
    }
}

@Composable
private fun AnalyticsStatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (value.isNotEmpty()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VendorAnalyticsCard(vendor: VendorAnalytics) {
    val statusColor = if (vendor.is_suspended)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.primary

    val activeText = stringResource(Res.string.active)
    val suspendedText = stringResource(Res.string.suspended)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = vendor.vendor_name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (vendor.is_suspended) suspendedText else activeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(Res.string.orders_today),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${vendor.orders_today}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(Res.string.revenue_month),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "EGP ${"%.2f".format(vendor.revenue_this_month)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(Res.string.workers),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${vendor.active_workers}/${vendor.total_workers}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun VendorTableHeader() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.vendor),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(2f)
            )
            Text(
                text = stringResource(Res.string.status),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(Res.string.orders_today),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(Res.string.revenue_month),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.5f),
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(Res.string.workers),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun VendorTableRow(vendor: VendorAnalytics) {
    val statusColor = if (vendor.is_suspended)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.primary

    val activeText = stringResource(Res.string.active)
    val suspendedText = stringResource(Res.string.suspended)

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = vendor.vendor_name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(2f)
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (vendor.is_suspended) suspendedText else activeText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
            Text(
                text = "${vendor.orders_today}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "EGP ${"%.2f".format(vendor.revenue_this_month)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1.5f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "${vendor.active_workers}/${vendor.total_workers}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }
    }
}
