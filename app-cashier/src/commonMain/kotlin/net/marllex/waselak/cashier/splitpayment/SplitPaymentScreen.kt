package net.marllex.waselak.cashier.splitpayment

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
import net.marllex.waselak.core.model.OrderPayment
import net.marllex.waselak.core.model.SplitPaymentSummary
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPaymentScreen(
    viewModel: SplitPaymentViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.split_payment),
                isLoading = uiState.isLoading,
                onRefresh = viewModel::loadOrder,
                onNavigateBack = onNavigateBack,
            )
        },
        floatingActionButton = {
            if (uiState.summary != null && uiState.summary?.isFullyPaid == false) {
                FloatingActionButton(onClick = viewModel::showAddDialog) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_payment))
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                // Order ID input
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.orderIdInput,
                        onValueChange = viewModel::onOrderIdInputChange,
                        label = { Text(stringResource(Res.string.order_id)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(onClick = viewModel::loadOrder) { Text(stringResource(Res.string.load)) }
                }

                when {
                    uiState.isLoading -> LoadingIndicator()
                    uiState.error != null -> ErrorView(message = uiState.error!!, onRetry = viewModel::loadOrder)
                    uiState.summary == null -> EmptyView(stringResource(Res.string.enter_order_id_message))
                    else -> {
                        val summary = uiState.summary!!
                        SplitPaymentContent(
                            summary = summary,
                            onDeletePayment = viewModel::deletePayment,
                        )
                    }
                }
            }
        }
    }

    // Add Payment Dialog
    if (uiState.showAddDialog) {
        val remaining = uiState.summary?.remaining ?: 0.0
        AlertDialog(
            onDismissRequest = viewModel::dismissAddDialog,
            title = { Text(stringResource(Res.string.add_payment)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.remaining_value, "%.2f".format(remaining)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    // Payment method selection
                    Text(stringResource(Res.string.payment_method), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("CASH", "CARD", "WALLET").forEach { method ->
                            FilterChip(
                                selected = uiState.paymentMethod == method,
                                onClick = { viewModel.onPaymentMethodChange(method) },
                                label = { Text(method) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = uiState.paymentAmount,
                        onValueChange = viewModel::onPaymentAmountChange,
                        label = { Text(stringResource(Res.string.amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = uiState.paymentNote,
                        onValueChange = viewModel::onPaymentNoteChange,
                        label = { Text(stringResource(Res.string.note)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::addPayment,
                    enabled = !uiState.isSaving && uiState.paymentAmount.isNotBlank(),
                ) { Text(stringResource(Res.string.add)) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissAddDialog) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun SplitPaymentContent(summary: SplitPaymentSummary, onDeletePayment: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        // Summary card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (summary.isFullyPaid) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(Res.string.order_total), style = MaterialTheme.typography.bodyMedium)
                        Text("${"%.2f".format(summary.orderTotal)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(Res.string.total_paid), style = MaterialTheme.typography.bodyMedium)
                        Text("${"%.2f".format(summary.totalPaid)}", style = MaterialTheme.typography.titleMedium, color = Color(0xFF4CAF50))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(Res.string.remaining), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${"%.2f".format(summary.remaining)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (summary.isFullyPaid) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (summary.paidPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (summary.isFullyPaid) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(Res.string.fully_paid), style = MaterialTheme.typography.labelLarge, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Payments list
        item {
            Text(stringResource(Res.string.payments_count, summary.paymentCount), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        }

        if (summary.payments.isEmpty()) {
            item { Text(stringResource(Res.string.no_payments_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(summary.payments, key = { it.id }) { payment ->
                PaymentCard(payment = payment, onDelete = { onDeletePayment(payment.id) })
            }
        }
    }
}

@Composable
private fun PaymentCard(payment: OrderPayment, onDelete: () -> Unit) {
    val methodIcon = when {
        payment.isCash -> Icons.Default.Money
        payment.isCard -> Icons.Default.CreditCard
        payment.isWallet -> Icons.Default.AccountBalanceWallet
        else -> Icons.Default.Payment
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(methodIcon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(payment.paymentMethod, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                payment.paidByName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                payment.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("${"%.2f".format(payment.amount)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.remove), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
