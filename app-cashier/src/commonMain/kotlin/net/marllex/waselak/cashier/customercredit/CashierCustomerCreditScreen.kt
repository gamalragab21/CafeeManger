package net.marllex.waselak.cashier.customercredit

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
import net.marllex.waselak.core.model.CreditTransaction
import net.marllex.waselak.core.model.CustomerCredit
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierCustomerCreditScreen(
    viewModel: CashierCustomerCreditViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.selectedCredit != null) uiState.selectedCredit!!.customerName ?: stringResource(Res.string.customer) else stringResource(Res.string.customer_credit)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.selectedCredit != null) viewModel.clearSelection()
                        else onNavigateBack?.invoke()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load) { Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.refresh)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            if (uiState.selectedCredit != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(onClick = viewModel::showChargeDialog) {
                        Icon(Icons.Default.AddCircle, contentDescription = stringResource(Res.string.charge_credit))
                    }
                    FloatingActionButton(onClick = viewModel::showPaymentDialog, containerColor = Color(0xFF4CAF50)) {
                        Icon(Icons.Default.Payment, contentDescription = stringResource(Res.string.record_payment))
                    }
                }
            }
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null && uiState.debtors.isEmpty() -> ErrorView(message = uiState.error!!, onRetry = viewModel::load)
            uiState.selectedCredit != null -> CustomerDetailContent(
                modifier = Modifier.padding(padding),
                credit = uiState.selectedCredit!!,
                transactions = uiState.transactions,
            )
            uiState.debtors.isEmpty() -> EmptyView(stringResource(Res.string.no_customer_credit))
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(uiState.debtors, key = { it.id }) { credit ->
                    CreditCard(credit = credit, onClick = { viewModel.selectCustomer(credit) })
                }
            }
        }
    }

    // Payment Dialog
    if (uiState.showPaymentDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPaymentDialog,
            title = { Text(stringResource(Res.string.record_payment)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.balance_value, "%.2f".format(uiState.selectedCredit?.balance ?: 0.0)), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = uiState.paymentAmount, onValueChange = viewModel::onPaymentAmountChange,
                        label = { Text(stringResource(Res.string.amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                    OutlinedTextField(value = uiState.paymentNote, onValueChange = viewModel::onPaymentNoteChange, label = { Text(stringResource(Res.string.note)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = viewModel::recordPayment, enabled = !uiState.isSaving && uiState.paymentAmount.isNotBlank()) { Text(stringResource(Res.string.record)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissPaymentDialog) { Text(stringResource(Res.string.cancel)) } },
        )
    }

    // Charge Dialog
    if (uiState.showChargeDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissChargeDialog,
            title = { Text(stringResource(Res.string.charge_credit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.available_value, "%.2f".format(uiState.selectedCredit?.availableCredit ?: 0.0)), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = uiState.chargeAmount, onValueChange = viewModel::onChargeAmountChange,
                        label = { Text(stringResource(Res.string.amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                    OutlinedTextField(value = uiState.chargeNote, onValueChange = viewModel::onChargeNoteChange, label = { Text(stringResource(Res.string.note)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = viewModel::recordCharge, enabled = !uiState.isSaving && uiState.chargeAmount.isNotBlank()) { Text(stringResource(Res.string.charge)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissChargeDialog) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreditCard(credit: CustomerCredit, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(credit.customerName ?: stringResource(Res.string.customer), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                credit.customerPhone?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (credit.usagePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(0.6f),
                    color = if (credit.isAtLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text("${"%.2f".format(credit.balance)} / ${"%.2f".format(credit.creditLimit)}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${"%.2f".format(credit.balance)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (credit.hasDebt) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
            )
        }
    }
}

@Composable
private fun CustomerDetailContent(modifier: Modifier, credit: CustomerCredit, transactions: List<CreditTransaction>) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        // Summary card
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(Res.string.balance)); Text("${"%.2f".format(credit.balance)}", fontWeight = FontWeight.Bold, color = if (credit.hasDebt) MaterialTheme.colorScheme.error else Color(0xFF4CAF50))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(Res.string.credit_limit)); Text("${"%.2f".format(credit.creditLimit)}")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(Res.string.available)); Text("${"%.2f".format(credit.availableCredit)}", color = Color(0xFF4CAF50))
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (credit.usagePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            Text(stringResource(Res.string.transactions_count, transactions.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        }

        if (transactions.isEmpty()) {
            item { Text(stringResource(Res.string.no_transactions_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(transactions, key = { it.id }) { transaction ->
                TransactionRow(transaction)
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: CreditTransaction) {
    val color = when {
        transaction.isPayment -> Color(0xFF4CAF50)
        transaction.isCharge -> Color(0xFFF44336)
        else -> Color(0xFF2196F3)
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.type, style = MaterialTheme.typography.labelMedium, color = color)
                transaction.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                transaction.createdByName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (transaction.isPayment) "-" else "+"}${"%.2f".format(transaction.amount)}",
                    style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(Res.string.transaction_balance, "%.2f".format(transaction.newBalance)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
