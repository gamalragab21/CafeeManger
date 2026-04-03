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
                    title = stringResource(CoreRes.string.installment_details),
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
                            Text(plan.customerName ?: stringResource(CoreRes.string.customer), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(CoreRes.string.total)); Text("%.2f".format(plan.totalAmount), fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(CoreRes.string.remaining_amount)); Text("%.2f".format(plan.remainingAmount), fontWeight = FontWeight.Bold, color = Color(0xFFF57C00))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(CoreRes.string.monthly_amount)); Text("%.2f".format(plan.installmentAmount))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(CoreRes.string.change_status)); Text(formatStatus(plan.status), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Button(onClick = viewModel::showPaymentDialog, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(CoreRes.string.record_payment))
                    }
                }

                item { Text(stringResource(CoreRes.string.payment_schedule), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

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
                                Text(stringResource(CoreRes.string.due_date_value, formatDate(payment.dueDate)), style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(CoreRes.string.amount_value, "%.2f".format(payment.amount)), style = MaterialTheme.typography.bodySmall)
                                if (payment.lateFee > 0) Text(stringResource(CoreRes.string.late_fee_value, "%.2f".format(payment.lateFee)), color = Color(0xFFD32F2F), style = MaterialTheme.typography.bodySmall)
                                if (payment.isPaid) Text(stringResource(CoreRes.string.paid_amount_value, "%.2f".format(payment.paidAmount)), color = Color(0xFF388E3C), style = MaterialTheme.typography.bodySmall)
                            }
                            AssistChip(onClick = {}, label = { Text(formatStatus(payment.status), color = statusColor) })
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
                            Text(stringResource(CoreRes.string.no_installment_plans), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(uiState.plans.filter { it.isActive }) { plan ->
                    ElevatedCard(onClick = { viewModel.selectPlan(plan) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(plan.customerName ?: stringResource(CoreRes.string.customer), fontWeight = FontWeight.Bold)
                                Text(formatStatus(plan.status), color = Color(0xFF388E3C))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(CoreRes.string.total_remaining_summary, "%.2f".format(plan.totalAmount), "%.2f".format(plan.remainingAmount)), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(CoreRes.string.months_monthly_summary, plan.numInstallments, "%.2f".format(plan.installmentAmount)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (uiState.showPaymentDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPaymentDialog,
            title = { Text(stringResource(CoreRes.string.record_payment)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.selectedPlan?.nextPayment?.let {
                        Text(stringResource(CoreRes.string.expected_value, "%.2f".format(it.totalDue)), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(value = uiState.paymentAmount, onValueChange = viewModel::onPaymentAmount, label = { Text(stringResource(CoreRes.string.amount)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = uiState.paymentNote, onValueChange = viewModel::onPaymentNote, label = { Text(stringResource(CoreRes.string.note)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = viewModel::recordPayment, enabled = !uiState.isSaving) {
                    if (uiState.isSaving) CircularProgressIndicator(Modifier.size(16.dp)) else Text(stringResource(CoreRes.string.record))
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissPaymentDialog) { Text(stringResource(CoreRes.string.cancel)) } },
        )
    }
}

@Composable
private fun formatStatus(status: String): String = when (status) {
    "ACTIVE" -> stringResource(CoreRes.string.installment_status_active)
    "COMPLETED" -> stringResource(CoreRes.string.installment_status_completed)
    "DEFAULTED" -> stringResource(CoreRes.string.installment_status_defaulted)
    "CANCELLED" -> stringResource(CoreRes.string.installment_status_cancelled)
    "PAID" -> stringResource(CoreRes.string.payment_status_paid)
    "PARTIALLY_PAID" -> stringResource(CoreRes.string.payment_status_partially_paid_inst)
    "OVERDUE" -> stringResource(CoreRes.string.payment_status_overdue)
    "PENDING" -> stringResource(CoreRes.string.payment_status_pending)
    "WAIVED" -> stringResource(CoreRes.string.payment_status_waived)
    else -> status
}

private fun formatDate(timestamp: Long): String {
    val date = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return "${date.dayOfMonth}/${date.monthValue}/${date.year}"
}
