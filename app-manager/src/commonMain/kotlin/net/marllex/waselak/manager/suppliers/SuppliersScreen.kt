package net.marllex.waselak.manager.suppliers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.Supplier
import net.marllex.waselak.core.model.PurchaseOrder
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.suppliers_purchase_orders)) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == 0) {
                FloatingActionButton(
                    onClick = { viewModel.showAddDialog() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_supplier)) }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
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
                                    PurchaseOrderCard(po)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Dialog
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

    // Delete Confirm
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(Res.string.delete_supplier)) },
            text = { Text(stringResource(Res.string.delete_supplier_confirm, uiState.deletingSupplier?.name ?: "")) },
            confirmButton = { Button(onClick = viewModel::deleteSupplier, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(Res.string.delete)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

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

@Composable
private fun PurchaseOrderCard(po: PurchaseOrder) {
    Card(
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
                SuggestionChip(onClick = {}, label = { Text(po.status) })
            }
            po.supplierName?.let { Text(stringResource(Res.string.supplier_label, it), style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(Res.string.po_items_total, po.itemCount, po.total.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
