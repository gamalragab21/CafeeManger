package net.marllex.waselak.feature.manager.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import net.marllex.waselak.core.model.AnalyticsSummary
import net.marllex.waselak.core.model.DailyAnalytics
import net.marllex.waselak.core.model.DeliveryPerformance
import net.marllex.waselak.core.model.Settlements
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.koin.compose.viewmodel.koinViewModel
import net.marllex.waselak.core.common.extensions.formatEpochMs
import net.marllex.waselak.core.common.extensions.formatLocalDateTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.analytics)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {

                    IconButton(onClick = { viewModel.loadAnalytics() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
            )
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.summary == null -> LoadingIndicator()
            uiState.error != null -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadAnalytics
            )

            uiState.summary != null -> AnalyticsContent(
                summary = uiState.summary!!,
                filteredSummary = uiState.filteredSummary,
                settlements = uiState.settlements,
                deliveryPerformance = uiState.deliveryPerformance,
                cashierPerformance = uiState.cashierPerformance,
                dailyData = uiState.dailyData,
                selectedCashierId = uiState.selectedCashierId,
                selectedDeliveryUserId = uiState.selectedDeliveryUserId,
                fromDate = uiState.fromDate,
                toDate = uiState.toDate,
                onApplyFilters = { cashierId, deliveryUserId, from, to ->
                    viewModel.applyFilters(cashierId, deliveryUserId, from, to)
                },
                onClearFilters = viewModel::clearFilters,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalyticsContent(
    summary: AnalyticsSummary,
    filteredSummary: AnalyticsSummary?,
    settlements: Settlements?,
    deliveryPerformance: List<DeliveryPerformance>,
    cashierPerformance: List<DeliveryPerformance>,
    dailyData: List<DailyAnalytics>,
    selectedCashierId: String?,
    selectedDeliveryUserId: String?,
    fromDate: Long?,
    toDate: Long?,
    onApplyFilters: (String?, String?, Long?, Long?) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCashierMenu by remember { mutableStateOf(false) }
    var showDeliveryMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val displaySummary = filteredSummary ?: summary
    val cashierFilterLabel = selectedCashierId?.let { id ->
        cashierPerformance.find { it.deliveryUserId == id }?.deliveryUserName ?: id.takeLast(6)
    } ?: stringResource(Res.string.cashier)
    val deliveryFilterLabel = selectedDeliveryUserId?.let { id ->
        deliveryPerformance.find { it.deliveryUserId == id }?.deliveryUserName ?: id.takeLast(6)
    } ?: stringResource(Res.string.delivery_person)
    val now = System.currentTimeMillis()
    val sevenDays = now - 7L * 24 * 60 * 60 * 1000
    val thirtyDays = now - 30L * 24 * 60 * 60 * 1000
    val datePreset = when {
        fromDate == null && toDate == null -> "ALL"
        toDate != null && kotlin.math.abs(toDate - now) < 2 * 60 * 60 * 1000 && fromDate != null && kotlin.math.abs(
            fromDate - sevenDays
        ) < 2 * 60 * 60 * 1000 -> "7"

        toDate != null && kotlin.math.abs(toDate - now) < 2 * 60 * 60 * 1000 && fromDate != null && kotlin.math.abs(
            fromDate - thirtyDays
        ) < 2 * 60 * 60 * 1000 -> "30"

        else -> "CUSTOM"
    }
    val formatAmount: (Double) -> String = { amt -> String.format("%.2f EGP", amt) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Filters Section
             item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header: Title and Clear Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.FilterList,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(Res.string.filters),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (selectedCashierId != null || selectedDeliveryUserId != null || fromDate != null || toDate != null) {
                                    TextButton(onClick = onClearFilters) {
                                        Text(stringResource(Res.string.clear_filters))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Section 1: Date Range Presets (Flowing)
                            Text(
                                text = "Time Period",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            // FlowRow automatically handles screen width
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = 4 // Keeps it neat on tablets
                            ) {
                                val datePresets = listOf(
                                    Triple("ALL", stringResource(Res.string.all_time), null),
                                    Triple("7", stringResource(Res.string.last_7_days), sevenDays),
                                    Triple("30", stringResource(Res.string.last_30_days), thirtyDays)
                                )

                                datePresets.forEach { (key, label, start) ->
                                    FilterChip(
                                        selected = datePreset == key,
                                        onClick = { onApplyFilters(selectedCashierId, selectedDeliveryUserId, start, if (start != null) now else null) },
                                        label = { Text(label) }
                                    )
                                }

                                // Custom date chip with selected range display
                                val customLabel = if (datePreset == "CUSTOM" && fromDate != null && toDate != null) {
                                    "${fromDate.formatEpochMs("MMM dd")} - ${toDate.formatEpochMs("MMM dd")}"
                                } else {
                                    stringResource(Res.string.custom)
                                }

                                FilterChip(
                                    selected = datePreset == "CUSTOM",
                                    onClick = { showDatePicker = true },
                                    label = { Text(customLabel) },
                                    leadingIcon = {
                                        Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Section 2: Dropdowns (Side-by-side or Stacked)
                            Text(
                                text = "Assignees",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Cashier Dropdown
                                Box(modifier = Modifier.weight(1f)) {
                                    FilterChip(
                                        selected = selectedCashierId != null,
                                        onClick = { showCashierMenu = true },
                                        label = { Text(cashierFilterLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                                    )
                                    DropdownMenu(
                                        expanded = showCashierMenu,
                                        onDismissRequest = { showCashierMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.all)) },
                                            onClick = {
                                                showCashierMenu = false
                                                onApplyFilters(null, selectedDeliveryUserId, fromDate, toDate)
                                            }
                                        )
                                        cashierPerformance.forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text(p.deliveryUserName) },
                                                onClick = {
                                                    showCashierMenu = false
                                                    onApplyFilters(p.deliveryUserId, selectedDeliveryUserId, fromDate, toDate)
                                                }
                                            )
                                        }
                                    }
                                }

                                // Delivery Dropdown
                                Box(modifier = Modifier.weight(1f)) {
                                    FilterChip(
                                        selected = selectedDeliveryUserId != null,
                                        onClick = { showDeliveryMenu = true },
                                        label = { Text(deliveryFilterLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                                    )
                                    DropdownMenu(
                                        expanded = showDeliveryMenu,
                                        onDismissRequest = { showDeliveryMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.all)) },
                                            onClick = {
                                                showDeliveryMenu = false
                                                onApplyFilters(selectedCashierId, null, fromDate, toDate)
                                            }
                                        )
                                        deliveryPerformance.forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text(p.deliveryUserName) },
                                                onClick = {
                                                    showDeliveryMenu = false
                                                    onApplyFilters(selectedCashierId, p.deliveryUserId, fromDate, toDate)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        if (showDatePicker) {
            item {
                DateRangePickerModal(
                    initialStart = fromDate,
                    initialEnd = toDate,
                    onDismiss = { showDatePicker = false },
                    onConfirm = { start, end ->
                        showDatePicker = false
                        onApplyFilters(selectedCashierId, selectedDeliveryUserId, start, end)
                    }
                )
            }
        }

        // Summary Section
        item {
            Text(
                if (filteredSummary != null) "Filtered Summary" else stringResource(Res.string.summary_last_30_days),
                style = MaterialTheme.typography.titleLarge
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = stringResource(Res.string.total_orders),
                    value = displaySummary.totalOrders.toString(),
                    icon = Icons.Filled.Receipt,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = stringResource(Res.string.revenue),
                    value = formatAmount(displaySummary.totalRevenue),
                    icon = Icons.Filled.AttachMoney,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            MetricCard(
                title = stringResource(Res.string.avg_order_value),
                value = formatAmount(displaySummary.averageOrderValue),
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
                        stringResource(Res.string.settlements),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            items(settlements.byPaymentMethod.entries.toList()) { (method, data) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
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
                                    text = stringResource(Res.string.order_count),
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
                                    text = stringResource(Res.string.revenue),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatAmount(data.totalRevenue),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(Res.string.total_tax),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatAmount(data.totalTax),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        // Cashier Performance Section
        if (cashierPerformance.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.cashier_performance),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            items(cashierPerformance) { performance ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
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
                                    stringResource(Res.string.order_count),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${performance.orderCount}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Column {
                                Text(
                                    stringResource(Res.string.revenue),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatAmount(performance.totalRevenue),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Column {
                                Text(
                                    stringResource(Res.string.total_tax),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatAmount(performance.totalTax),
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
                        stringResource(Res.string.delivery_performance),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            items(deliveryPerformance) { performance ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
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
                                    text = stringResource(Res.string.orders_delivered),
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
                                    text = stringResource(Res.string.total_with_tax),
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
                                    text = stringResource(Res.string.total_tax),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatAmount(performance.totalTax),
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
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
                                text = stringResource(Res.string.qty, topItem.quantitySold),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatAmount(topItem.revenue),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.daily_breakdown),
                        style = MaterialTheme.typography.titleLarge
                    )
                    // Summary hint
                    Text(
                        text = "${dailyData.size} ${stringResource(Res.string.days)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Daily summary card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(Res.string.period_summary),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(Res.string.total_orders),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${dailyData.sumOf { it.orders }}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(Res.string.total_revenue),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatAmount(dailyData.sumOf { it.revenue }),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(Res.string.avg_daily),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatAmount(dailyData.sumOf { it.revenue } / dailyData.size.coerceAtLeast(1)),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            val maxRevenue = dailyData.maxOfOrNull { it.revenue } ?: 1.0
            val maxOrders = dailyData.maxOfOrNull { it.orders } ?: 1

            // Legend explaining what the bars mean
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(Res.string.daily_legend),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                Text(stringResource(Res.string.revenue), style = MaterialTheme.typography.labelSmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                                Text(stringResource(Res.string.orders_label), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            items(dailyData) { daily ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Date header with day name
                        val localDate = try { LocalDate.parse(daily.date) } catch (_: Exception) { null }
                        val dayName = localDate?.let { formatLocalDateTime(it.atTime(0, 0), "EEEE") } ?: ""
                        val displayDate = localDate?.let { formatLocalDateTime(it.atTime(0, 0), "MMM dd, yyyy") } ?: daily.date

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = displayDate,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = dayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatAmount(daily.revenue),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${daily.orders} ${stringResource(Res.string.orders_label)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Revenue progress bar with icon and percentage
                        val revenuePercent = ((daily.revenue / maxRevenue) * 100).toInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.TrendingUp, null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(Res.string.revenue),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(56.dp)
                            )
                            LinearProgressIndicator(
                                progress = { (daily.revenue / maxRevenue).toFloat() },
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$revenuePercent%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        // Orders progress bar with icon and percentage
                        val ordersPercent = ((daily.orders.toFloat() / maxOrders) * 100).toInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.ShoppingCart, null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(Res.string.orders_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(56.dp)
                            )
                            LinearProgressIndicator(
                                progress = { (daily.orders.toFloat() / maxOrders) },
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$ordersPercent%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.width(36.dp)
                            )
                        }
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerModal(
    initialStart: Long?,
    initialEnd: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?, Long?) -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart,
        initialSelectedEndDateMillis = initialEnd
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        dateRangePickerState.selectedStartDateMillis,
                        dateRangePickerState.selectedEndDateMillis
                    )
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            modifier = Modifier.padding(16.dp)
        )
    }
}
