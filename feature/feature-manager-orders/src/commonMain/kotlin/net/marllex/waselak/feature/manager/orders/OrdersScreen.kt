package net.marllex.waselak.feature.manager.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Receipt
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
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlinx.coroutines.launch

import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.orders.generated.resources.Res
import net.marllex.waselak.feature.manager.orders.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.common.extensions.formatEpochMs
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.OrderItem
import net.marllex.waselak.core.model.OrderStatus
import net.marllex.waselak.core.model.PaymentMethod
import net.marllex.waselak.core.model.PaymentStatus
import net.marllex.waselak.core.ui.components.ChannelChip
import net.marllex.waselak.core.ui.components.EmptyView
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
import waselak.core.core_ui.generated.resources.returns_exchanges

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = koinViewModel(),
    onViewReceipt: ((String) -> Unit)? = null,
    onSplitPayment: ((String) -> Unit)? = null,
    businessType: String? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val platformActions = rememberPlatformActions()

    val fallbackViewReceipt: (String) -> Unit = remember(viewModel, platformActions) {
        { orderId ->
            viewModel.shareReceipt(orderId) { url ->
                platformActions.openUrl(url)
            }
        }
    }

    // Load orders when screen appears + periodic polling to reflect KDS status changes.
    // Uses simple LaunchedEffect instead of repeatOnLifecycle for cross-platform compatibility
    // (Desktop lifecycle may not reach RESUMED state reliably).
    LaunchedEffect(Unit) {
        viewModel.loadOrders()
        while (true) {
            kotlinx.coroutines.delay(10_000) // refresh every 10 seconds
            viewModel.loadOrders()
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show error as Snackbar
    LaunchedEffect(uiState.error) {
        val errorMessage = uiState.error
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearError()
        }
    }

    BoxWithConstraints {
    val screenWidth = maxWidth
    val isTablet = screenWidth >= 600.dp

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.orders),
                isLoading = uiState.isLoading,
                onRefresh = viewModel::loadOrders,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        when {
            uiState.isLoading && uiState.orders.isEmpty() -> LoadingIndicator()
            uiState.error != null && uiState.orders.isEmpty() -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadOrders,
            )

            else -> Column(modifier = Modifier.fillMaxSize()) {
                // Modern Filter Section
                ModernFilterSection(
                    selectedChannel = uiState.selectedChannel,
                    selectedStatus = uiState.selectedStatus,
                    selectedCashierId = uiState.selectedCashierId,
                    selectedDeliveryUserId = uiState.selectedDeliveryUserId,
                    selectedTableId = uiState.selectedTableId,
                    fromDate = uiState.fromDate,
                    toDate = uiState.toDate,
                    cashiers = uiState.cashiers,
                    deliveryUsers = uiState.deliveryUsers,
                    tables = uiState.tables,
                    hasActiveFilters = uiState.hasActiveFilters,
                    onChannelSelected = viewModel::filterByChannel,
                    onStatusSelected = viewModel::filterByStatus,
                    onCashierSelected = viewModel::filterByCashier,
                    onDeliverySelected = viewModel::filterByDelivery,
                    onTableSelected = viewModel::filterByTable,
                    onDateRangeSelected = viewModel::filterByDateRange,
                    onClearAll = viewModel::clearAllFilters,
                    onShowDatePicker = { showDatePicker = true }
                )

                if (uiState.orders.isEmpty()) {
                    EmptyView(stringResource(Res.string.no_orders))
                } else {
                    // Orders count info
                    Text(
                        text = "${uiState.orders.size} / ${uiState.totalCount} ${stringResource(Res.string.orders_found)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    val gridColumns = if (screenWidth >= 1200.dp) 3 else if (screenWidth >= 700.dp) 2 else 1
                    val gridState = rememberLazyGridState()
                    val showScrollToTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(if (isTablet) 24.dp else 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.orders, key = { it.id }) { order ->
                                OrderCard(
                                    order = order,
                                    onStatusUpdate = { viewModel.updateOrderStatus(order.id, it) },
                                    onViewReceipt = { (onViewReceipt ?: fallbackViewReceipt)(order.id) },
                                    onEdit = if (order.status != OrderStatus.COMPLETED && order.status != OrderStatus.CANCELED) {
                                        { viewModel.showEditOrder(order) }
                                    } else null,
                                    onPayNow = if (order.paymentStatus == PaymentStatus.PENDING && order.status != OrderStatus.CANCELED) {
                                        { viewModel.showPaymentDialog(order) }
                                    } else null,
                                    onSplitPayment = if (onSplitPayment != null && order.paymentStatus == PaymentStatus.PENDING && order.status != OrderStatus.CANCELED) {
                                        { onSplitPayment(order.id) }
                                    } else null,
                                    onReturn = if ((order.status == OrderStatus.COMPLETED || order.status == OrderStatus.REFUNDED) &&
                                        businessType in listOf("PHARMACY", "RETAIL")) {
                                        { viewModel.showReturnDialog(order) }
                                    } else null,
                                    onCopyOrderId = { platformActions.copyToClipboard(order.id) },
                                )
                            }
                            if (uiState.hasMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (uiState.isLoadingMore) {
                                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                        } else {
                                            OutlinedButton(onClick = { viewModel.loadMoreOrders() }) {
                                                Text(stringResource(Res.string.load_more))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Scroll to top FAB
                        if (showScrollToTop) {
                            SmallFloatingActionButton(
                                onClick = { scope.launch { gridState.animateScrollToItem(0) } },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = stringResource(Res.string.scroll_to_top),
                                )
                            }
                        }
                    }
                }
            }
        }
        } // PullToRefreshBox
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

    // Delivery person assignment bottom sheet
    if (uiState.showAssignDeliveryDialog) {
        AssignDeliveryBottomSheet(
            deliveryUsers = uiState.deliveryUsers,
            isLoading = uiState.isLoading,
            onAssign = viewModel::assignDeliveryUser,
            onDismiss = viewModel::dismissAssignDeliveryDialog,
        )
    }

    // Edit order bottom sheet
    if (uiState.showEditOrderDialog && uiState.editingOrder != null) {
        EditOrderBottomSheet(
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

    // Payment confirmation bottom sheet
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

    // Return / Exchange dialog
    if (uiState.showReturnDialog && uiState.returningOrder != null) {
        ReturnBottomSheet(
            order = uiState.returningOrder!!,
            returnItems = uiState.returnItems,
            returnReason = uiState.returnReason,
            returnType = uiState.returnType,
            isProcessing = uiState.isReturnProcessing,
            exchangeMenuItems = uiState.exchangeMenuItems,
            exchangeSelectedItem = uiState.exchangeSelectedItem,
            exchangeSearchQuery = uiState.exchangeSearchQuery,
            onReturnTypeChange = viewModel::setReturnType,
            onReasonChange = viewModel::setReturnReason,
            onQuantityChange = viewModel::updateReturnItemQuantity,
            onExchangeSearchChange = viewModel::setExchangeSearchQuery,
            onExchangeItemSelect = viewModel::selectExchangeItem,
            onSubmit = viewModel::submitReturn,
            onDismiss = viewModel::dismissReturnDialog,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignDeliveryBottomSheet(
    deliveryUsers: List<net.marllex.waselak.core.model.User>,
    isLoading: Boolean,
    onAssign: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (!isLoading) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.DeliveryDining,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.assign_delivery_person),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (deliveryUsers.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_delivery_users),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(Res.string.select_delivery_person),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(Res.string.assigning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
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

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(Res.string.cancel))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderCard(
    order: Order,
    onStatusUpdate: (OrderStatus) -> Unit,
    onViewReceipt: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onPayNow: (() -> Unit)? = null,
    onSplitPayment: (() -> Unit)? = null,
    onReturn: (() -> Unit)? = null,
    onCopyOrderId: () -> Unit = {},
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${order.id.takeLast(6).uppercase()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(
                            onClick = onCopyOrderId,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = order.createdAt.formatEpochMs("MMM dd, yyyy hh:mm a"),
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
                    if (order.discount > 0) {
                        PriceRow(
                            label = stringResource(Res.string.discount),
                            value = "- ${CurrencyFormatter.format(order.discount)}",
                            isDiscount = true,
                        )
                    }
                    if (order.pointsRedeemed > 0) {
                        PriceRow(
                            label = stringResource(Res.string.points_redeemed),
                            value = "${order.pointsRedeemed} pts",
                            isDiscount = true,
                        )
                    }
                    if (order.deliveryFee > 0) {
                        PriceRow(stringResource(Res.string.delivery_fee), CurrencyFormatter.format(order.deliveryFee))
                    }
                    if (order.tax > 0) {
                        PriceRow(stringResource(Res.string.tax_label), CurrencyFormatter.format(order.tax))
                    }
                    PriceRow(stringResource(Res.string.total), CurrencyFormatter.format(order.total), isBold = true)
                    // Refund info
                    if (order.hasReturns) {
                        PriceRow(
                            stringResource(Res.string.refunded_amount) + " (${order.returnedItemCount})",
                            "-${CurrencyFormatter.format(order.refundedAmount)}",
                            isDiscount = true,
                        )
                        PriceRow(
                            stringResource(Res.string.net_total),
                            CurrencyFormatter.format(order.netTotal),
                            isBold = true,
                        )
                    }
                    // Discount reason
                    val discountReason = order.discountReason
                    if (!discountReason.isNullOrBlank()) {
                        Text(
                            text = "${stringResource(Res.string.discount_reason)}: $discountReason",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

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
                }
            }

            // --- Action Buttons ---
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = onViewReceipt,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.view_receipt), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
                if (onEdit != null) {
                    OutlinedButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.edit_order), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (onSplitPayment != null) {
                    OutlinedButton(
                        onClick = onSplitPayment,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.split_payment), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (onReturn != null) {
                    OutlinedButton(
                        onClick = onReturn,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.AssignmentReturn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.returns_exchanges), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Pay Now button for pending payment orders
            if (onPayNow != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onPayNow,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.pay_now))
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
fun PriceRow(label: String, value: String, isBold: Boolean = false, isDiscount: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isDiscount) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isDiscount -> MaterialTheme.colorScheme.error
                isBold -> MaterialTheme.colorScheme.primary
                else -> Color.Unspecified
            },
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
            text = CurrencyFormatter.formatDecimal(item.itemPriceSnapshot * item.quantity),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun getNextStatuses(order: Order): List<OrderStatus> {
    return OrderStatus.entries.filter { order.status.canTransitionTo(it, order.channel) }
}

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
    ModalBottomSheet(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Payment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.confirm_payment),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = stringResource(Res.string.payment_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Order info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "#${order.id.takeLast(6).uppercase()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = CurrencyFormatter.format(order.total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Payment method selection
            Text(
                text = stringResource(Res.string.select_payment_method),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            PaymentMethod.entries.filter { it != PaymentMethod.SPLIT && it != PaymentMethod.CREDIT }.forEach { method ->
                val isSelected = method == selectedMethod
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isProcessing) { onSelectMethod(method) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isSelected) 2.dp else 0.dp,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimary)
                                )
                            }
                        }
                        Text(
                            text = method.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(Res.string.cancel))
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(Res.string.confirm_payment))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EditOrderBottomSheet(
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.edit_order),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

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

            // Action buttons
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(Res.string.cancel))
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving && editItems.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(Res.string.save_changes))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReturnBottomSheet(
    order: Order,
    returnItems: List<ReturnItemSelection>,
    returnReason: String,
    returnType: String,
    isProcessing: Boolean,
    exchangeMenuItems: List<net.marllex.waselak.core.model.Item> = emptyList(),
    exchangeSelectedItem: net.marllex.waselak.core.model.Item? = null,
    exchangeSearchQuery: String = "",
    onReturnTypeChange: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onQuantityChange: (Int, Int) -> Unit,
    onExchangeSearchChange: (String) -> Unit = {},
    onExchangeItemSelect: (net.marllex.waselak.core.model.Item?) -> Unit = {},
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(Res.string.returns_exchanges),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "#${order.id.takeLast(6).uppercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Return type selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = returnType == "RETURN",
                    onClick = { onReturnTypeChange("RETURN") },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(Res.string.return_label)) }
                SegmentedButton(
                    selected = returnType == "EXCHANGE",
                    onClick = { onReturnTypeChange("EXCHANGE") },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(Res.string.exchange_label)) }
            }

            // Reason
            OutlinedTextField(
                value = returnReason,
                onValueChange = onReasonChange,
                label = { Text(stringResource(Res.string.return_reason)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Items with quantity selectors
            Text(
                stringResource(Res.string.select_return_items),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            returnItems.forEachIndexed { index, item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.itemName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${stringResource(Res.string.max_qty)}: ${item.maxQuantity}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { onQuantityChange(index, item.selectedQuantity - 1) },
                                enabled = item.selectedQuantity > 0,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Text(
                                "${item.selectedQuantity}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (item.selectedQuantity > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            IconButton(
                                onClick = { onQuantityChange(index, item.selectedQuantity + 1) },
                                enabled = item.selectedQuantity < item.maxQuantity,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Exchange: replacement item picker
            if (returnType == "EXCHANGE") {
                HorizontalDivider()
                Text(
                    stringResource(Res.string.exchange_label) + " →",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                // Selected item chip
                if (exchangeSelectedItem != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exchangeSelectedItem.name, fontWeight = FontWeight.SemiBold)
                                Text(CurrencyFormatter.formatDecimal(exchangeSelectedItem.price), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onExchangeItemSelect(null) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Search field
                OutlinedTextField(
                    value = exchangeSearchQuery,
                    onValueChange = onExchangeSearchChange,
                    label = { Text(stringResource(Res.string.search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Items list (filtered)
                val filteredItems = if (exchangeSearchQuery.isBlank()) {
                    exchangeMenuItems.take(10)
                } else {
                    exchangeMenuItems.filter { it.name.contains(exchangeSearchQuery, ignoreCase = true) }.take(10)
                }

                if (filteredItems.isNotEmpty() && exchangeSelectedItem == null) {
                    Column(
                        modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        filteredItems.forEach { menuItem ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onExchangeItemSelect(menuItem) },
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(menuItem.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(CurrencyFormatter.formatDecimal(menuItem.price), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Submit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing,
                ) { Text(stringResource(Res.string.cancel)) }
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing && returnReason.isNotBlank() && returnItems.any { it.selectedQuantity > 0 } &&
                        (returnType != "EXCHANGE" || exchangeSelectedItem != null),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                    else Text(stringResource(Res.string.submit_return))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
