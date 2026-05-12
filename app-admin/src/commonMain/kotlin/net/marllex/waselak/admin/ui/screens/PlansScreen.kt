package net.marllex.waselak.admin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.marllex.waselak.admin.network.PlanDto
import net.marllex.waselak.admin.network.PlanUpdateDto
import net.marllex.waselak.admin.util.LocalWindowSizeClass
import net.marllex.waselak.admin.util.UiMessage
import net.marllex.waselak.admin.util.WindowWidthSizeClass
import net.marllex.waselak.admin.util.resolve
import net.marllex.waselak.admin.viewmodel.PlansViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

@Composable
fun PlansScreen(
    viewModel: PlansViewModel = koinViewModel()
) {
    val widthClass = LocalWindowSizeClass.current
    val plans by viewModel.plans.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val resolvedMessage = message?.resolve()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadPlans()
    }

    LaunchedEffect(message) {
        resolvedMessage?.let {
            viewModel.clearMessage()
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.plans),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            if (isLoading && plans.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (plans.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.no_plans),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (widthClass == WindowWidthSizeClass.COMPACT) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(plans, key = { it.name }) { plan ->
                            PlanCard(
                                plan = plan,
                                isLoading = isLoading,
                                onSave = { update ->
                                    viewModel.updatePlan(plan.name, update)
                                }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 400.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        gridItems(plans, key = { it.name }) { plan ->
                            PlanCard(
                                plan = plan,
                                isLoading = isLoading,
                                onSave = { update ->
                                    viewModel.updatePlan(plan.name, update)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: PlanDto,
    isLoading: Boolean,
    onSave: (PlanUpdateDto) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Editable state initialized from plan
    var displayName by remember(plan) { mutableStateOf(plan.display_name) }
    var priceEgp by remember(plan) { mutableStateOf(plan.price_egp.toString()) }
    var maxManagers by remember(plan) { mutableStateOf(plan.max_managers.toString()) }
    var maxCashiers by remember(plan) { mutableStateOf(plan.max_cashiers.toString()) }
    var maxDelivery by remember(plan) { mutableStateOf(plan.max_delivery.toString()) }
    var maxOrdersPerMonth by remember(plan) { mutableStateOf(plan.max_orders_per_month.toString()) }
    var maxMenuItems by remember(plan) { mutableStateOf(plan.max_menu_items.toString()) }
    var maxBranches by remember(plan) { mutableStateOf(plan.max_branches.toString()) }
    var stockManagement by remember(plan) { mutableStateOf(plan.stock_management) }
    var workerAttendance by remember(plan) { mutableStateOf(plan.worker_attendance) }
    var deliveryModule by remember(plan) { mutableStateOf(plan.delivery_module) }
    var overtime by remember(plan) { mutableStateOf(plan.overtime) }
    var salaries by remember(plan) { mutableStateOf(plan.salaries) }
    var customerManagement by remember(plan) { mutableStateOf(plan.customer_management) }
    var tableManagement by remember(plan) { mutableStateOf(plan.table_management) }
    var digitalReceipt by remember(plan) { mutableStateOf(plan.digital_receipt) }
    var workerQrcode by remember(plan) { mutableStateOf(plan.worker_qrcode) }
    var loyaltyPoints by remember(plan) { mutableStateOf(plan.loyalty_points) }
    var manualDiscount by remember(plan) { mutableStateOf(plan.manual_discount) }
    var offersManagement by remember(plan) { mutableStateOf(plan.offers_management) }
    var cashDrawer by remember(plan) { mutableStateOf(plan.cash_drawer) }
    var splitPayment by remember(plan) { mutableStateOf(plan.split_payment) }
    var customerCredit by remember(plan) { mutableStateOf(plan.customer_credit) }
    var suppliers by remember(plan) { mutableStateOf(plan.suppliers) }
    var returns by remember(plan) { mutableStateOf(plan.returns) }
    var prescriptions by remember(plan) { mutableStateOf(plan.prescriptions) }
    var drugInteractions by remember(plan) { mutableStateOf(plan.drug_interactions) }
    var scheduledOrders by remember(plan) { mutableStateOf(plan.scheduled_orders) }
    var kds by remember(plan) { mutableStateOf(plan.kds) }
    var notifications by remember(plan) { mutableStateOf(plan.notifications) }
    var analytics by remember(plan) { mutableStateOf(plan.analytics) }
    var digitalMenu by remember(plan) { mutableStateOf(plan.digital_menu) }

    val analyticsOptions = listOf("NONE", "BASIC", "ADVANCED")
    var analyticsExpanded by remember { mutableStateOf(false) }
    val digitalMenuOptions = listOf("NONE", "BASIC", "FULL")
    var digitalMenuExpanded by remember { mutableStateOf(false) }

    val planBadgeColor = when (plan.name.uppercase()) {
        "ENTERPRISE" -> MaterialTheme.colorScheme.tertiary
        // After the 3→2 consolidation, PRO is the entry tier. Legacy
        // STARTER / BUSINESS rows fall through to the same color so old
        // analytics charts stay coherent.
        "PRO", "STARTER", "BUSINESS" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = planBadgeColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = plan.display_name,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = planBadgeColor
                        )
                    }

                    Text(
                        text = "EGP ${plan.price_egp}/${stringResource(Res.string.egp_month).substringAfter("/")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
            }

            // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text(stringResource(Res.string.display_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = priceEgp,
                        onValueChange = { priceEgp = it },
                        label = { Text("${stringResource(Res.string.price)} (${stringResource(Res.string.egp_month)})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    PlanLimitField(stringResource(Res.string.max_managers), maxManagers) { maxManagers = it }
                    PlanLimitField(stringResource(Res.string.max_cashiers), maxCashiers) { maxCashiers = it }
                    PlanLimitField(stringResource(Res.string.max_delivery), maxDelivery) { maxDelivery = it }
                    PlanLimitField(stringResource(Res.string.max_orders), maxOrdersPerMonth) { maxOrdersPerMonth = it }
                    PlanLimitField(stringResource(Res.string.max_items), maxMenuItems) { maxMenuItems = it }
                    PlanLimitField(stringResource(Res.string.max_branches), maxBranches) { maxBranches = it }

                    PlanSwitchRow(stringResource(Res.string.stock_management), stockManagement) { stockManagement = it }
                    PlanSwitchRow(stringResource(Res.string.worker_attendance), workerAttendance) { workerAttendance = it }
                    PlanSwitchRow(stringResource(Res.string.delivery_module), deliveryModule) { deliveryModule = it }
                    PlanSwitchRow(stringResource(Res.string.overtime), overtime) { overtime = it }
                    PlanSwitchRow(stringResource(Res.string.salaries), salaries) { salaries = it }
                    PlanSwitchRow(stringResource(Res.string.customer_management), customerManagement) { customerManagement = it }
                    PlanSwitchRow(stringResource(Res.string.table_management), tableManagement) { tableManagement = it }
                    PlanSwitchRow(stringResource(Res.string.digital_receipt), digitalReceipt) { digitalReceipt = it }
                    PlanSwitchRow(stringResource(Res.string.worker_qrcode), workerQrcode) { workerQrcode = it }
                    PlanSwitchRow(stringResource(Res.string.loyalty_points), loyaltyPoints) { loyaltyPoints = it }
                    PlanSwitchRow(stringResource(Res.string.manual_discount), manualDiscount) { manualDiscount = it }
                    PlanSwitchRow(stringResource(Res.string.offers_management), offersManagement) { offersManagement = it }
                    PlanSwitchRow(stringResource(Res.string.cash_drawer), cashDrawer) { cashDrawer = it }
                    PlanSwitchRow(stringResource(Res.string.split_payment), splitPayment) { splitPayment = it }
                    PlanSwitchRow(stringResource(Res.string.customer_credit), customerCredit) { customerCredit = it }
                    PlanSwitchRow(stringResource(Res.string.suppliers_management), suppliers) { suppliers = it }
                    PlanSwitchRow(stringResource(Res.string.returns_management), returns) { returns = it }
                    PlanSwitchRow(stringResource(Res.string.prescriptions), prescriptions) { prescriptions = it }
                    PlanSwitchRow(stringResource(Res.string.drug_interactions), drugInteractions) { drugInteractions = it }
                    PlanSwitchRow(stringResource(Res.string.scheduled_orders), scheduledOrders) { scheduledOrders = it }
                    PlanSwitchRow(stringResource(Res.string.kds_display), kds) { kds = it }
                    PlanSwitchRow(stringResource(Res.string.notifications), notifications) { notifications = it }

                    // Analytics dropdown
                    PlanDropdown(
                        label = stringResource(Res.string.analytics_level),
                        value = analytics,
                        options = analyticsOptions,
                        expanded = analyticsExpanded,
                        onExpandedChange = { analyticsExpanded = it },
                        onSelect = { analytics = it }
                    )

                    // Digital Menu dropdown
                    PlanDropdown(
                        label = stringResource(Res.string.digital_menu_level),
                        value = digitalMenu,
                        options = digitalMenuOptions,
                        expanded = digitalMenuExpanded,
                        onExpandedChange = { digitalMenuExpanded = it },
                        onSelect = { digitalMenu = it }
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onSave(
                                PlanUpdateDto(
                                    display_name = displayName,
                                    price_egp = priceEgp.toIntOrNull(),
                                    max_managers = maxManagers.toIntOrNull(),
                                    max_cashiers = maxCashiers.toIntOrNull(),
                                    max_delivery = maxDelivery.toIntOrNull(),
                                    max_orders_per_month = maxOrdersPerMonth.toIntOrNull(),
                                    max_menu_items = maxMenuItems.toIntOrNull(),
                                    max_branches = maxBranches.toIntOrNull(),
                                    stock_management = stockManagement,
                                    worker_attendance = workerAttendance,
                                    delivery_module = deliveryModule,
                                    overtime = overtime,
                                    salaries = salaries,
                                    customer_management = customerManagement,
                                    table_management = tableManagement,
                                    digital_receipt = digitalReceipt,
                                    worker_qrcode = workerQrcode,
                                    loyalty_points = loyaltyPoints,
                                    manual_discount = manualDiscount,
                                    offers_management = offersManagement,
                                    cash_drawer = cashDrawer,
                                    split_payment = splitPayment,
                                    customer_credit = customerCredit,
                                    suppliers = suppliers,
                                    returns = returns,
                                    prescriptions = prescriptions,
                                    drug_interactions = drugInteractions,
                                    scheduled_orders = scheduledOrders,
                                    kds = kds,
                                    notifications = notifications,
                                    analytics = analytics,
                                    digital_menu = digitalMenu
                                )
                            )
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(Res.string.save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanLimitField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val unlimitedText = stringResource(Res.string.unlimited)
    val displayHint = if (value == "-1") unlimitedText else null

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = displayHint?.let { { Text(it, color = MaterialTheme.colorScheme.primary) } }
    )
}

@Composable
private fun PlanSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDropdown(
    label: String,
    value: String,
    options: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}
