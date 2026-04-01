package net.marllex.waselak.manager.installments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun InstallmentsScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: InstallmentsViewModel = koinViewModel(),
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
        floatingActionButton = {
            if (uiState.selectedPlan == null) {
                FloatingActionButton(onClick = viewModel::showCreateDialog) {
                    Icon(Icons.Default.Add, contentDescription = "Create Plan")
                }
            }
        },
    ) { padding ->
        if (uiState.selectedPlan != null) {
            PlanDetailContent(
                plan = uiState.selectedPlan!!,
                onRecordPayment = viewModel::showPaymentDialog,
                onApplyLateFee = viewModel::applyLateFee,
                onChangeStatus = { viewModel.showStatusDialog(uiState.selectedPlan!!) },
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

    // Create Plan Dialog
    if (uiState.showCreateDialog) {
        CreatePlanDialog(
            uiState = uiState,
            onDismiss = viewModel::dismissCreateDialog,
            onCustomerIdChange = viewModel::onCreateCustomerId,
            onTotalAmountChange = viewModel::onCreateTotalAmount,
            onDownPaymentChange = viewModel::onCreateDownPayment,
            onMonthsChange = viewModel::onCreateMonths,
            onLateFeeChange = viewModel::onCreateLateFeePercent,
            onCreate = viewModel::createPlan,
        )
    }

    // Record Payment Dialog
    if (uiState.showPaymentDialog) {
        RecordPaymentDialog(
            uiState = uiState,
            onDismiss = viewModel::dismissPaymentDialog,
            onAmountChange = viewModel::onPaymentAmount,
            onNoteChange = viewModel::onPaymentNote,
            onRecord = viewModel::recordPayment,
        )
    }

    // Status Change Dialog
    uiState.showStatusDialog?.let { plan ->
        StatusChangeDialog(
            plan = plan,
            onDismiss = viewModel::dismissStatusDialog,
            onStatusChange = viewModel::updateStatus,
        )
    }
}

@Composable
private fun PlansListContent(
    uiState: InstallmentsViewModel.UiState,
    onSelectPlan: (InstallmentPlan) -> Unit,
    onStatusFilter: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Analytics summary cards
        uiState.analytics?.let { analytics ->
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard("Total Plans", "${analytics.totalPlans}", Color(0xFF1976D2), Modifier.weight(1f))
                    SummaryCard("Active", "${analytics.activePlans}", Color(0xFF388E3C), Modifier.weight(1f))
                    SummaryCard("Overdue", "${analytics.defaultedPlans}", Color(0xFFD32F2F), Modifier.weight(1f))
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard("Collected", "%.0f".format(analytics.collectedRevenue), Color(0xFF388E3C), Modifier.weight(1f))
                    SummaryCard("Pending", "%.0f".format(analytics.pendingRevenue), Color(0xFFF57C00), Modifier.weight(1f))
                    SummaryCard("Late Fees", "%.0f".format(analytics.lateFeesCollected), Color(0xFFD32F2F), Modifier.weight(1f))
                }
            }
        }

        // Filter chips
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = uiState.statusFilter == null, onClick = { onStatusFilter(null) }, label = { Text("All") })
                FilterChip(selected = uiState.statusFilter == "ACTIVE", onClick = { onStatusFilter("ACTIVE") }, label = { Text("Active") })
                FilterChip(selected = uiState.statusFilter == "OVERDUE", onClick = { onStatusFilter("OVERDUE") }, label = { Text("Overdue") })
                FilterChip(selected = uiState.statusFilter == "COMPLETED", onClick = { onStatusFilter("COMPLETED") }, label = { Text("Completed") })
                FilterChip(selected = uiState.statusFilter == "DEFAULTED", onClick = { onStatusFilter("DEFAULTED") }, label = { Text("Defaulted") })
            }
        }

        // Plans list
        if (uiState.filteredPlans.isEmpty() && !uiState.isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No installment plans found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(uiState.filteredPlans) { plan ->
            PlanCard(plan = plan, onClick = { onSelectPlan(plan) })
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanCard(plan: InstallmentPlan, onClick: () -> Unit) {
    val statusColor = when (plan.status) {
        "ACTIVE" -> Color(0xFF388E3C)
        "COMPLETED" -> Color(0xFF1976D2)
        "DEFAULTED" -> Color(0xFFD32F2F)
        "CANCELLED" -> Color(0xFF757575)
        else -> Color(0xFF757575)
    }

    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(plan.customerName ?: "Customer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    plan.customerPhone?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                AssistChip(onClick = {}, label = { Text(plan.status, color = statusColor) })
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Total: %.2f".format(plan.totalAmount), style = MaterialTheme.typography.bodyMedium)
                    Text("Remaining: %.2f".format(plan.remainingAmount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${plan.numInstallments} months", style = MaterialTheme.typography.bodyMedium)
                    Text("%.2f/month".format(plan.installmentAmount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (plan.overdueCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text("${plan.overdueCount} overdue payments", color = Color(0xFFD32F2F), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PlanDetailContent(
    plan: InstallmentPlan,
    onRecordPayment: () -> Unit,
    onApplyLateFee: () -> Unit,
    onChangeStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Plan info
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Plan Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    InfoRow("Customer", plan.customerName ?: "-")
                    InfoRow("Phone", plan.customerPhone ?: "-")
                    InfoRow("Total Amount", "%.2f".format(plan.totalAmount))
                    InfoRow("Down Payment", "%.2f".format(plan.downPayment))
                    InfoRow("Remaining", "%.2f".format(plan.remainingAmount))
                    InfoRow("Monthly Amount", "%.2f".format(plan.installmentAmount))
                    InfoRow("Months", "${plan.numInstallments}")
                    InfoRow("Late Fee %", "%.1f%%".format(plan.lateFeePercent))
                    InfoRow("Status", plan.status)
                }
            }
        }

        // Actions
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRecordPayment, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Record Payment")
                }
                if (plan.overdueCount > 0 && plan.lateFeePercent > 0) {
                    OutlinedButton(onClick = onApplyLateFee, modifier = Modifier.weight(1f)) {
                        Text("Apply Late Fee")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onChangeStatus, modifier = Modifier.fillMaxWidth()) {
                Text("Change Status")
            }
        }

        // Payment schedule
        item {
            Text("Payment Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(plan.payments) { payment ->
            val statusColor = when (payment.status) {
                "PAID" -> Color(0xFF388E3C)
                "OVERDUE" -> Color(0xFFD32F2F)
                "PENDING" -> Color(0xFFF57C00)
                "WAIVED" -> Color(0xFF757575)
                else -> Color(0xFF757575)
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Due: ${formatDate(payment.dueDate)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Amount: %.2f".format(payment.amount), style = MaterialTheme.typography.bodySmall)
                        if (payment.lateFee > 0) Text("+ Late fee: %.2f".format(payment.lateFee), color = Color(0xFFD32F2F), style = MaterialTheme.typography.bodySmall)
                        if (payment.isPaid) Text("Paid: %.2f".format(payment.paidAmount), color = Color(0xFF388E3C), style = MaterialTheme.typography.bodySmall)
                    }
                    AssistChip(onClick = {}, label = { Text(payment.status, color = statusColor) })
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CreatePlanDialog(
    uiState: InstallmentsViewModel.UiState,
    onDismiss: () -> Unit,
    onCustomerIdChange: (String) -> Unit,
    onTotalAmountChange: (String) -> Unit,
    onDownPaymentChange: (String) -> Unit,
    onMonthsChange: (String) -> Unit,
    onLateFeeChange: (String) -> Unit,
    onCreate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Installment Plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = uiState.createCustomerId, onValueChange = onCustomerIdChange, label = { Text("Customer ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = uiState.createTotalAmount, onValueChange = onTotalAmountChange, label = { Text("Total Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = uiState.createDownPayment, onValueChange = onDownPaymentChange, label = { Text("Down Payment") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = uiState.createMonths, onValueChange = onMonthsChange, label = { Text("Number of Months") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = uiState.createLateFeePercent, onValueChange = onLateFeeChange, label = { Text("Late Fee %") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                val total = uiState.createTotalAmount.toDoubleOrNull() ?: 0.0
                val down = uiState.createDownPayment.toDoubleOrNull() ?: 0.0
                val months = uiState.createMonths.toIntOrNull() ?: 1
                if (total > 0 && months > 0) {
                    val monthly = (total - down) / months
                    Text("Monthly: %.2f".format(monthly), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreate, enabled = !uiState.isCreating) {
                if (uiState.isCreating) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RecordPaymentDialog(
    uiState: InstallmentsViewModel.UiState,
    onDismiss: () -> Unit,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onRecord: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val nextPayment = uiState.selectedPlan?.nextPayment
                if (nextPayment != null) {
                    Text("Due: ${formatDate(nextPayment.dueDate)}", style = MaterialTheme.typography.bodySmall)
                    Text("Expected: %.2f".format(nextPayment.totalDue), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(value = uiState.paymentAmount, onValueChange = onAmountChange, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = uiState.paymentNote, onValueChange = onNoteChange, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = onRecord, enabled = !uiState.isSaving) {
                if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text("Record")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StatusChangeDialog(
    plan: InstallmentPlan,
    onDismiss: () -> Unit,
    onStatusChange: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current: ${plan.status}")
                listOf("ACTIVE", "COMPLETED", "DEFAULTED", "CANCELLED").forEach { status ->
                    if (status != plan.status) {
                        OutlinedButton(onClick = { onStatusChange(status) }, modifier = Modifier.fillMaxWidth()) {
                            Text(status)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun formatDate(timestamp: Long): String {
    val date = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return "${date.dayOfMonth}/${date.monthValue}/${date.year}"
}
