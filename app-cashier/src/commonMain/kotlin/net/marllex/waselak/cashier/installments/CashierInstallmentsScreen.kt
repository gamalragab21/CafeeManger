package net.marllex.waselak.cashier.installments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.InstallmentPlan
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res as CoreRes
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierInstallmentsScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: CashierInstallmentsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            if (uiState.selectedPlan != null) {
                WaselakTopAppBar(
                    title = "Installment Details",
                    isLoading = uiState.isLoading,
                    onRefresh = { viewModel.selectPlan(uiState.selectedPlan!!) },
                    onNavigateBack = { viewModel.clearSelection() },
                )
            } else {
                WaselakTopAppBar(
                    title = stringResource(CoreRes.string.installments),
                    isLoading = uiState.isLoading,
                    onRefresh = viewModel::load,
                    onNavigateBack = onNavigateBack,
                )
            }
        },
    ) { padding ->
        if (uiState.selectedPlan != null) {
            val plan = uiState.selectedPlan!!
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(plan.customerName ?: "Customer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total"); Text("%.2f".format(plan.totalAmount), fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Remaining"); Text("%.2f".format(plan.remainingAmount), fontWeight = FontWeight.Bold, color = Color(0xFFF57C00))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Monthly"); Text("%.2f".format(plan.installmentAmount))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Status"); Text(plan.status, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Button(onClick = viewModel::showPaymentDialog, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Record Payment")
                    }
                }

                item { Text("Payment Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

                items(plan.payments) { payment ->
                    val statusColor = when (payment.status) {
                        "PAID" -> Color(0xFF388E3C)
                        "OVERDUE" -> Color(0xFFD32F2F)
                        "PENDING" -> Color(0xFFF57C00)
                        else -> Color(0xFF757575)
                    }
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Due: ${formatDate(payment.dueDate)}", style = MaterialTheme.typography.bodyMedium)
                                Text("Amount: %.2f".format(payment.amount), style = MaterialTheme.typography.bodySmall)
                                if (payment.lateFee > 0) Text("+ Fee: %.2f".format(payment.lateFee), color = Color(0xFFD32F2F), style = MaterialTheme.typography.bodySmall)
                                if (payment.isPaid) Text("Paid: %.2f".format(payment.paidAmount), color = Color(0xFF388E3C), style = MaterialTheme.typography.bodySmall)
                            }
                            AssistChip(onClick = {}, label = { Text(payment.status, color = statusColor) })
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (uiState.plans.isEmpty() && !uiState.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No installment plans", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(uiState.plans.filter { it.isActive }) { plan ->
                    ElevatedCard(onClick = { viewModel.selectPlan(plan) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(plan.customerName ?: "Customer", fontWeight = FontWeight.Bold)
                                Text(plan.status, color = Color(0xFF388E3C))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Total: %.2f | Remaining: %.2f".format(plan.totalAmount, plan.remainingAmount), style = MaterialTheme.typography.bodySmall)
                            Text("${plan.numInstallments} months • %.2f/month".format(plan.installmentAmount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (uiState.showPaymentDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPaymentDialog,
            title = { Text("Record Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.selectedPlan?.nextPayment?.let {
                        Text("Expected: %.2f".format(it.totalDue), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(value = uiState.paymentAmount, onValueChange = viewModel::onPaymentAmount, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = uiState.paymentNote, onValueChange = viewModel::onPaymentNote, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = viewModel::recordPayment, enabled = !uiState.isSaving) {
                    if (uiState.isSaving) CircularProgressIndicator(Modifier.size(16.dp)) else Text("Record")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissPaymentDialog) { Text("Cancel") } },
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val date = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return "${date.dayOfMonth}/${date.monthValue}/${date.year}"
}
