package net.marllex.waselak.manager.suppliers

import net.marllex.waselak.core.common.format.kFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
// Date formatting was JVM-only (SimpleDateFormat / java.util.Date / java.util.Locale).
// kotlinx.datetime works on iOS too — Instant.fromEpochMilliseconds → LocalDate
// in the system zone, then a one-line "yyyy-MM-dd" format.
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.core.model.PurchaseOrder
import net.marllex.waselak.core.model.Stock
import net.marllex.waselak.core.model.Supplier
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliersScreen(
    viewModel: SuppliersViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    // Snackbar for success messages
    val snackbarHostState = remember { SnackbarHostState() }
    val createdMsg = stringResource(Res.string.po_created_success)
    val submittedMsg = stringResource(Res.string.po_submitted_success)
    val receivedMsg = stringResource(Res.string.items_received_success)
    val cancelledMsg = stringResource(Res.string.po_cancelled_success)
    LaunchedEffect(uiState.successMessage) {
        val msg = uiState.successMessage ?: return@LaunchedEffect
        val text = when (msg) {
            "po_created" -> createdMsg
            "po_submitted" -> submittedMsg
            "items_received" -> receivedMsg
            "po_cancelled" -> cancelledMsg
            else -> msg
        }
        snackbarHostState.showSnackbar(text)
        viewModel.clearSuccessMessage()
    }

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.suppliers_purchase_orders),
                isLoading = uiState.isLoading,
                onRefresh = viewModel::load,
                onNavigateBack = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.selectedTab == 0) {
                FloatingActionButton(
                    onClick = { viewModel.showAddDialog() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_supplier)) }
            } else {
                FloatingActionButton(
                    onClick = viewModel::showCreatePo,
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.create_purchase_order)) }
            }
        }
    ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                TabRow(selectedTabIndex = uiState.selectedTab) {
                    Tab(selected = uiState.selectedTab == 0, onClick = { viewModel.onTabChange(0) }, text = { Text(stringResource(Res.string.suppliers)) })
                    Tab(selected = uiState.selectedTab == 1, onClick = { viewModel.onTabChange(1) }, text = { Text(stringResource(Res.string.purchase_orders)) })
                }

                if (uiState.selectedTab == 0) {
                    // Search
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(Res.string.search_suppliers)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                when {
                    uiState.isLoading -> LoadingIndicator()
                    uiState.error != null && uiState.suppliers.isEmpty() -> ErrorView(message = uiState.error!!, onRetry = viewModel::load)
                    else -> {
                        if (uiState.selectedTab == 0) {
                            if (uiState.filteredSuppliers.isEmpty()) {
                                EmptyView(stringResource(Res.string.no_suppliers_yet))
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                ) {
                                    items(uiState.filteredSuppliers, key = { it.id }) { supplier ->
                                        SupplierCard(
                                            supplier = supplier,
                                            onEdit = { viewModel.showAddDialog(supplier) },
                                            onDelete = { viewModel.confirmDelete(supplier) },
                                        )
                                    }
                                }
                            }
                        } else {
                            if (uiState.purchaseOrders.isEmpty()) {
                                EmptyView(stringResource(Res.string.no_purchase_orders_yet))
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                ) {
                                    items(uiState.purchaseOrders, key = { it.id }) { po ->
                                        PurchaseOrderCard(
                                            po = po,
                                            onClick = { viewModel.showPoDetail(po) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }

    // Add/Edit Supplier Dialog
    if (uiState.showAddDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAddDialog,
            title = { Text(if (uiState.editingSupplier != null) stringResource(Res.string.edit_supplier) else stringResource(Res.string.add_supplier)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uiState.dialogName, onValueChange = viewModel::onDialogNameChange, label = { Text("${stringResource(Res.string.name)} *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = uiState.dialogContactName, onValueChange = viewModel::onDialogContactNameChange, label = { Text(stringResource(Res.string.contact_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = uiState.dialogPhone, onValueChange = viewModel::onDialogPhoneChange, label = { Text(stringResource(Res.string.phone)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = uiState.dialogEmail, onValueChange = viewModel::onDialogEmailChange, label = { Text(stringResource(Res.string.email)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = uiState.dialogAddress, onValueChange = viewModel::onDialogAddressChange, label = { Text(stringResource(Res.string.address)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = uiState.dialogNotes, onValueChange = viewModel::onDialogNotesChange, label = { Text(stringResource(Res.string.notes)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = viewModel::saveSupplier, enabled = !uiState.isSaving && uiState.dialogName.isNotBlank()) {
                    if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(if (uiState.editingSupplier != null) stringResource(Res.string.update) else stringResource(Res.string.add))
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissAddDialog) { Text(stringResource(Res.string.cancel)) } },
        )
    }

    // Delete Supplier Confirm
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(Res.string.delete_supplier)) },
            text = { Text(stringResource(Res.string.delete_supplier_confirm, uiState.deletingSupplier?.name ?: "")) },
            confirmButton = { Button(onClick = viewModel::deleteSupplier, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(Res.string.delete)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(Res.string.cancel)) } },
        )
    }

    // Create PO Dialog
    if (uiState.showCreatePoDialog) {
        CreatePurchaseOrderDialog(
            suppliers = uiState.suppliers,
            stockItems = uiState.stockItems,
            selectedSupplierId = uiState.poSupplierId,
            items = uiState.poItems,
            notes = uiState.poNotes,
            expectedDate = uiState.poExpectedDate,
            subtotal = uiState.poSubtotal,
            isSaving = uiState.isCreatingPo,
            onSelectSupplier = viewModel::setPoSupplier,
            onAddItem = viewModel::addPoItem,
            onUpdateQuantity = viewModel::updatePoItemQuantity,
            onUpdateCost = viewModel::updatePoItemCost,
            onRemoveItem = viewModel::removePoItem,
            onNotesChange = viewModel::setPoNotes,
            onExpectedDateChange = viewModel::setPoExpectedDate,
            onConfirm = viewModel::createPurchaseOrder,
            onDismiss = viewModel::dismissCreatePo,
        )
    }

    // PO Detail Dialog
    uiState.showPoDetail?.let { po ->
        PoDetailDialog(
            po = po,
            onSubmit = { viewModel.submitPo(po.id) },
            onReceive = { viewModel.showReceiveDialog(po) },
            onCancel = { viewModel.confirmCancelPo(po) },
            onDismiss = viewModel::dismissPoDetail,
        )
    }

    // Receive Items Dialog
    uiState.showReceiveDialog?.let { po ->
        ReceiveItemsDialog(
            po = po,
            items = uiState.receiveItems,
            isSaving = uiState.isCreatingPo,
            onUpdateQuantity = viewModel::updateReceiveQuantity,
            onUpdateBatch = viewModel::updateBatchNumber,
            onUpdateExpiry = viewModel::updateExpiryDate,
            onToggleNoExpiry = viewModel::toggleNoExpiry,
            onConfirm = viewModel::receivePo,
            onDismiss = viewModel::dismissReceiveDialog,
        )
    }

    // Cancel PO Confirm
    uiState.showCancelConfirm?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissCancelPo,
            title = { Text(stringResource(Res.string.cancel_order)) },
            text = { Text(stringResource(Res.string.cancel_po_confirm)) },
            confirmButton = {
                Button(
                    onClick = viewModel::cancelPo,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.cancel_order)) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissCancelPo) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

// ──────────────────────── Cards ────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplierCard(supplier: Supplier, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(supplier.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                supplier.contactName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                supplier.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (!supplier.active) {
                SuggestionChip(onClick = {}, label = { Text(stringResource(Res.string.inactive)) })
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseOrderCard(po: PurchaseOrder, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#${po.orderNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                PoStatusChip(po.status)
            }
            po.supplierName?.let { Text(stringResource(Res.string.supplier_label, it), style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(Res.string.po_items_total, po.itemCount, po.total.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PoStatusChip(status: String) {
    val (label, color) = when (status) {
        "DRAFT" -> stringResource(Res.string.po_status_draft) to MaterialTheme.colorScheme.outline
        "SUBMITTED" -> stringResource(Res.string.po_status_submitted) to MaterialTheme.colorScheme.primary
        "PARTIALLY_RECEIVED" -> stringResource(Res.string.po_status_partially_received) to MaterialTheme.colorScheme.tertiary
        "RECEIVED" -> stringResource(Res.string.po_status_received) to MaterialTheme.colorScheme.primary
        "CANCELLED" -> stringResource(Res.string.po_status_cancelled) to MaterialTheme.colorScheme.error
        else -> status to MaterialTheme.colorScheme.outline
    }
    SuggestionChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = color.copy(alpha = 0.12f),
            labelColor = color,
        ),
        border = null,
    )
}

// ──────────────────── Create PO Dialog ────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePurchaseOrderDialog(
    suppliers: List<Supplier>,
    stockItems: List<Stock>,
    selectedSupplierId: String,
    items: List<PoItemDraft>,
    notes: String,
    expectedDate: String,
    subtotal: Double,
    isSaving: Boolean,
    onSelectSupplier: (String) -> Unit,
    onAddItem: (Stock) -> Unit,
    onUpdateQuantity: (Int, Double) -> Unit,
    onUpdateCost: (Int, Double) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onNotesChange: (String) -> Unit,
    onExpectedDateChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.create_purchase_order)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Supplier Dropdown
                var supplierExpanded by remember { mutableStateOf(false) }
                val selectedSupplier = suppliers.find { it.id == selectedSupplierId }
                ExposedDropdownMenuBox(
                    expanded = supplierExpanded,
                    onExpandedChange = { supplierExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedSupplier?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("${stringResource(Res.string.select_supplier)} *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supplierExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        singleLine = true,
                    )
                    ExposedDropdownMenu(expanded = supplierExpanded, onDismissRequest = { supplierExpanded = false }) {
                        suppliers.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.name) },
                                onClick = { onSelectSupplier(s.id); supplierExpanded = false },
                            )
                        }
                    }
                }

                // Items section
                Text(stringResource(Res.string.add_item), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

                // Stock picker
                var stockExpanded by remember { mutableStateOf(false) }
                val availableStock = stockItems.filter { stock -> items.none { it.stockId == stock.id } }
                ExposedDropdownMenuBox(
                    expanded = stockExpanded,
                    onExpandedChange = { stockExpanded = it },
                ) {
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.select_item)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stockExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        singleLine = true,
                    )
                    ExposedDropdownMenu(expanded = stockExpanded, onDismissRequest = { stockExpanded = false }) {
                        availableStock.forEach { stock ->
                            DropdownMenuItem(
                                text = { Text("${stock.itemName} (${stock.unit})") },
                                onClick = { onAddItem(stock); stockExpanded = false },
                            )
                        }
                    }
                }

                // Added items
                if (items.isEmpty()) {
                    Text(
                        stringResource(Res.string.no_items_added),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    items.forEachIndexed { index, item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(item.stockName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    IconButton(onClick = { onRemoveItem(index) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.remove), modifier = Modifier.size(16.dp))
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = item.quantity.toString(),
                                        onValueChange = { v -> v.toDoubleOrNull()?.let { onUpdateQuantity(index, it) } },
                                        label = { Text(stringResource(Res.string.quantity)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
                                    OutlinedTextField(
                                        value = item.unitCost.toString(),
                                        onValueChange = { v -> v.toDoubleOrNull()?.let { onUpdateCost(index, it) } },
                                        label = { Text(stringResource(Res.string.unit_cost)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
                                }
                            }
                        }
                    }

                    // Subtotal
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(Res.string.subtotal), fontWeight = FontWeight.SemiBold)
                        Text(kFormat("%.2f", subtotal), fontWeight = FontWeight.SemiBold)
                    }
                }

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text(stringResource(Res.string.notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                // Expected delivery date with date picker
                var showExpectedDatePicker by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = expectedDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.expected_delivery)) },
                    placeholder = { Text(stringResource(Res.string.date_placeholder)) },
                    trailingIcon = {
                        IconButton(onClick = { showExpectedDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(Res.string.select_date))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { showExpectedDatePicker = true },
                    singleLine = true,
                )

                if (showExpectedDatePicker) {
                    val datePickerState = rememberDatePickerState()
                    DatePickerDialog(
                        onDismissRequest = { showExpectedDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showExpectedDatePicker = false
                                datePickerState.selectedDateMillis?.let { millis ->
                                    onExpectedDateChange(formatYmd(millis))
                                }
                            }) { Text(stringResource(Res.string.ok)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExpectedDatePicker = false }) {
                                Text(stringResource(Res.string.cancel))
                            }
                        },
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving && selectedSupplierId.isNotBlank() && items.isNotEmpty(),
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(stringResource(Res.string.create_order))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

// ──────────────────── PO Detail Dialog ────────────────────

@Composable
private fun PoDetailDialog(
    po: PurchaseOrder,
    onSubmit: () -> Unit,
    onReceive: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.po_details))
                PoStatusChip(po.status)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Order info
                Text("#${po.orderNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                po.supplierName?.let {
                    Text(stringResource(Res.string.supplier_label, it), style = MaterialTheme.typography.bodyMedium)
                }
                po.notes?.let {
                    Text("${stringResource(Res.string.notes)}: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                po.expectedDeliveryDate?.let {
                    Text("${stringResource(Res.string.expected_delivery)}: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                HorizontalDivider()

                // Items
                if (po.items.isNotEmpty()) {
                    po.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.stockName ?: item.stockId, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    "${stringResource(Res.string.requested_qty)}: ${item.requestedQuantity} ${item.unit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (item.receivedQuantity > 0) {
                                    Text(
                                        "${stringResource(Res.string.received_qty)}: ${item.receivedQuantity} ${item.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (item.isFullyReceived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                            Text(
                                kFormat("%.2f", item.totalCost),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(Res.string.subtotal), fontWeight = FontWeight.Bold)
                    Text(kFormat("%.2f", po.total), fontWeight = FontWeight.Bold)
                }

                // Action buttons
                Spacer(Modifier.height(8.dp))
                if (po.isDraft) {
                    Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.submit_order))
                    }
                }
                if (po.canReceive) {
                    Button(onClick = onReceive, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.receive_items))
                    }
                }
                if (po.isDraft || po.isSubmitted) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.cancel_order))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

// ──────────────────── Receive Items Dialog ────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiveItemsDialog(
    po: PurchaseOrder,
    items: List<ReceiveItemDraft>,
    isSaving: Boolean,
    onUpdateQuantity: (Int, Double) -> Unit,
    onUpdateBatch: (Int, String) -> Unit,
    onUpdateExpiry: (Int, String) -> Unit,
    onToggleNoExpiry: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Track which item's expiry date picker is open
    var showExpiryPickerForIndex by remember { mutableIntStateOf(-1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(Res.string.receive_items)} - #${po.orderNumber}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.forEachIndexed { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.stockName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "${stringResource(Res.string.requested_qty)}: ${item.requestedQty} | ${stringResource(Res.string.received_qty)}: ${item.alreadyReceived}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = item.receivedQty.toString(),
                                onValueChange = { v -> v.toDoubleOrNull()?.let { onUpdateQuantity(index, it) } },
                                label = { Text("${stringResource(Res.string.quantity)} (${item.unit})") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            // Batch number
                            OutlinedTextField(
                                value = item.batchNumber,
                                onValueChange = { onUpdateBatch(index, it) },
                                label = { Text(stringResource(Res.string.batch_number)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            // No Expiry checkbox
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onToggleNoExpiry(index) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = item.noExpiry,
                                    onCheckedChange = { onToggleNoExpiry(index) },
                                )
                                Text(
                                    stringResource(Res.string.no_expiry),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            // Expiry date with calendar picker (hidden when noExpiry is checked)
                            if (!item.noExpiry) {
                                OutlinedTextField(
                                    value = item.expiryDate,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(Res.string.expiry_date)) },
                                    placeholder = { Text(stringResource(Res.string.date_placeholder)) },
                                    trailingIcon = {
                                        IconButton(onClick = { showExpiryPickerForIndex = index }) {
                                            Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(Res.string.select_date))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().clickable { showExpiryPickerForIndex = index },
                                    singleLine = true,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving && items.any { it.receivedQty > 0 },
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(stringResource(Res.string.receive_items))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )

    // Expiry date picker dialog
    if (showExpiryPickerForIndex >= 0) {
        val idx = showExpiryPickerForIndex
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showExpiryPickerForIndex = -1 },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onUpdateExpiry(idx, formatYmd(millis))
                    }
                    showExpiryPickerForIndex = -1
                }) { Text(stringResource(Res.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showExpiryPickerForIndex = -1 }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// "yyyy-MM-dd" formatter used by the supplier purchase-order date pickers.
// Lives here (private) because it's the only place in this file that needs
// it. Same output as the old SimpleDateFormat("yyyy-MM-dd", Locale.US).
private fun formatYmd(epochMillis: Long): String {
    val ld = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    val mm = ld.monthNumber.toString().padStart(2, '0')
    val dd = ld.dayOfMonth.toString().padStart(2, '0')
    return "${ld.year}-$mm-$dd"
}
