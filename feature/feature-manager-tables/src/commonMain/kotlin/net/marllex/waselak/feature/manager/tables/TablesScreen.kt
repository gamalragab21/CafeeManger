package net.marllex.waselak.feature.manager.tables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import net.marllex.waselak.feature.manager.tables.generated.resources.Res
import net.marllex.waselak.feature.manager.tables.generated.resources.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import net.marllex.waselak.core.model.Table
import net.marllex.waselak.core.model.TableStatus
import net.marllex.waselak.core.ui.components.ErrorView
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
) {
    val uiState by viewModel.uiState.collectAsState()

    BoxWithConstraints {
    val isTablet = maxWidth >= 600.dp
    val gridColumns = if (maxWidth >= 840.dp) 4 else if (isTablet) 3 else 2

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.tables)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
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
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null && uiState.tables.isEmpty() -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadTables,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(if (isTablet) 24.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.tables, key = { it.id }) { table ->
                    TableCard(
                        table = table,
                        onEdit = if (readOnly) null else {{ viewModel.showEditDialog(table) }},
                        onDelete = if (readOnly) null else {{ viewModel.deleteTable(table.id) }},
                        onStatusChange = { viewModel.updateStatus(table, it) },
                    )
                }
            }
        }

        if (uiState.showAddDialog) {
            TableDialog(
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
    }
    } // BoxWithConstraints
}

@Composable
private fun TableCard(
    table: Table,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onStatusChange: (TableStatus) -> Unit,
) {
    val statusColor = when (table.status) {
        TableStatus.AVAILABLE -> TableAvailable
        TableStatus.OCCUPIED -> TableOccupied
        TableStatus.RESERVED -> TableReserved
    }

    var showStatusMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.table, table.number),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = " ${table.capacity}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
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
            if (onEdit != null || onDelete != null) {
                Row {
                    onEdit?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                    }
                    onDelete?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableDialog(
    isEditing: Boolean,
    number: String,
    capacity: String,
    isSaving: Boolean,
    onNumberChange: (String) -> Unit,
    onCapacityChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) stringResource(Res.string.edit_table) else stringResource(Res.string.add_table)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = number, onValueChange = onNumberChange,
                    label = { Text(stringResource(Res.string.table_number)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = capacity, onValueChange = onCapacityChange,
                    label = { Text(stringResource(Res.string.capacity)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !isSaving && number.isNotBlank()) {
                Text(if (isSaving) stringResource(Res.string.saving) else stringResource(Res.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
