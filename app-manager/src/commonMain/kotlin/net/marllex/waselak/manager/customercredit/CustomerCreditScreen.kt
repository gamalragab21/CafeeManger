package net.marllex.waselak.manager.customercredit

import net.marllex.waselak.core.common.format.kFormat
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
fun CustomerCreditScreen(
    viewModel: CustomerCreditViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = if (uiState.selectedCredit != null) uiState.selectedCredit!!.customerName ?: stringResource(Res.string.customer) else stringResource(Res.string.customer_credit),
                isLoading = uiState.isLoading,
                onRefresh = viewModel::load,
                onNavigateBack = {
                    if (uiState.selectedCredit != null) viewModel.clearSelection()
                    else onNavigateBack?.invoke()
                },
                actions = {
                    if (uiState.selectedCredit != null) {
                        IconButton(onClick = viewModel::showLimitDialog) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.set_limit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null && uiState.debtors.isEmpty() -> ErrorView(message = uiState.error!!, onRetry = viewModel::load)
            uiState.selectedCredit != null -> CustomerDetailContent(
                modifier = Modifier.padding(padding).fillMaxSize(),
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
                    DebtorCard(credit = credit, onClick = { viewModel.selectCustomer(credit) })
                }
            }
        }
    }

    // Set Credit Limit Dialog
    if (uiState.showLimitDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLimitDialog,
            title = { Text(stringResource(Res.string.set_credit_limit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${stringResource(Res.string.customer)}: ${uiState.selectedCredit?.customerName ?: ""}")
                    Text(stringResource(Res.string.current_limit, kFormat("%.2f", uiState.selectedCredit?.creditLimit ?: 0.0)))
                    OutlinedTextField(
                        value = uiState.newLimit,
                        onValueChange = viewModel::onNewLimitChange,
                        label = { Text("${stringResource(Res.string.new_limit)} *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = { Button(onClick = viewModel::setCreditLimit, enabled = !uiState.isSaving && uiState.newLimit.isNotBlank()) { Text(stringResource(Res.string.update)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissLimitDialog) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtorCard(credit: CustomerCredit, onClick: () -> Unit) {
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
                Text("${kFormat("%.2f", credit.balance)} / ${kFormat("%.2f", credit.creditLimit)}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${kFormat("%.2f", credit.balance)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (credit.hasDebt) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                )
                if (credit.isAtLimit) {
                    Text(stringResource(Res.string.at_limit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
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
                    credit.customerPhone?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(Res.string.outstanding_balance)); Text("${kFormat("%.2f", credit.balance)}", fontWeight = FontWeight.Bold, color = if (credit.hasDebt) MaterialTheme.colorScheme.error else Color(0xFF4CAF50))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(Res.string.credit_limit)); Text("${kFormat("%.2f", credit.creditLimit)}")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(Res.string.available_credit)); Text("${kFormat("%.2f", credit.availableCredit)}", color = Color(0xFF4CAF50))
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
            Text(stringResource(Res.string.transaction_history_count, transactions.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
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
                val typeLabel = when (transaction.type) {
                    "CHARGE" -> stringResource(Res.string.trx_type_charge)
                    "PAYMENT" -> stringResource(Res.string.trx_type_payment)
                    "ADJUSTMENT" -> stringResource(Res.string.trx_type_adjustment)
                    else -> transaction.type
                }
                Text(typeLabel, style = MaterialTheme.typography.labelMedium, color = color)
                transaction.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                transaction.createdByName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (transaction.isPayment) "-" else "+"}${kFormat("%.2f", transaction.amount)}",
                    style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(Res.string.transaction_balance, kFormat("%.2f", transaction.newBalance)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
