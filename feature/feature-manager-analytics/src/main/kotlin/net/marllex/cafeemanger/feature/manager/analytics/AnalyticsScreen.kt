package net.marllex.cafeemanger.feature.manager.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.marllex.cafeemanger.core.model.AnalyticsSummary
import net.marllex.cafeemanger.core.model.DailyAnalytics
import net.marllex.cafeemanger.core.model.DeliveryPerformance
import net.marllex.cafeemanger.core.model.Settlements
import net.marllex.cafeemanger.core.ui.components.ErrorView
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analytics)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.summary == null -> LoadingIndicator()
            uiState.error != null -> ErrorView(message = uiState.error!!, onRetry = viewModel::loadAnalytics)
            uiState.summary != null -> AnalyticsContent(
                summary = uiState.summary!!,
                filteredSummary = uiState.filteredSummary,
                settlements = uiState.settlements,
                deliveryPerformance = uiState.deliveryPerformance,
                dailyData = uiState.dailyData,
                selectedStatus = uiState.selectedStatus,
                selectedChannel = uiState.selectedChannel,
                onApplyFilters = { status, channel ->
                    val from = uiState.fromDate
                    val to = uiState.toDate
                    viewModel.applyFilters(status, channel, from, to)
                },
                onClearFilters = viewModel::clearFilters,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun AnalyticsContent(
    summary: AnalyticsSummary,
    filteredSummary: AnalyticsSummary?,
    settlements: Settlements?,
    deliveryPerformance: List<DeliveryPerformance>,
    dailyData: List<DailyAnalytics>,
    selectedStatus: String?,
    selectedChannel: String?,
    onApplyFilters: (String?, String?) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStatusMenu by remember { mutableStateOf(false) }
    var showChannelMenu by remember { mutableStateOf(false) }

    val displaySummary = filteredSummary ?: summary

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Filters Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.filters),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        if (selectedStatus != null || selectedChannel != null) {
                            Button(onClick = onClearFilters) {
                                Text(stringResource(R.string.clear_filters))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Status Filter
                        Box {
                            FilterChip(
                                selected = selectedStatus != null,
                                onClick = { showStatusMenu = true },
                                label = { Text(selectedStatus ?: stringResource(R.string.status)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = showStatusMenu,
                                onDismissRequest = { showStatusMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.all)) },
                                    onClick = {
                                        showStatusMenu = false
                                        onApplyFilters(null, selectedChannel)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.completed)) },
                                    onClick = {
                                        showStatusMenu = false
                                        onApplyFilters("COMPLETED", selectedChannel)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.pending)) },
                                    onClick = {
                                        showStatusMenu = false
                                        onApplyFilters("PENDING", selectedChannel)
                                    }
                                )
                            }
                        }

                        // Channel Filter
                        Box {
                            FilterChip(
                                selected = selectedChannel != null,
                                onClick = { showChannelMenu = true },
                                label = {
                                    Text(
                                        selectedChannel ?: stringResource(R.string.channel)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = showChannelMenu,
                                onDismissRequest = { showChannelMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.all)) },
                                    onClick = {
                                        showChannelMenu = false
                                        onApplyFilters(selectedStatus, null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.dine_in)) },
                                    onClick = {
                                        showChannelMenu = false
                                        onApplyFilters(selectedStatus, "DINE_IN")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delivery)) },
                                    onClick = {
                                        showChannelMenu = false
                                        onApplyFilters(selectedStatus, "DELIVERY")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.takeaway)) },
                                    onClick = {
                                        showChannelMenu = false
                                        onApplyFilters(selectedStatus, "TAKEAWAY")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Summary Section
        item {
            Text(
                if (filteredSummary != null) "Filtered Summary" else stringResource(R.string.summary_last_30_days),
                style = MaterialTheme.typography.titleLarge
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = stringResource(R.string.total_orders),
                    value = displaySummary.totalOrders.toString(),
                    icon = Icons.Filled.Receipt,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = stringResource(R.string.revenue),
                    value = String.format("%.2f", displaySummary.totalRevenue),
                    icon = Icons.Filled.AttachMoney,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            MetricCard(
                title = stringResource(R.string.avg_order_value),
                value = String.format("%.2f", displaySummary.averageOrderValue),
                icon = Icons.Filled.TrendingUp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Settlements Section
        if (settlements != null && settlements.byPaymentMethod.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Payment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.settlements),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            items(settlements.byPaymentMethod.entries.toList()) { (method, data) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = method,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.order_count),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${data.orderCount}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.revenue),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format("%.2f", data.totalRevenue),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.total_tax),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format("%.2f", data.totalTax),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        // Delivery Performance Section
        if (deliveryPerformance.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocalShipping,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.delivery_performance),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            items(deliveryPerformance) { performance ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = performance.deliveryUserName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.orders_delivered),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${performance.orderCount}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.total_with_tax),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(
                                        "%.2f",
                                        performance.totalRevenue + performance.totalTax
                                    ),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.total_tax),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format("%.2f", performance.totalTax),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top Items Section
        if (displaySummary.topItems.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Top Items", style = MaterialTheme.typography.titleLarge)
            }

            items(displaySummary.topItems) { topItem ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = topItem.item.toString(),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.qty, topItem.quantitySold),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = String.format("%.2f", topItem.revenue),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Daily Breakdown
        if (dailyData.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.daily_breakdown),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            val maxRevenue = dailyData.maxOfOrNull { it.revenue } ?: 1.0
            items(dailyData) { daily ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = daily.date,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${daily.orders} orders",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (daily.revenue / maxRevenue).toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = String.format("%.2f", daily.revenue),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
