package net.marllex.waselak.feature.manager.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.foundation.layout.BoxWithConstraints

import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.orders.generated.resources.Res
import net.marllex.waselak.feature.manager.orders.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.common.extensions.formatEpochMs
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.OrderItem
import net.marllex.waselak.core.model.OrderStatus
import net.marllex.waselak.core.model.PaymentMethod
import net.marllex.waselak.core.ui.components.ChannelChip
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.components.OrderStatusChip
import net.marllex.waselak.core.ui.components.PaymentStatusChip
import net.marllex.waselak.core.ui.components.PaymentMethodChip
import net.marllex.waselak.core.ui.components.formatStatusLabel
import net.marllex.waselak.core.ui.platform.rememberPlatformActions
import net.marllex.waselak.core.common.utils.CurrencyFormatter
import net.marllex.waselak.feature.manager.orders.components.ModernFilterSection
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = koinViewModel(),
    onViewReceipt: ((String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val platformActions = rememberPlatformActions()
    val lifecycleOwner = LocalLifecycleOwner.current

    val fallbackViewReceipt: (String) -> Unit = remember(viewModel, platformActions) {
        { orderId ->
            viewModel.shareReceipt(orderId) { url ->
                platformActions.openUrl(url)
            }
        }
    }

    // Auto-refresh when screen becomes visible
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadOrders()
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }

    BoxWithConstraints {
    val isTablet = maxWidth >= 600.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.orders)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.orders.isEmpty() -> LoadingIndicator()
            uiState.error != null && uiState.orders.isEmpty() -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadOrders,
            )

            else -> Column(modifier = Modifier.padding(padding)) {
                // Modern Filter Section
                ModernFilterSection(
                    selectedChannel = uiState.selectedChannel,
                    selectedStatus = uiState.selectedStatus,
                    selectedCashierId = uiState.selectedCashierId,
                    selectedDeliveryUserId = uiState.selectedDeliveryUserId,
                    fromDate = uiState.fromDate,
                    toDate = uiState.toDate,
                    cashiers = uiState.cashiers,
                    deliveryUsers = uiState.deliveryUsers,
                    hasActiveFilters = uiState.hasActiveFilters,
                    onChannelSelected = viewModel::filterByChannel,
                    onStatusSelected = viewModel::filterByStatus,
                    onCashierSelected = viewModel::filterByCashier,
                    onDeliverySelected = viewModel::filterByDelivery,
                    onDateRangeSelected = viewModel::filterByDateRange,
                    onClearAll = viewModel::clearAllFilters,
                    onShowDatePicker = { showDatePicker = true }
                )

                // Orders count info
                if (uiState.orders.isNotEmpty()) {
                    Text(
                        text = "${uiState.orders.size} ${stringResource(Res.string.orders_found)}",
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
                            onViewReceipt = { (onViewReceipt ?: fallbackViewReceipt)(order.id) },
                            onEdit = if (order.status != OrderStatus.COMPLETED && order.status != OrderStatus.CANCELED && order.status != OrderStatus.REFUNDED) {
                                { viewModel.showEditOrder(order) }
                            } else null,
                            onPay = if (order.paymentStatus != net.marllex.waselak.core.model.PaymentStatus.PAID && order.status != OrderStatus.REFUNDED) {
                                { viewModel.showPaymentDialog(order) }
                            } else null,
                            onRefund = if (order.status == OrderStatus.COMPLETED && order.paymentStatus == net.marllex.waselak.core.model.PaymentStatus.PAID) {
                                { viewModel.showRefundDialog(order) }
                            } else null,
                        )
                    }
                }
            }
        }
    }
    } // BoxWithConstraints

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
                    Text(stringResource(Res.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.cancel))
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
            isLoading = uiState.isLoading,
            onAssign = viewModel::assignDeliveryUser,
            onDismiss = viewModel::dismissAssignDeliveryDialog,
        )
    }

    // Edit order dialog
    if (uiState.showEditOrderDialog && uiState.editingOrder != null) {
        EditOrderDialog(
            order = uiState.editingOrder!!,
            editItems = uiState.editItems,
            clientName = uiState.editClientName,
            clientPhone = uiState.editClientPhone,
            clientAddress = uiState.editClientAddress,
            notes = uiState.editNotes,
            isSaving = uiState.isEditSaving,
            onClientNameChange = viewModel::updateEditClientName,
            onClientPhoneChange = viewModel::updateEditClientPhone,
            onClientAddressChange = viewModel::updateEditClientAddress,
            onNotesChange = viewModel::updateEditNotes,
            onQuantityChange = viewModel::updateEditItemQuantity,
            onRemoveItem = viewModel::removeEditItem,
            onSave = viewModel::saveEditOrder,
            onDismiss = viewModel::dismissEditOrderDialog,
        )
    }

    // Payment bottom sheet
    if (uiState.showPaymentDialog && uiState.payingOrder != null) {
        PaymentBottomSheet(
            order = uiState.payingOrder!!,
            selectedMethod = uiState.selectedPaymentMethod,
            isProcessing = uiState.isPaymentProcessing,
            onSelectMethod = viewModel::selectPaymentMethod,
            onConfirm = viewModel::confirmPayment,
            onDismiss = viewModel::dismissPaymentDialog,
        )
    }

    // Refund confirmation dialog
    if (uiState.showRefundDialog && uiState.refundingOrder != null) {
        RefundConfirmationDialog(
            order = uiState.refundingOrder!!,
            reason = uiState.refundReason,
            isProcessing = uiState.isRefundProcessing,
            onReasonChange = viewModel::updateRefundReason,
            onConfirm = viewModel::confirmRefund,
            onDismiss = viewModel::dismissRefundDialog,
        )
    }
}

@Composable
private fun AssignDeliveryDialog(
    deliveryUsers: List<net.marllex.waselak.core.model.User>,
    isLoading: Boolean,
    onAssign: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        icon = {
            Icon(
                Icons.Filled.DeliveryDining,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(Res.string.assign_delivery_person)) },
        text = {
            if (deliveryUsers.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_delivery_users),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(Res.string.select_delivery_person),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    
                    if (isLoading) {
                        // Show loading indicator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(Res.string.assigning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Show delivery users list
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
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(Res.string.cancel))
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
    onEdit: (() -> Unit)? = null,
    onPay: (() -> Unit)? = null,
    onRefund: (() -> Unit)? = null,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#${order.id.takeLast(6).uppercase()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = order.createdAt.formatEpochMs("MMM dd, yyyy HH:mm"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OrderStatusChip(status = order.status)
                    PaymentStatusChip(status = order.paymentStatus)
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Channel, Table & Payment (Flowing) ---
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ChannelChip(channel = order.channel.name)
                order.tableNumber?.let { tableNum ->
                    Text(
                        text = "${stringResource(Res.string.table_label)} $tableNum",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                PaymentMethodChip(label = order.paymentMethod.name)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Info Section ---
            Text(
                text = stringResource(Res.string.items_count_total, order.items.size, CurrencyFormatter.formatDecimal(order.total)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            val details = listOfNotNull(
                order.cashierName?.let { stringResource(Res.string.cashier) to it },
                order.deliveryUserName?.let { stringResource(Res.string.delivery) to it },
                order.clientName?.let { stringResource(Res.string.client) to it },
                order.clientPhone?.let { stringResource(Res.string.phone) to it }
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
                        text = stringResource(Res.string.order_items),
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
                    PriceRow(stringResource(Res.string.subtotal), CurrencyFormatter.format(order.subtotal))
                    PriceRow(stringResource(Res.string.delivery_fee), CurrencyFormatter.format(order.deliveryFee + order.tax))
                    PriceRow(stringResource(Res.string.total), CurrencyFormatter.format(order.total), isBold = true)

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
                                    "${stringResource(Res.string.notes)}: $it",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            order.clientAddress?.let {
                                Text(
                                    "${stringResource(Res.string.address)}: $it",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Refund info (shown for refunded orders)
                    if (order.status == OrderStatus.REFUNDED && order.refundReason != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                                    MaterialTheme.shapes.small
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "${stringResource(Res.string.refund_reason)}: ${order.refundReason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium,
                            )
                            order.refundedAt?.let { ts ->
                                Text(
                                    text = ts.formatEpochMs("MMM dd, yyyy HH:mm"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            }

            // --- Action Buttons ---
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onViewReceipt,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.view_receipt))
                }
                if (onEdit != null) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.edit_order))
                    }
                }
            }
            // Pay button for unpaid orders
            if (onPay != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onPay,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${stringResource(Res.string.confirm_payment)} ${CurrencyFormatter.formatDecimal(order.total)}",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // Refund button for completed + paid orders
            if (onRefund != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRefund,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                    ),
                ) {
                    Text(
                        text = stringResource(Res.string.refund_order),
                        fontWeight = FontWeight.Bold,
                    )
                }
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
            net.marllex.waselak.core.ui.util.VariantDisplayHelper.formatVariantSummary(item.variantOptionsSnapshot)?.let { summary ->
                Text(text = summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            item.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = CurrencyFormatter.formatDecimal(item.itemPriceSnapshot * item.quantity),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun getNextStatuses(order: Order): List<OrderStatus> {
    // Exclude REFUNDED — refund has its own dedicated flow with reason input
    return OrderStatus.entries.filter { it != OrderStatus.REFUNDED && order.status.canTransitionTo(it, order.channel) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditOrderDialog(
    order: Order,
    editItems: List<OrderItem>,
    clientName: String,
    clientPhone: String,
    clientAddress: String,
    notes: String,
    isSaving: Boolean,
    onClientNameChange: (String) -> Unit,
    onClientPhoneChange: (String) -> Unit,
    onClientAddressChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onQuantityChange: (String, Int) -> Unit,
    onRemoveItem: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit_order)) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Items section
                item {
                    Text(
                        stringResource(Res.string.order_items),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(editItems, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.itemNameSnapshot, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                net.marllex.waselak.core.ui.util.VariantDisplayHelper.formatVariantSummary(item.variantOptionsSnapshot)?.let { summary ->
                                    Text(text = summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                                Text("${CurrencyFormatter.formatDecimal(item.itemPriceSnapshot)} x ${item.quantity} = ${CurrencyFormatter.formatDecimal(item.totalPrice)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onQuantityChange(item.id, item.quantity - 1) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Text("${item.quantity}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { onQuantityChange(item.id, item.quantity + 1) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onRemoveItem(item.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // New total
                item {
                    val newSubtotal = editItems.sumOf { it.totalPrice }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(Res.string.subtotal), fontWeight = FontWeight.Bold)
                        Text(CurrencyFormatter.formatDecimal(newSubtotal), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Client info
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                item {
                    OutlinedTextField(
                        value = clientName, onValueChange = onClientNameChange,
                        label = { Text(stringResource(Res.string.client)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = clientPhone, onValueChange = onClientPhoneChange,
                        label = { Text(stringResource(Res.string.phone)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                if (order.channel == net.marllex.waselak.core.model.OrderChannel.DELIVERY) {
                    item {
                        OutlinedTextField(
                            value = clientAddress, onValueChange = onClientAddressChange,
                            label = { Text(stringResource(Res.string.address)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = notes, onValueChange = onNotesChange,
                        label = { Text(stringResource(Res.string.notes)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !isSaving && editItems.isNotEmpty(),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(Res.string.save_changes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════
// Payment Bottom Sheet
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentBottomSheet(
    order: Order,
    selectedMethod: PaymentMethod,
    isProcessing: Boolean,
    onSelectMethod: (PaymentMethod) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // Header
            Text(
                text = stringResource(Res.string.confirm_payment),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "#${order.id.takeLast(6).uppercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Total amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.total),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = CurrencyFormatter.formatDecimal(order.total),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Payment method selector
            Text(
                text = stringResource(Res.string.select_payment_method),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            val methods = PaymentMethod.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                methods.forEachIndexed { index, method ->
                    SegmentedButton(
                        selected = selectedMethod == method,
                        onClick = { onSelectMethod(method) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = methods.size,
                        ),
                    ) {
                        Text(method.name)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Confirm button
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isProcessing,
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = "${stringResource(Res.string.confirm_payment)} ${CurrencyFormatter.formatDecimal(order.total)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Refund Confirmation Dialog
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun RefundConfirmationDialog(
    order: Order,
    reason: String,
    isProcessing: Boolean,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text(
                text = stringResource(Res.string.refund_order),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Order ID and total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "#${order.id.takeLast(6).uppercase()}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = CurrencyFormatter.formatDecimal(order.total),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // Warning message
                Text(
                    text = stringResource(Res.string.refund_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Reason input
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text(stringResource(Res.string.refund_reason)) },
                    placeholder = { Text(stringResource(Res.string.refund_reason_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    enabled = !isProcessing,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isProcessing && reason.isNotBlank(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.processing_refund))
                } else {
                    Text(stringResource(Res.string.confirm_refund))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing,
            ) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
