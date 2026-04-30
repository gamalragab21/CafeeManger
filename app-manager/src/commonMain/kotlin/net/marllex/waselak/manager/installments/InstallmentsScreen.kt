package net.marllex.waselak.manager.installments

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.core.common.format.kFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.Customer
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
        floatingActionButton = {
            if (uiState.selectedPlan == null) {
                FloatingActionButton(onClick = viewModel::showCreateDialog) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(CoreRes.string.create_plan))
                }
            }
        },
    ) { padding ->
        if (uiState.selectedPlan != null) {
            PlanDetailContent(
                plan = uiState.selectedPlan!!,
                onRecordPaymentForPayment = viewModel::showPaymentDialogForPayment,
                onApplyLateFeeForPayment = viewModel::applyLateFeeForPayment,
                onToggleLateFee = viewModel::toggleLateFee,
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
            onTotalAmountChange = viewModel::onCreateTotalAmount,
            onDownPaymentChange = viewModel::onCreateDownPayment,
            onMonthsChange = viewModel::onCreateMonths,
            onLateFeeChange = viewModel::onCreateLateFeePercent,
            onCustomerSearch = viewModel::onCustomerSearch,
            onSelectCustomer = viewModel::selectCustomer,
            onClearCustomer = viewModel::clearCustomerSelection,
            onShowCreateCustomer = viewModel::showCreateCustomerForm,
            onHideCreateCustomer = viewModel::hideCreateCustomerForm,
            onNewCustomerPhone = viewModel::onNewCustomerPhone,
            onNewCustomerName = viewModel::onNewCustomerName,
            onNewCustomerAddress = viewModel::onNewCustomerAddress,
            onCreateCustomer = viewModel::createNewCustomer,
            onStartMonthChange = viewModel::onCreateStartMonth,
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
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Analytics summary cards (full width rows)
        uiState.analytics?.let { analytics ->
            item(span = { GridItemSpan(1) }) { StatCard(stringResource(CoreRes.string.total_plans), "${analytics.totalPlans}", Color(0xFF1976D2)) }
            item(span = { GridItemSpan(1) }) { StatCard(stringResource(CoreRes.string.active_plans), "${analytics.activePlans}", Color(0xFF388E3C)) }
            item(span = { GridItemSpan(1) }) { StatCard(stringResource(CoreRes.string.overdue_plans), "${analytics.defaultedPlans}", Color(0xFFD32F2F)) }
            item(span = { GridItemSpan(1) }) { StatCard(stringResource(CoreRes.string.collected), kFormat("%.0f", analytics.collectedRevenue), Color(0xFF388E3C)) }
            item(span = { GridItemSpan(1) }) { StatCard(stringResource(CoreRes.string.pending_amount), kFormat("%.0f", analytics.pendingRevenue), Color(0xFFF57C00)) }
            item(span = { GridItemSpan(1) }) { StatCard(stringResource(CoreRes.string.late_fees), kFormat("%.0f", analytics.lateFeesCollected), Color(0xFFD32F2F)) }
        }

        // Filter chips (full width)
        item(span = { GridItemSpan(3) }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = uiState.statusFilter == null, onClick = { onStatusFilter(null) }, label = { Text(stringResource(CoreRes.string.filter_all)) })
                FilterChip(selected = uiState.statusFilter == "ACTIVE", onClick = { onStatusFilter("ACTIVE") }, label = { Text(stringResource(CoreRes.string.installment_status_active)) })
                FilterChip(selected = uiState.statusFilter == "OVERDUE", onClick = { onStatusFilter("OVERDUE") }, label = { Text(stringResource(CoreRes.string.payment_status_overdue)) })
                FilterChip(selected = uiState.statusFilter == "COMPLETED", onClick = { onStatusFilter("COMPLETED") }, label = { Text(stringResource(CoreRes.string.installment_status_completed)) })
                FilterChip(selected = uiState.statusFilter == "DEFAULTED", onClick = { onStatusFilter("DEFAULTED") }, label = { Text(stringResource(CoreRes.string.installment_status_defaulted)) })
            }
        }

        // Plans grid (3 columns)
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

@Composable
private fun PlanCard(plan: InstallmentPlan, onClick: () -> Unit) {
    val statusColor = when (plan.status) {
        "ACTIVE" -> Color(0xFF388E3C)
        "COMPLETED" -> Color(0xFF1976D2)
        "DEFAULTED" -> Color(0xFFD32F2F)
        "CANCELLED" -> Color(0xFF757575)
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
                Text(kFormat("%.2f", plan.totalAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                Text(stringResource(CoreRes.string.remaining_value, kFormat("%.2f", plan.remainingAmount)), style = MaterialTheme.typography.labelSmall, color = Color(0xFFF57C00))
                Text(stringResource(CoreRes.string.months_count, plan.numInstallments) + " • " + kFormat("%.2f", plan.installmentAmount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun PlanDetailContent(
    plan: InstallmentPlan,
    onRecordPaymentForPayment: (String?) -> Unit,
    onApplyLateFeeForPayment: (String) -> Unit,
    onToggleLateFee: (String, Boolean) -> Unit,
    onChangeStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowMs = remember { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Top card: customer + status (full width) ──
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

        // ── Financial summary grid: 3 columns of stat cards ──
        item { StatCard(stringResource(CoreRes.string.total_amount), kFormat("%.2f", plan.totalAmount), Color(0xFF1976D2)) }
        item { StatCard(stringResource(CoreRes.string.total_paid_amount), kFormat("%.2f", plan.paidAmount), Color(0xFF388E3C)) }
        item { StatCard(stringResource(CoreRes.string.total_remaining), kFormat("%.2f", plan.remainingAmount), if (plan.remainingAmount > 0) Color(0xFFF57C00) else Color(0xFF388E3C)) }

        item { StatCard(stringResource(CoreRes.string.monthly_installment), kFormat("%.2f", plan.installmentAmount), Color(0xFF1976D2)) }
        item { StatCard(stringResource(CoreRes.string.paid_installments, plan.paidPaymentsCount, plan.numInstallments), "", Color(0xFF388E3C)) }
        item { StatCard(stringResource(CoreRes.string.late_fees), kFormat("%.2f", plan.totalLateFees), if (plan.totalLateFees > 0) Color(0xFFD32F2F) else Color(0xFF757575)) }

        if (plan.downPayment > 0 || plan.lateFeePercent > 0) {
            item { StatCard(stringResource(CoreRes.string.down_payment), kFormat("%.2f", plan.downPayment), Color(0xFF1976D2)) }
            item { StatCard(stringResource(CoreRes.string.late_fee_percent), kFormat("%.1f%%", plan.lateFeePercent), Color(0xFFD32F2F)) }
            item { StatCard(stringResource(CoreRes.string.plan_start_date), formatDate(plan.startDate), Color(0xFF757575)) }
        }

        // ── Current month / next due card (full width) ──
        plan.currentMonthPayment(nowMs)?.let { current ->
            item(span = { GridItemSpan(3) }) {
                val isThisMonth = run {
                    val now = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(TimeZone.currentSystemDefault()).date
                    val due = Instant.fromEpochMilliseconds(current.dueDate).toLocalDateTime(TimeZone.currentSystemDefault()).date
                    now.year == due.year && now.monthNumber == due.monthNumber
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
                            StatCard(stringResource(CoreRes.string.due_this_month, ""), kFormat("%.2f", current.totalDue), Color(0xFF1976D2), Modifier.weight(1f))
                            StatCard(stringResource(CoreRes.string.paid_this_month, ""), kFormat("%.2f", current.paidAmount), Color(0xFF388E3C), Modifier.weight(1f))
                            StatCard(stringResource(CoreRes.string.remaining_this_month, ""), kFormat("%.2f", current.remainingDue), if (current.isOverdue) Color(0xFFD32F2F) else Color(0xFFF57C00), Modifier.weight(1f))
                        }
                        if (current.lateFee > 0) {
                            Text(stringResource(CoreRes.string.late_fee_value, kFormat("%.2f", current.lateFee)), color = Color(0xFFD32F2F), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(stringResource(CoreRes.string.due_date_value, formatDate(current.dueDate)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (current.needsPayment) {
                            Button(
                                onClick = { onRecordPaymentForPayment(current.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                if (current.isPartiallyPaid) Text(stringResource(CoreRes.string.pay_remaining) + kFormat(" (%.2f)", current.remainingDue))
                                else Text(stringResource(CoreRes.string.record_payment) + kFormat(" (%.2f)", current.totalDue))
                            }
                        }
                    }
                }
            }
        }

        // Change status button (full width)
        item(span = { GridItemSpan(3) }) {
            OutlinedButton(onClick = onChangeStatus, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(CoreRes.string.change_status))
            }
        }

        // ── Payment schedule header (full width) ──
        item(span = { GridItemSpan(3) }) {
            Text(stringResource(CoreRes.string.payment_schedule), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        // ── Payment schedule grid (3 columns) ──
        val payments = plan.payments
        items(payments.size, span = { GridItemSpan(1) }) { index ->
            val payment = payments[index]
            PaymentGridCard(
                index = index + 1,
                payment = payment,
                lateFeePercent = plan.lateFeePercent,
                onRecordPayment = { onRecordPaymentForPayment(payment.id) },
                onApplyFee = { onApplyLateFeeForPayment(payment.id) },
                onToggleLateFee = { enabled -> onToggleLateFee(payment.id, enabled) },
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
    payment: net.marllex.waselak.core.model.InstallmentPayment,
    lateFeePercent: Double,
    onRecordPayment: () -> Unit,
    onApplyFee: () -> Unit,
    onToggleLateFee: (Boolean) -> Unit,
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
            // Status strip at top
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(borderColor))

            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Header: #N + status
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("#$index", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(formatStatus(payment.status), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                }
                // Due date
                Text(formatDate(payment.dueDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Amount
                Text(kFormat("%.2f", payment.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                // Late fee
                if (payment.lateFee > 0) {
                    Text(kFormat("+ %.2f", payment.lateFee), color = Color(0xFFD32F2F), style = MaterialTheme.typography.labelSmall)
                }
                // Paid
                if (payment.paidAmount > 0) {
                    Text(stringResource(CoreRes.string.paid_amount_value, kFormat("%.2f", payment.paidAmount)), color = Color(0xFF388E3C), style = MaterialTheme.typography.labelSmall)
                }
                // Remaining
                if (payment.needsPayment && payment.remainingDue > 0 && payment.remainingDue != payment.totalDue) {
                    Text(stringResource(CoreRes.string.remaining_this_month, kFormat("%.2f", payment.remainingDue)), color = Color(0xFFF57C00), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
                // Actions
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
                    if (payment.canApplyLateFee && lateFeePercent > 0) {
                        OutlinedButton(
                            onClick = onApplyFee,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(stringResource(CoreRes.string.apply_fee), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                    // Late fee toggle (manager can enable/disable per payment)
                    if (lateFeePercent > 0 && !payment.isPaid) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (payment.lateFeeEnabled) stringResource(CoreRes.string.late_fee_on) else stringResource(CoreRes.string.late_fee_off),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (payment.lateFeeEnabled) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Switch(
                                checked = payment.lateFeeEnabled,
                                onCheckedChange = { onToggleLateFee(it) },
                                modifier = Modifier.height(24.dp),
                            )
                        }
                    }
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
    onTotalAmountChange: (String) -> Unit,
    onDownPaymentChange: (String) -> Unit,
    onMonthsChange: (String) -> Unit,
    onLateFeeChange: (String) -> Unit,
    onCustomerSearch: (String) -> Unit,
    onSelectCustomer: (Customer) -> Unit,
    onClearCustomer: () -> Unit,
    onShowCreateCustomer: () -> Unit,
    onHideCreateCustomer: () -> Unit,
    onNewCustomerPhone: (String) -> Unit,
    onNewCustomerName: (String) -> Unit,
    onNewCustomerAddress: (String) -> Unit,
    onCreateCustomer: () -> Unit,
    onStartMonthChange: (Int) -> Unit,
    onCreate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CoreRes.string.create_plan)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Customer selector
                if (uiState.selectedCustomer != null) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(uiState.selectedCustomer.name ?: uiState.selectedCustomer.phone, fontWeight = FontWeight.Bold)
                                Text(uiState.selectedCustomer.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = onClearCustomer) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    }
                } else if (uiState.showCreateCustomer) {
                    // Create new customer form
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(CoreRes.string.create_new_customer), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                IconButton(onClick = onHideCreateCustomer) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                            OutlinedTextField(
                                value = uiState.newCustomerName,
                                onValueChange = onNewCustomerName,
                                label = { Text(stringResource(CoreRes.string.customer)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = uiState.newCustomerPhone,
                                onValueChange = onNewCustomerPhone,
                                label = { Text(stringResource(CoreRes.string.phone)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                isError = uiState.createCustomerError != null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            // Show error for duplicate phone
                            if (uiState.createCustomerError != null) {
                                val existingName = uiState.createCustomerError.substringAfter("phone_exists:", "")
                                Text(
                                    text = stringResource(CoreRes.string.phone_already_exists, existingName),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            OutlinedTextField(
                                value = uiState.newCustomerAddress,
                                onValueChange = onNewCustomerAddress,
                                label = { Text(stringResource(CoreRes.string.address)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = onCreateCustomer,
                                enabled = uiState.newCustomerName.isNotBlank() && uiState.newCustomerPhone.isNotBlank() && !uiState.isCreatingCustomer && uiState.createCustomerError == null,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (uiState.isCreatingCustomer) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                else Text(stringResource(CoreRes.string.create))
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = uiState.customerSearchQuery,
                        onValueChange = onCustomerSearch,
                        label = { Text(stringResource(CoreRes.string.search_customer)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (uiState.customerSearchQuery.isNotBlank() && uiState.filteredCustomers.isNotEmpty()) {
                        ElevatedCard(modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                            LazyColumn {
                                items(uiState.filteredCustomers.take(5)) { customer ->
                                    ListItem(
                                        headlineContent = { Text(customer.name ?: customer.phone) },
                                        supportingContent = { if (customer.name != null) Text(customer.phone) },
                                        modifier = Modifier.clickable { onSelectCustomer(customer) },
                                    )
                                }
                            }
                        }
                    }
                    // "Create New Customer" button
                    TextButton(onClick = onShowCreateCustomer, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(CoreRes.string.create_new_customer))
                    }
                }

                OutlinedTextField(value = uiState.createTotalAmount, onValueChange = onTotalAmountChange, label = { Text(stringResource(CoreRes.string.total_amount)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = uiState.createDownPayment, onValueChange = onDownPaymentChange, label = { Text(stringResource(CoreRes.string.down_payment)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = uiState.createMonths, onValueChange = onMonthsChange, label = { Text(stringResource(CoreRes.string.num_months)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = uiState.createLateFeePercent, onValueChange = onLateFeeChange, label = { Text(stringResource(CoreRes.string.late_fee_percent)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

                // Start month selector
                Text(stringResource(CoreRes.string.start_month), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = uiState.createStartMonth == 0, onClick = { onStartMonthChange(0) }, label = { Text(stringResource(CoreRes.string.this_month_label)) })
                    FilterChip(selected = uiState.createStartMonth == 1, onClick = { onStartMonthChange(1) }, label = { Text(stringResource(CoreRes.string.next_month_label)) })
                    FilterChip(selected = uiState.createStartMonth == 2, onClick = { onStartMonthChange(2) }, label = { Text(stringResource(CoreRes.string.after_months_label, 2)) })
                }

                val total = uiState.createTotalAmount.toDoubleOrNull() ?: 0.0
                val down = uiState.createDownPayment.toDoubleOrNull() ?: 0.0
                val months = uiState.createMonths.toIntOrNull() ?: 1
                if (total > 0 && months > 0) {
                    val monthly = (total - down) / months
                    Text(stringResource(CoreRes.string.monthly_preview, kFormat("%.2f", monthly)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreate, enabled = !uiState.isCreating && uiState.selectedCustomer != null) {
                if (uiState.isCreating) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text(stringResource(CoreRes.string.create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
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
    // Find the specific payment being targeted
    val targetPayment = uiState.selectedPlan?.payments?.find { it.id == uiState.targetPaymentId }
        ?: uiState.selectedPlan?.nextPayment

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CoreRes.string.record_payment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (targetPayment != null) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(CoreRes.string.due_date_value, formatDate(targetPayment.dueDate)), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(CoreRes.string.amount_value, kFormat("%.2f", targetPayment.amount)), style = MaterialTheme.typography.bodySmall)
                            if (targetPayment.lateFee > 0) {
                                Text(stringResource(CoreRes.string.late_fee_value, kFormat("%.2f", targetPayment.lateFee)), style = MaterialTheme.typography.bodySmall, color = Color(0xFFD32F2F))
                            }
                            Text(
                                stringResource(CoreRes.string.due_this_month, kFormat("%.2f", targetPayment.totalDue)),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                            )
                            if (targetPayment.paidAmount > 0) {
                                Text(stringResource(CoreRes.string.paid_this_month, kFormat("%.2f", targetPayment.paidAmount)), style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C))
                                Text(
                                    stringResource(CoreRes.string.remaining_this_month, kFormat("%.2f", targetPayment.remainingDue)),
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF57C00),
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(value = uiState.paymentAmount, onValueChange = onAmountChange, label = { Text(stringResource(CoreRes.string.amount)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = uiState.paymentNote, onValueChange = onNoteChange, label = { Text(stringResource(CoreRes.string.note_optional)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = onRecord, enabled = !uiState.isSaving) {
                if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text(stringResource(CoreRes.string.record))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
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
        title = { Text(stringResource(CoreRes.string.change_status)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(CoreRes.string.current_status, formatStatus(plan.status)))
                listOf("ACTIVE", "COMPLETED", "DEFAULTED", "CANCELLED").forEach { status ->
                    if (status != plan.status) {
                        OutlinedButton(onClick = { onStatusChange(status) }, modifier = Modifier.fillMaxWidth()) {
                            Text(formatStatus(status))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.close)) } },
    )
}

private fun formatDate(timestamp: Long): String {
    val date = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.dayOfMonth}/${date.monthNumber}/${date.year}"
}
