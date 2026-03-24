package net.marllex.waselak.feature.manager.tables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.tables.generated.resources.Res
import net.marllex.waselak.feature.manager.tables.generated.resources.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.collectAsState
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.core.model.Customer
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.Reservation
import net.marllex.waselak.core.model.Table
import net.marllex.waselak.core.model.TableStatus
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.OrderStatusChip
import net.marllex.waselak.core.ui.components.PaymentStatusChip
import net.marllex.waselak.core.common.utils.CurrencyFormatter
import net.marllex.waselak.core.common.extensions.formatEpochMs
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.FeatureNotAvailableView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.theme.TableAvailable
import net.marllex.waselak.core.ui.theme.TableOccupied
import net.marllex.waselak.core.ui.theme.TableReserved
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TablesScreen(
    viewModel: TablesViewModel = koinViewModel(),
    readOnly: Boolean = false,
    onStartOrder: ((tableId: String, reservationId: String, clientName: String, clientPhone: String?) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    BoxWithConstraints {
    val isTablet = maxWidth >= 600.dp
    val gridColumns = if (maxWidth >= 840.dp) 4 else if (isTablet) 3 else 2

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.tables),
                isLoading = uiState.isLoading,
                onRefresh = viewModel::loadTables,
            )
        },
        floatingActionButton = {
            if (!readOnly) {
                FloatingActionButton(onClick = viewModel::showAddDialog) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Table")
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        when {
            uiState.showFeatureNotAvailable -> FeatureNotAvailableView(
                message = uiState.featureNotAvailableMessage,
            )
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null && uiState.tables.isEmpty() -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadTables,
            )
            uiState.tables.isEmpty() -> EmptyView(stringResource(Res.string.no_tables))
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = if (isTablet) 24.dp else 16.dp, end = if (isTablet) 24.dp else 16.dp, top = if (isTablet) 24.dp else 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.tables, key = { it.id }) { table ->
                    val reservation = uiState.reservationsByTableId[table.id]
                    val order = uiState.ordersByTableId[table.id]
                    TableCard(
                        table = table,
                        reservation = reservation,
                        order = order,
                        onEdit = if (readOnly) null else {{ viewModel.showEditDialog(table) }},
                        onDelete = if (readOnly) null else {{ viewModel.deleteTable(table.id) }},
                        onStatusChange = { viewModel.updateStatus(table, it) },
                        onReserve = { viewModel.showReserveSheet(table) },
                        onReservationClick = { reservation?.let { viewModel.showReservationDetail(it) } },
                        onOrderClick = { order?.let { viewModel.showOrderDetail(it) } },
                    )
                }
            }
        }
        } // PullToRefreshBox

        if (uiState.showAddDialog) {
            TableBottomSheet(
                isEditing = uiState.editingTable != null,
                number = uiState.dialogNumber,
                capacity = uiState.dialogCapacity,
                isSaving = uiState.isSaving,
                onNumberChange = viewModel::updateDialogNumber,
                onCapacityChange = viewModel::updateDialogCapacity,
                onSave = viewModel::saveTable,
                onDismiss = viewModel::dismissDialog,
            )
        }

        if (uiState.showReservationSheet) {
            ReservationBottomSheet(
                tableNumber = uiState.reservationTableNumber,
                clientName = uiState.reservationClientName,
                clientPhone = uiState.reservationClientPhone,
                date = uiState.reservationDate,
                time = uiState.reservationTime,
                guests = uiState.reservationGuests,
                notes = uiState.reservationNotes,
                isSaving = uiState.isSavingReservation,
                customerSearchQuery = uiState.customerSearchQuery,
                customerSearchResults = uiState.customerSearchResults,
                selectedCustomer = uiState.selectedCustomer,
                onSearchCustomer = viewModel::searchCustomer,
                onSelectCustomer = viewModel::selectCustomer,
                onClearCustomer = viewModel::clearSelectedCustomer,
                onClientPhoneChange = viewModel::updateReservationClientPhone,
                onClientNameChange = viewModel::updateReservationClientName,
                onDateChange = viewModel::updateReservationDate,
                onTimeChange = viewModel::updateReservationTime,
                onGuestsChange = viewModel::updateReservationGuests,
                onNotesChange = viewModel::updateReservationNotes,
                onSave = viewModel::saveReservation,
                onDismiss = viewModel::dismissReservationSheet,
            )
        }

        if (uiState.showReservationDetail && uiState.selectedReservation != null) {
            val reservationOrder = uiState.selectedReservation!!.orderId?.let { orderId ->
                uiState.ordersByTableId.values.find { it.id == orderId }
            } ?: uiState.ordersByTableId[uiState.selectedReservation!!.tableId]
            ReservationDetailBottomSheet(
                reservation = uiState.selectedReservation!!,
                order = reservationOrder,
                onCancel = { viewModel.cancelReservation(uiState.selectedReservation!!.id) },
                onComplete = { viewModel.completeReservation(uiState.selectedReservation!!.id) },
                onStartOrder = onStartOrder?.let { callback ->
                    {
                        val res = uiState.selectedReservation!!
                        viewModel.dismissReservationDetail()
                        callback(res.tableId, res.id, res.clientName, res.clientPhone)
                    }
                },
                onDismiss = viewModel::dismissReservationDetail,
            )
        }

        if (uiState.showOrderDetail && uiState.selectedOrder != null) {
            OrderDetailBottomSheet(
                order = uiState.selectedOrder!!,
                onDismiss = viewModel::dismissOrderDetail,
            )
        }

    }
    } // BoxWithConstraints
}

@Composable
private fun TableCard(
    table: Table,
    reservation: Reservation?,
    order: Order?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onStatusChange: (TableStatus) -> Unit,
    onReserve: () -> Unit,
    onReservationClick: () -> Unit,
    onOrderClick: () -> Unit,
) {
    val statusColor = when (table.status) {
        TableStatus.AVAILABLE -> TableAvailable
        TableStatus.OCCUPIED -> TableOccupied
        TableStatus.RESERVED -> TableReserved
    }

    var showStatusMenu by remember { mutableStateOf(false) }
    val isReserved = table.status == TableStatus.RESERVED && reservation != null
    val isOccupied = table.status == TableStatus.OCCUPIED && order != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    isReserved -> Modifier.clickable { onReservationClick() }
                    isOccupied -> Modifier.clickable { onOrderClick() }
                    else -> Modifier
                }
            ),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.table, table.number),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = " ${table.capacity}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Order info on occupied tables
            if (isOccupied && order != null) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                Spacer(modifier = Modifier.height(6.dp))
                OrderStatusChip(status = order.status)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = CurrencyFormatter.formatDecimal(order.total),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TableOccupied,
                )
                Text(
                    text = stringResource(Res.string.order_items_count, order.items.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val orderClientName = order.clientName
                if (!orderClientName.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = orderClientName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Reservation info on reserved tables
            else if (reservation != null && isReserved) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = TableReserved,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = reservation.clientName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "${reservation.reservationDate} ${reservation.reservationTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                val phone = reservation.clientPhone
                if (!phone.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            } else {
                // Status button for non-reserved tables
                OutlinedButton(onClick = { showStatusMenu = true }) {
                    Text(text = table.status.name, color = statusColor)
                }
                DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                    TableStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.name) },
                            onClick = {
                                onStatusChange(status)
                                showStatusMenu = false
                            },
                        )
                    }
                }
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                // Status change button for reserved/occupied tables
                if (isReserved || isOccupied) {
                    Box {
                        IconButton(onClick = { showStatusMenu = true }) {
                            Icon(
                                Icons.Filled.SwapHoriz,
                                contentDescription = stringResource(Res.string.change_status),
                                tint = statusColor,
                            )
                        }
                        DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                            TableStatus.entries.forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(status.name) },
                                    onClick = {
                                        onStatusChange(status)
                                        showStatusMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                // Reserve button for available tables
                if (table.status == TableStatus.AVAILABLE) {
                    IconButton(onClick = onReserve) {
                        Icon(
                            Icons.Filled.EventAvailable,
                            contentDescription = stringResource(Res.string.reserve),
                            tint = TableReserved,
                        )
                    }
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableBottomSheet(
    isEditing: Boolean,
    number: String,
    capacity: String,
    isSaving: Boolean,
    onNumberChange: (String) -> Unit,
    onCapacityChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (isEditing) stringResource(Res.string.edit_table) else stringResource(Res.string.add_table),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = number, onValueChange = onNumberChange,
                label = { Text(stringResource(Res.string.table_number)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = capacity, onValueChange = onCapacityChange,
                label = { Text(stringResource(Res.string.capacity)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
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
                    enabled = !isSaving && number.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (isSaving) stringResource(Res.string.saving) else stringResource(Res.string.save))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReservationBottomSheet(
    tableNumber: String,
    clientName: String,
    clientPhone: String,
    date: String,
    time: String,
    guests: String,
    notes: String,
    isSaving: Boolean,
    customerSearchQuery: String,
    customerSearchResults: List<Customer>,
    selectedCustomer: Customer?,
    onSearchCustomer: (String) -> Unit,
    onSelectCustomer: (Customer) -> Unit,
    onClearCustomer: () -> Unit,
    onClientPhoneChange: (String) -> Unit,
    onClientNameChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onGuestsChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.create_reservation) + " - " + stringResource(Res.string.table, tableNumber),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // Phone search field - search by phone number to find customer
            Column {
                OutlinedTextField(
                    value = if (selectedCustomer != null) customerSearchQuery else customerSearchQuery,
                    onValueChange = {
                        onSearchCustomer(it)
                        onClientPhoneChange(it)
                    },
                    label = { Text(stringResource(Res.string.client_phone)) },
                    placeholder = { Text(stringResource(Res.string.search_customer_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = {
                        Icon(
                            if (selectedCustomer != null) Icons.Filled.Phone else Icons.Filled.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = if (selectedCustomer != null) {
                        {
                            IconButton(onClick = onClearCustomer) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                            }
                        }
                    } else null,
                    readOnly = selectedCustomer != null,
                )

                // Customer search results dropdown
                if (customerSearchResults.isNotEmpty() && selectedCustomer == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Column {
                            customerSearchResults.take(5).forEach { customer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectCustomer(customer) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = customer.phone,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        val customerName = customer.name
                                        if (!customerName.isNullOrBlank()) {
                                            Text(
                                                text = customerName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (customer.orderCount > 0) {
                                        Text(
                                            text = "${customer.orderCount} orders",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                if (customer != customerSearchResults.take(5).last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Client name - auto-filled when customer found by phone
            OutlinedTextField(
                value = clientName, onValueChange = onClientNameChange,
                label = { Text(stringResource(Res.string.client_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                readOnly = selectedCustomer != null,
            )
            // Date & Time with picker dialogs
            var showDatePicker by remember { mutableStateOf(false) }
            var showTimePicker by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = {},
                        label = { Text(stringResource(Res.string.reservation_date)) },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true },
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = time,
                        onValueChange = {},
                        label = { Text(stringResource(Res.string.reservation_time)) },
                        placeholder = { Text("HH:MM") },
                        singleLine = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showTimePicker = true },
                    )
                }
            }

            // Date Picker Dialog
            if (showDatePicker) {
                val initialMillis = try {
                    LocalDate.parse(date).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                } catch (_: Exception) { null }
                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showDatePicker = false
                            datePickerState.selectedDateMillis?.let { millis ->
                                val ld = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                                val formatted = "${ld.year}-${ld.monthNumber.toString().padStart(2, '0')}-${ld.dayOfMonth.toString().padStart(2, '0')}"
                                onDateChange(formatted)
                            }
                        }) { Text(stringResource(Res.string.save)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.cancel)) }
                    },
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // Time Picker Dialog
            if (showTimePicker) {
                val parts = time.split(":")
                val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 12
                val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val timePickerState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
                Dialog(onDismissRequest = { showTimePicker = false }) {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(Res.string.reservation_time),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TimePicker(state = timePickerState)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TextButton(
                                    onClick = { showTimePicker = false },
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(Res.string.cancel)) }
                                Button(
                                    onClick = {
                                        showTimePicker = false
                                        val formatted = "${timePickerState.hour.toString().padStart(2, '0')}:${timePickerState.minute.toString().padStart(2, '0')}"
                                        onTimeChange(formatted)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) { Text(stringResource(Res.string.save)) }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = guests, onValueChange = onGuestsChange,
                label = { Text(stringResource(Res.string.number_of_guests)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.People, contentDescription = null) },
            )
            OutlinedTextField(
                value = notes, onValueChange = onNotesChange,
                label = { Text(stringResource(Res.string.notes)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
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
                    enabled = !isSaving && clientPhone.isNotBlank() && date.isNotBlank() && time.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (isSaving) stringResource(Res.string.saving) else stringResource(Res.string.save))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReservationDetailBottomSheet(
    reservation: Reservation,
    order: Order? = null,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    onStartOrder: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.reservation_details),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            val tableNum = reservation.tableNumber
            if (tableNum != null) {
                Text(
                    text = stringResource(Res.string.table, tableNum),
                    style = MaterialTheme.typography.titleMedium,
                    color = TableReserved,
                )
            }

            HorizontalDivider()

            // Client info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(
                        text = stringResource(Res.string.client_name),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = reservation.clientName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            val detailPhone = reservation.clientPhone
            if (!detailPhone.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = stringResource(Res.string.client_phone),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = detailPhone,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            // Date & Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(
                        text = stringResource(Res.string.reservation_date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(Res.string.reservation_at, reservation.reservationDate, reservation.reservationTime),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            // Guests
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(
                        text = stringResource(Res.string.number_of_guests),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(Res.string.guests_count, reservation.numberOfGuests),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            // Notes
            val detailNotes = reservation.notes
            if (!detailNotes.isNullOrBlank()) {
                Column {
                    Text(
                        text = stringResource(Res.string.notes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = detailNotes,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Status
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (reservation.status.name) {
                        "PENDING" -> Color(0xFFFFF3E0)
                        "CONFIRMED" -> Color(0xFFE8F5E9)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = reservation.status.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = when (reservation.status.name) {
                        "PENDING" -> Color(0xFFE65100)
                        "CONFIRMED" -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            // Linked order info
            if (order != null) {
                HorizontalDivider()
                Text(
                    text = stringResource(Res.string.order_details),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OrderStatusChip(status = order.status)
                    PaymentStatusChip(status = order.paymentStatus)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(Res.string.order_items_count, order.items.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = CurrencyFormatter.formatDecimal(order.total),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                // Order items list
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${item.quantity}x ${item.itemNameSnapshot}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = CurrencyFormatter.formatDecimal(item.itemPriceSnapshot * item.quantity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Start Order button (shown only for cashier when reservation is PENDING/CONFIRMED)
            if (onStartOrder != null && reservation.status.name in listOf("PENDING", "CONFIRMED")) {
                Button(
                    onClick = onStartOrder,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(Res.string.start_order))
                }
            }

            HorizontalDivider()

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(stringResource(Res.string.cancel_reservation))
                }
                Button(
                    onClick = onComplete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(stringResource(Res.string.complete_reservation))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderDetailBottomSheet(
    order: Order,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.order_details),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            val tableNum = order.tableNumber
            if (tableNum != null) {
                Text(
                    text = stringResource(Res.string.table, tableNum),
                    style = MaterialTheme.typography.titleMedium,
                    color = TableOccupied,
                )
            }

            // Status chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrderStatusChip(status = order.status)
                PaymentStatusChip(status = order.paymentStatus)
            }

            HorizontalDivider()

            // Client info
            val clientName = order.clientName
            if (!clientName.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = stringResource(Res.string.client_name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = clientName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            val clientPhone = order.clientPhone
            if (!clientPhone.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = clientPhone,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            // Created time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = order.createdAt.formatEpochMs("MMM dd, hh:mm a"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // Items list
            order.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${item.quantity}x",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = item.itemNameSnapshot,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = CurrencyFormatter.formatDecimal(item.itemPriceSnapshot * item.quantity),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            HorizontalDivider()

            // Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.order_total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = CurrencyFormatter.formatDecimal(order.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
