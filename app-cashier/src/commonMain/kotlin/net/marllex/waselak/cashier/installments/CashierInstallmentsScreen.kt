package net.marllex.waselak.cashier.installments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import net.marllex.waselak.core.model.InstallmentPayment
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
            PlanDetailContent(
                plan = uiState.selectedPlan!!,
                onRecordPaymentForPayment = viewModel::showPaymentDialogForPayment,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        } else {
            PlansListContent(
                uiState = uiState,
                onSelectPlan = viewModel::selectPlan,
                onStatusFilter = viewModel::onStatusFilter,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        }
    }

    // Record Payment Dialog
    if (uiState.showPaymentDialog) {
        val targetPayment = uiState.selectedPlan?.payments?.find { it.id == uiState.targetPaymentId }
            ?: uiState.selectedPlan?.nextPayment

        AlertDialog(
            onDismissRequest = viewModel::dismissPaymentDialog,
            title = { Text(stringResource(CoreRes.string.record_payment)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (targetPayment != null) {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(CoreRes.string.due_date_value, formatDate(targetPayment.dueDate)), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(CoreRes.string.due_this_month, "%.2f".format(targetPayment.totalDue)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                if (targetPayment.paidAmount > 0) {
                                    Text(stringResource(CoreRes.string.paid_this_month, "%.2f".format(targetPayment.paidAmount)), style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C))
                                    Text(stringResource(CoreRes.string.remaining_this_month, "%.2f".format(targetPayment.remainingDue)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF57C00))
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = uiState.paymentAmount, onValueChange = viewModel::onPaymentAmount, label = { Text(stringResource(CoreRes.string.amount)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = uiState.paymentNote, onValueChange = viewModel::onPaymentNote, label = { Text(stringResource(CoreRes.string.note_optional)) }, modifier = Modifier.fillMaxWidth())
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

// ── Plans List (3-column grid) ──

@Composable
private fun PlansListContent(
    uiState: CashierInstallmentsViewModel.UiState,
    onSelectPlan: (InstallmentPlan) -> Unit,
    onStatusFilter: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Filter chips (full width)
        item(span = { GridItemSpan(3) }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = uiState.statusFilter == null, onClick = { onStatusFilter(null) }, label = { Text(stringResource(CoreRes.string.filter_all)) })
                FilterChip(selected = uiState.statusFilter == "ACTIVE", onClick = { onStatusFilter("ACTIVE") }, label = { Text(stringResource(CoreRes.string.installment_status_active)) })
                FilterChip(selected = uiState.statusFilter == "OVERDUE", onClick = { onStatusFilter("OVERDUE") }, label = { Text(stringResource(CoreRes.string.payment_status_overdue)) })
                FilterChip(selected = uiState.statusFilter == "COMPLETED", onClick = { onStatusFilter("COMPLETED") }, label = { Text(stringResource(CoreRes.string.installment_status_completed)) })
            }
        }

        if (uiState.filteredPlans.isEmpty() && !uiState.isLoading) {
            item(span = { GridItemSpan(3) }) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(CoreRes.string.no_installment_plans), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(uiState.filteredPlans) { plan ->
            PlanCard(plan = plan, onClick = { onSelectPlan(plan) })
        }
    }
}

@Composable
private fun PlanCard(plan: InstallmentPlan, onClick: () -> Unit) {
    val statusColor = when (plan.status) {
        "ACTIVE" -> Color(0xFF388E3C)
        "COMPLETED" -> Color(0xFF1976D2)
        "DEFAULTED" -> Color(0xFFD32F2F)
        else -> Color(0xFF757575)
    }
    val borderColor = when (plan.status) {
        "ACTIVE" -> Color(0xFF388E3C)
        "COMPLETED" -> Color(0xFF1976D2)
        "DEFAULTED" -> Color(0xFFD32F2F)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(borderColor))
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(plan.customerName ?: stringResource(CoreRes.string.customer), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                plan.customerPhone?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("%.2f".format(plan.totalAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                Text(stringResource(CoreRes.string.remaining_value, "%.2f".format(plan.remainingAmount)), style = MaterialTheme.typography.labelSmall, color = Color(0xFFF57C00))
                Text(stringResource(CoreRes.string.months_count, plan.numInstallments) + " • " + "%.2f".format(plan.installmentAmount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatStatus(plan.status), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                    if (plan.overdueCount > 0) {
                        Text("${plan.overdueCount} !", color = Color(0xFFD32F2F), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Plan Detail (grid layout like manager) ──

@Composable
private fun PlanDetailContent(
    plan: InstallmentPlan,
    onRecordPaymentForPayment: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowMs = remember { System.currentTimeMillis() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Customer + status card (full width)
        item(span = { GridItemSpan(3) }) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(plan.customerName ?: "-", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            plan.customerPhone?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        AssistChip(onClick = {}, label = { Text(formatStatus(plan.status)) })
                    }
                }
            }
        }

        // Stat cards (3 columns)
        item { StatCard(stringResource(CoreRes.string.total_amount), "%.2f".format(plan.totalAmount), Color(0xFF1976D2)) }
        item { StatCard(stringResource(CoreRes.string.total_paid_amount), "%.2f".format(plan.paidAmount), Color(0xFF388E3C)) }
        item { StatCard(stringResource(CoreRes.string.total_remaining), "%.2f".format(plan.remainingAmount), if (plan.remainingAmount > 0) Color(0xFFF57C00) else Color(0xFF388E3C)) }

        item { StatCard(stringResource(CoreRes.string.monthly_installment), "%.2f".format(plan.installmentAmount), Color(0xFF1976D2)) }
        item { StatCard(stringResource(CoreRes.string.paid_installments, plan.paidPaymentsCount, plan.numInstallments), "", Color(0xFF388E3C)) }
        item { StatCard(stringResource(CoreRes.string.late_fees), "%.2f".format(plan.totalLateFees), if (plan.totalLateFees > 0) Color(0xFFD32F2F) else Color(0xFF757575)) }

        // Current month card (full width)
        plan.currentMonthPayment(nowMs)?.let { current ->
            item(span = { GridItemSpan(3) }) {
                val isThisMonth = run {
                    val now = java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    val due = java.time.Instant.ofEpochMilli(current.dueDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    now.year == due.year && now.monthValue == due.monthValue
                }
                val cardColor = if (current.isOverdue) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = cardColor),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (isThisMonth) stringResource(CoreRes.string.this_month_summary) else stringResource(CoreRes.string.next_due_date),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        )
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(stringResource(CoreRes.string.due_this_month, ""), "%.2f".format(current.totalDue), Color(0xFF1976D2), Modifier.weight(1f))
                            StatCard(stringResource(CoreRes.string.paid_this_month, ""), "%.2f".format(current.paidAmount), Color(0xFF388E3C), Modifier.weight(1f))
                            StatCard(stringResource(CoreRes.string.remaining_this_month, ""), "%.2f".format(current.remainingDue), if (current.isOverdue) Color(0xFFD32F2F) else Color(0xFFF57C00), Modifier.weight(1f))
                        }
                        Text(stringResource(CoreRes.string.due_date_value, formatDate(current.dueDate)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (current.needsPayment) {
                            Button(
                                onClick = { onRecordPaymentForPayment(current.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                if (current.isPartiallyPaid) Text(stringResource(CoreRes.string.pay_remaining) + " (%.2f)".format(current.remainingDue))
                                else Text(stringResource(CoreRes.string.record_payment) + " (%.2f)".format(current.totalDue))
                            }
                        }
                    }
                }
            }
        }

        // Payment schedule header (full width)
        item(span = { GridItemSpan(3) }) {
            Text(stringResource(CoreRes.string.payment_schedule), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        // Payment grid (3 columns)
        val payments = plan.payments
        items(payments.size, span = { GridItemSpan(1) }) { index ->
            val payment = payments[index]
            PaymentGridCard(
                index = index + 1,
                payment = payment,
                onRecordPayment = { onRecordPaymentForPayment(payment.id) },
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (value.isNotEmpty()) {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun PaymentGridCard(
    index: Int,
    payment: InstallmentPayment,
    onRecordPayment: () -> Unit,
) {
    val statusColor = when (payment.status) {
        "PAID" -> Color(0xFF388E3C)
        "PARTIALLY_PAID" -> Color(0xFFF57C00)
        "OVERDUE" -> Color(0xFFD32F2F)
        "PENDING" -> Color(0xFF757575)
        "WAIVED" -> Color(0xFF9E9E9E)
        else -> Color(0xFF757575)
    }
    val borderColor = when (payment.status) {
        "PAID" -> Color(0xFF388E3C)
        "PARTIALLY_PAID" -> Color(0xFFF57C00)
        "OVERDUE" -> Color(0xFFD32F2F)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(borderColor))
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("#$index", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(formatStatus(payment.status), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                }
                Text(formatDate(payment.dueDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%.2f".format(payment.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (payment.lateFee > 0) {
                    Text("+ %.2f".format(payment.lateFee), color = Color(0xFFD32F2F), style = MaterialTheme.typography.labelSmall)
                }
                if (payment.paidAmount > 0) {
                    Text(stringResource(CoreRes.string.paid_amount_value, "%.2f".format(payment.paidAmount)), color = Color(0xFF388E3C), style = MaterialTheme.typography.labelSmall)
                }
                if (payment.needsPayment && payment.remainingDue > 0 && payment.remainingDue != payment.totalDue) {
                    Text(stringResource(CoreRes.string.remaining_this_month, "%.2f".format(payment.remainingDue)), color = Color(0xFFF57C00), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
                if (payment.needsPayment) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onRecordPayment,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (payment.isPartiallyPaid) stringResource(CoreRes.string.pay_remaining)
                            else stringResource(CoreRes.string.record_payment),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
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
