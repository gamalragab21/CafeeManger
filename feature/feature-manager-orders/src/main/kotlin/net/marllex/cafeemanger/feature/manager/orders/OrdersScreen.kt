package net.marllex.cafeemanger.feature.manager.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.OrderItem
import net.marllex.cafeemanger.core.model.OrderStatus
import net.marllex.cafeemanger.core.ui.components.ChannelChip
import net.marllex.cafeemanger.core.ui.components.ErrorView
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import net.marllex.cafeemanger.core.ui.components.OrderStatusChip
import net.marllex.cafeemanger.core.ui.components.PaymentMethodChip
import net.marllex.cafeemanger.core.ui.components.formatStatusLabel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = hiltViewModel(),
    onViewReceipt: ((String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val context = LocalContext.current
    val fallbackViewReceipt: (String) -> Unit = remember(viewModel, context) {
        { orderId ->
            viewModel.shareReceipt(orderId) { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showCashierMenu by remember { mutableStateOf(false) }
    var showDeliveryMenu by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.orders)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    // Filter toggle button
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.filters),
                            tint = if (uiState.hasActiveFilters) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Clear filters button (only show when filters are active)
                    if (uiState.hasActiveFilters) {
                        IconButton(onClick = { viewModel.clearAllFilters() }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear_filters),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null && uiState.orders.isEmpty() -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadOrders,
            )

            else -> Column(modifier = Modifier.padding(padding)) {
                // Expandable Filters Section
                AnimatedVisibility(
                    visible = showFilters,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Date filter row
                            Text(
                                text = stringResource(R.string.date_filter),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val now = System.currentTimeMillis()
                                val todayStart = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    set(java.util.Calendar.MINUTE, 0)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }.timeInMillis

                                FilterChip(
                                    selected = uiState.fromDate == todayStart,
                                    onClick = { viewModel.filterByDateRange(todayStart, now) },
                                    label = { Text(stringResource(R.string.today)) }
                                )
                                FilterChip(
                                    selected = uiState.fromDate != null && uiState.fromDate != todayStart && uiState.toDate != null,
                                    onClick = { showDatePicker = true },
                                    label = {
                                        if (uiState.fromDate != null && uiState.fromDate != todayStart) {
                                            val dateFormat = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
                                            Text("${dateFormat.format(java.util.Date(uiState.fromDate!!))} - ${dateFormat.format(java.util.Date(uiState.toDate ?: now))}")
                                        } else {
                                            Text(stringResource(R.string.custom_date))
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp))
                                    }
                                )
                                if (uiState.fromDate != null) {
                                    FilterChip(
                                        selected = false,
                                        onClick = { viewModel.filterByDateRange(null, null) },
                                        label = { Text(stringResource(R.string.all_dates)) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Cashier and Delivery filter dropdowns
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Cashier dropdown
                                Box(modifier = Modifier.weight(1f)) {
                                    val cashierLabel = uiState.selectedCashierId?.let { id ->
                                        uiState.cashiers.find { it.id == id }?.name ?: id.takeLast(6)
                                    } ?: stringResource(R.string.all_cashiers)

                                    FilterChip(
                                        selected = uiState.selectedCashierId != null,
                                        onClick = { showCashierMenu = true },
                                        label = { Text(cashierLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                                    )
                                    DropdownMenu(
                                        expanded = showCashierMenu,
                                        onDismissRequest = { showCashierMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.all_cashiers)) },
                                            onClick = {
                                                showCashierMenu = false
                                                viewModel.filterByCashier(null)
                                            }
                                        )
                                        uiState.cashiers.forEach { cashier ->
                                            DropdownMenuItem(
                                                text = { Text(cashier.name) },
                                                onClick = {
                                                    showCashierMenu = false
                                                    viewModel.filterByCashier(cashier.id)
                                                }
                                            )
                                        }
                                    }
                                }

                                // Delivery dropdown
                                Box(modifier = Modifier.weight(1f)) {
                                    val deliveryLabel = uiState.selectedDeliveryUserId?.let { id ->
                                        uiState.deliveryUsers.find { it.id == id }?.name ?: id.takeLast(6)
                                    } ?: stringResource(R.string.all_delivery)

                                    FilterChip(
                                        selected = uiState.selectedDeliveryUserId != null,
                                        onClick = { showDeliveryMenu = true },
                                        label = { Text(deliveryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                                    )
                                    DropdownMenu(
                                        expanded = showDeliveryMenu,
                                        onDismissRequest = { showDeliveryMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.all_delivery)) },
                                            onClick = {
                                                showDeliveryMenu = false
                                                viewModel.filterByDelivery(null)
                                            }
                                        )
                                        uiState.deliveryUsers.forEach { delivery ->
                                            DropdownMenuItem(
                                                text = { Text(delivery.name) },
                                                onClick = {
                                                    showDeliveryMenu = false
                                                    viewModel.filterByDelivery(delivery.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Status filter chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedStatus == null,
                            onClick = { viewModel.filterByStatus(null) },
                            label = { Text(stringResource(R.string.all)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                    items(OrderStatus.entries.toList()) { status ->
                        FilterChip(
                            selected = uiState.selectedStatus == status.name,
                            onClick = { viewModel.filterByStatus(status.name) },
                            label = { Text(formatStatusLabel(status)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }

                // Orders count info
                if (uiState.orders.isNotEmpty()) {
                    Text(
                        text = "${uiState.orders.size} ${stringResource(R.string.orders_found)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isTablet) Modifier.widthIn(max = 720.dp) else Modifier),
                    contentPadding = PaddingValues(if (isTablet) 24.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.orders, key = { it.id }) { order ->
                        OrderCard(
                            order = order,
                            onStatusUpdate = { viewModel.updateOrderStatus(order.id, it) },
                            onViewReceipt = { (onViewReceipt ?: fallbackViewReceipt)(order.id) }
                        )
                    }
                }
            }
        }
    }

    // Date range picker dialog
    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = uiState.fromDate,
            initialSelectedEndDateMillis = uiState.toDate
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        viewModel.filterByDateRange(
                            dateRangePickerState.selectedStartDateMillis,
                            dateRangePickerState.selectedEndDateMillis
                        )
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    // Delivery person assignment dialog
    if (uiState.showAssignDeliveryDialog) {
        AssignDeliveryDialog(
            deliveryUsers = uiState.deliveryUsers,
            onAssign = viewModel::assignDeliveryUser,
            onDismiss = viewModel::dismissAssignDeliveryDialog,
        )
    }
}

@Composable
private fun AssignDeliveryDialog(
    deliveryUsers: List<net.marllex.cafeemanger.core.model.User>,
    onAssign: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.DeliveryDining,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.assign_delivery_person)) },
        text = {
            if (deliveryUsers.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_delivery_users),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.select_delivery_person),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    deliveryUsers.forEach { user ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAssign(user.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = user.phone,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {onDismiss()},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderCard(
    order: Order,
    onStatusUpdate: (OrderStatus) -> Unit,
    onViewReceipt: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // --- Header Section ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#${order.id.takeLast(6).uppercase()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OrderStatusChip(status = order.status)
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Channel & Payment (Flowing) ---
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ChannelChip(channel = order.channel.name)
                PaymentMethodChip(method = order.paymentMethod.name)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Info Section ---
            Text(
                text = "${order.items.size} items • Total: ${String.format("%.2f", order.total)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            val details = listOfNotNull(
                order.cashierName?.let { stringResource(R.string.cashier) to it },
                order.deliveryUserName?.let { stringResource(R.string.delivery) to it },
                order.clientName?.let { stringResource(R.string.client) to it },
                order.clientPhone?.let { stringResource(R.string.phone) to it }
            )

            details.forEach { (label, value) ->
                Text(
                    text = "$label: $value",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- Expandable Section ---
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.order_items),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    order.items.forEach { item ->
                        OrderItemRow(item = item)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    // Price Breakdown
                    PriceRow(stringResource(R.string.subtotal), String.format("%.2f EGP", order.subtotal))
                    PriceRow(stringResource(R.string.tax), String.format("%.2f EGP", order.tax))
                    PriceRow(stringResource(R.string.total), String.format("%.2f EGP", order.total), isBold = true)

                    // Secondary Info
                    if (order.notes != null || order.clientAddress != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    MaterialTheme.shapes.small
                                )
                                .padding(8.dp)
                        ) {
                            order.notes?.let {
                                Text(
                                    "${stringResource(R.string.notes)}: $it",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            order.clientAddress?.let {
                                Text(
                                    "${stringResource(R.string.address)}: $it",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // --- Action Buttons ---
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onViewReceipt,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Receipt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.view_receipt))
            }
            Spacer(modifier = Modifier.height(8.dp))
            val nextStatuses = getNextStatuses(order)
            if (nextStatuses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    nextStatuses.forEach { status ->
                        FilledTonalButton(
                            onClick = { onStatusUpdate(status) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                formatStatusLabel(status),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PriceRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isBold) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
    }
}

@Composable
private fun OrderItemRow(item: OrderItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${item.quantity}x",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.itemNameSnapshot, style = MaterialTheme.typography.bodyMedium)
            item.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = String.format("%.2f", item.itemPriceSnapshot * item.quantity),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun getNextStatuses(order: Order): List<OrderStatus> {
    return OrderStatus.entries.filter { order.status.canTransitionTo(it, order.channel) }
}
