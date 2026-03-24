package net.marllex.waselak.manager.cashdrawer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.CashDrawerSession
import net.marllex.waselak.core.model.CashMovement
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerCashDrawerScreen(
    viewModel: ManagerCashDrawerViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.cash_drawer),
                isLoading = uiState.isLoading,
                onRefresh = viewModel::load,
                onNavigateBack = onNavigateBack,
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == 0) {
                if (uiState.hasOpenSession) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallFloatingActionButton(onClick = viewModel::showMovementDialog) { Icon(Icons.Default.SwapVert, contentDescription = stringResource(Res.string.movement)) }
                        FloatingActionButton(onClick = viewModel::showCloseDialog, containerColor = MaterialTheme.colorScheme.error) { Icon(Icons.Default.Lock, contentDescription = stringResource(Res.string.close_drawer)) }
                    }
                } else {
                    FloatingActionButton(onClick = viewModel::showOpenDialog, containerColor = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.LockOpen, contentDescription = stringResource(Res.string.open_drawer)) }
                }
            }
        },
    ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Cashier filter dropdown
                if (uiState.cashiers.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    val selectedName = uiState.cashiers.find { it.id == uiState.selectedCashierId }?.name
                        ?: stringResource(Res.string.all)

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = selectedName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(Res.string.cashier_filter)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            singleLine = true,
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.all)) },
                                onClick = { viewModel.selectCashier(null); expanded = false },
                            )
                            uiState.cashiers.forEach { cashier ->
                                DropdownMenuItem(
                                    text = { Text(cashier.name) },
                                    onClick = { viewModel.selectCashier(cashier.id); expanded = false },
                                )
                            }
                        }
                    }
                }

                TabRow(selectedTabIndex = uiState.selectedTab) {
                    Tab(selected = uiState.selectedTab == 0, onClick = { viewModel.onTabChange(0) }, text = { Text(stringResource(Res.string.current_session)) })
                    Tab(selected = uiState.selectedTab == 1, onClick = { viewModel.onTabChange(1) }, text = { Text(stringResource(Res.string.history)) })
                }

                when {
                    uiState.isLoading -> LoadingIndicator()
                    uiState.error != null -> ErrorView(message = uiState.error!!, onRetry = viewModel::load)
                    uiState.selectedTab == 0 -> {
                        val session = uiState.currentSession
                        if (session == null || session.isClosed) {
                            EmptyView(stringResource(Res.string.no_open_drawer))
                        } else {
                            CurrentSessionContent(session, uiState.summary, uiState.movements)
                        }
                    }
                    else -> {
                        if (uiState.sessions.isEmpty()) {
                            EmptyView(stringResource(Res.string.no_session_history))
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp),
                            ) {
                                items(uiState.sessions, key = { it.id }) { session ->
                                    SessionCard(session)
                                }
                            }
                        }
                    }
                }
            }
    }

    // Open Dialog
    if (uiState.showOpenDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissOpenDialog,
            title = { Text(stringResource(Res.string.open_cash_drawer)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uiState.openingBalance, onValueChange = viewModel::onOpeningBalanceChange, label = { Text(stringResource(Res.string.opening_balance)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = uiState.openNotes, onValueChange = viewModel::onOpenNotesChange, label = { Text(stringResource(Res.string.notes)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = viewModel::openDrawer, enabled = !uiState.isSaving) { Text(stringResource(Res.string.open)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissOpenDialog) { Text(stringResource(Res.string.cancel)) } },
        )
    }

    // Close Dialog
    if (uiState.showCloseDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCloseDialog,
            title = { Text(stringResource(Res.string.close_cash_drawer)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.summary?.let { Text(stringResource(Res.string.expected_balance_value, it.expectedBalance), style = MaterialTheme.typography.bodyMedium) }
                    OutlinedTextField(value = uiState.closingBalance, onValueChange = viewModel::onClosingBalanceChange, label = { Text(stringResource(Res.string.closing_balance)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = uiState.closeNotes, onValueChange = viewModel::onCloseNotesChange, label = { Text(stringResource(Res.string.notes)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = viewModel::closeDrawer, enabled = !uiState.isSaving && uiState.closingBalance.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(Res.string.close_drawer)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissCloseDialog) { Text(stringResource(Res.string.cancel)) } },
        )
    }

    // Movement Dialog
    if (uiState.showMovementDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMovementDialog,
            title = { Text(stringResource(Res.string.cash_movement)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = uiState.movementType == "CASH_IN", onClick = { viewModel.onMovementTypeChange("CASH_IN") }, label = { Text(stringResource(Res.string.cash_in)) })
                        FilterChip(selected = uiState.movementType == "CASH_OUT", onClick = { viewModel.onMovementTypeChange("CASH_OUT") }, label = { Text(stringResource(Res.string.cash_out)) })
                    }
                    OutlinedTextField(value = uiState.movementAmount, onValueChange = viewModel::onMovementAmountChange, label = { Text(stringResource(Res.string.amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = uiState.movementReason, onValueChange = viewModel::onMovementReasonChange, label = { Text(stringResource(Res.string.notes)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = viewModel::addMovement, enabled = !uiState.isSaving && uiState.movementAmount.isNotBlank()) { Text(stringResource(Res.string.add)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissMovementDialog) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun CurrentSessionContent(session: CashDrawerSession, summary: net.marllex.waselak.core.model.DrawerSummary?, movements: List<CashMovement>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(Res.string.session_active), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(Res.string.opening_balance)); Text("${session.openingBalance}", fontWeight = FontWeight.SemiBold)
                    }
                    summary?.let { s ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(Res.string.cash_in)); Text("+${s.totalCashIn}", color = Color(0xFF4CAF50))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(Res.string.cash_out)); Text("-${s.totalCashOut}", color = Color(0xFFF44336))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(Res.string.sales)); Text("+${s.totalSales}", color = Color(0xFF2196F3))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(Res.string.expected_balance), fontWeight = FontWeight.Bold); Text("${s.expectedBalance}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            Text(stringResource(Res.string.movements_count, movements.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        }
        items(movements, key = { it.id }) { movement ->
            MovementRow(movement)
        }
    }
}

@Composable
private fun MovementRow(movement: CashMovement) {
    val color = when {
        movement.isCashIn || movement.isSale -> Color(0xFF4CAF50)
        movement.isCashOut || movement.isRefund -> Color(0xFFF44336)
        else -> Color.Gray
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val typeLabel = when (movement.type) {
                    "CASH_IN" -> stringResource(Res.string.movement_cash_in)
                    "CASH_OUT" -> stringResource(Res.string.movement_cash_out)
                    "SALE" -> stringResource(Res.string.movement_sale)
                    "REFUND" -> stringResource(Res.string.movement_refund)
                    "ADJUSTMENT" -> stringResource(Res.string.movement_adjustment)
                    else -> movement.type
                }
                Text(typeLabel, style = MaterialTheme.typography.labelMedium, color = color)
                movement.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                movement.createdByName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("${if (movement.isCashIn || movement.isSale) "+" else "-"}${movement.amount}", style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SessionCard(session: CashDrawerSession) {
    val isOpen = session.status == "OPEN"
    val statusColor = if (isOpen) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOpen) Color(0xFF4CAF50).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header: cashier name + status
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.cashierName ?: stringResource(Res.string.cashier),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        when (session.status) {
                            "OPEN" -> stringResource(Res.string.session_status_open)
                            "CLOSED" -> stringResource(Res.string.session_status_closed)
                            else -> session.status
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                    )
                }
            }

            // Session ID
            Text(
                "#${session.id.takeLast(6).uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // Balances
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(Res.string.opening_balance), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${session.openingBalance}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                if (session.closingBalance != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(Res.string.closing_balance), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${session.closingBalance}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Expected + Difference
            if (session.expectedBalance != null && session.closingBalance != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(stringResource(Res.string.expected_balance), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${session.expectedBalance}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(Res.string.difference), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val diff = session.discrepancyAmount
                        val diffColor = when {
                            diff > 0.5 -> Color(0xFF4CAF50)
                            diff < -0.5 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text("${diff}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = diffColor)
                    }
                }
            }

            // Notes
            session.notes?.let { notes ->
                Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

