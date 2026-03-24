package net.marllex.waselak.cashier.cashdrawer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.app_cashier.generated.resources.total
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashDrawerScreen(
    viewModel: CashDrawerViewModel = koinViewModel(),
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
            if (uiState.hasOpenSession) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(onClick = viewModel::showMovementDialog) { Icon(Icons.Default.SwapVert, contentDescription = stringResource(Res.string.movement)) }
                    FloatingActionButton(onClick = viewModel::showCloseDialog, containerColor = MaterialTheme.colorScheme.error) { Icon(Icons.Default.Lock, contentDescription = stringResource(Res.string.close_drawer)) }
                }
            } else {
                FloatingActionButton(onClick = viewModel::showOpenDialog, containerColor = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.LockOpen, contentDescription = stringResource(Res.string.open_drawer)) }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.error != null -> ErrorView(message = uiState.error!!, onRetry = viewModel::load)
                else -> {
                    val session = uiState.currentSession
                    if (session == null || session.isClosed) {
                        EmptyView(stringResource(Res.string.no_open_drawer))
                    } else {
                        CurrentSessionContent(session, uiState.summary, uiState.movements)
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
        // Summary card
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

        // Orders Summary
        if (summary != null && summary.totalOrders > 0) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(Res.string.total_orders), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${summary.totalOrders}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(Res.string.total_revenue), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${summary.totalSales}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Channel Breakdown
        if (summary != null && summary.channels.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(Res.string.channel_breakdown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        summary.channels.forEach { ch ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(ch.channel, style = MaterialTheme.typography.bodyMedium)
                                    Text("${ch.orderCount} ${stringResource(Res.string.orders)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${ch.totalAmount}", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Payment Method Breakdown
        if (summary != null && summary.totalSales > 0) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(Res.string.payment_distribution), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if (summary.cashSales > 0) {
                            PaymentMethodRow(stringResource(Res.string.payment_cash), summary.cashOrderCount, summary.cashSales, Color(0xFF4CAF50))
                        }
                        if (summary.cardSales > 0) {
                            PaymentMethodRow(stringResource(Res.string.payment_card), summary.cardOrderCount, summary.cardSales, Color(0xFF2196F3))
                        }
                        if (summary.walletSales > 0) {
                            PaymentMethodRow(stringResource(Res.string.payment_wallet), summary.walletOrderCount, summary.walletSales, Color(0xFFFF9800))
                        }
                        if (summary.creditSales > 0) {
                            PaymentMethodRow(stringResource(Res.string.payment_credit), summary.creditOrderCount, summary.creditSales, Color(0xFF9C27B0))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(Res.string.total), fontWeight = FontWeight.Bold)
                            Text("${summary.totalSales}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Movements
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
private fun PaymentMethodRow(label: String, count: Int, amount: Double, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(4.dp), color = color, modifier = Modifier.size(8.dp)) {}
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(" ($count)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("$amount", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun SessionCard(session: CashDrawerSession) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(session.cashierName ?: stringResource(Res.string.cashier), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val statusLabel = when (session.status) {
                    "OPEN" -> stringResource(Res.string.session_status_open)
                    "CLOSED" -> stringResource(Res.string.session_status_closed)
                    else -> session.status
                }
                SuggestionChip(onClick = {}, label = { Text(statusLabel) })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(Res.string.session_open_balance, "${session.openingBalance}")); Text(stringResource(Res.string.session_close_balance, "${session.closingBalance ?: "-"}"))
            }
            if (session.hasDiscrepancy) {
                Text(stringResource(Res.string.session_discrepancy, "${session.discrepancyAmount}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
