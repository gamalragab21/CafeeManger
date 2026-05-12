package net.marllex.waselak.admin.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.admin.network.*
import net.marllex.waselak.admin.ui.components.DateRangeSelector
import net.marllex.waselak.admin.util.UiMessage
import net.marllex.waselak.admin.util.formatDecimal
import net.marllex.waselak.admin.util.resolve
import net.marllex.waselak.admin.viewmodel.VendorDetailViewModel
import net.marllex.waselak.core.model.VendorTypeConfigs
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorDetailScreen(
    vendorId: String,
    onBack: () -> Unit,
    viewModel: VendorDetailViewModel = koinViewModel()
) {
    val detail by viewModel.detail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val error by viewModel.error.collectAsState()
    val resolvedError = error?.resolve()
    val message by viewModel.message.collectAsState()
    val resolvedMessage = message?.resolve()

    var isEditMode by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vendorId) {
        viewModel.loadVendorDetail(vendorId)
    }

    LaunchedEffect(message) {
        message?.let { msg ->
            resolvedMessage?.let { snackbarHostState.showSnackbar(it) }
            viewModel.clearMessage()
            if (msg.isSuccess) {
                isEditMode = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(detail?.vendor?.name ?: stringResource(Res.string.vendor_details)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditMode) {
                            isEditMode = false
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                actions = {
                    if (detail != null && !isEditMode) {
                        IconButton(onClick = { isEditMode = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.edit_mode))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading && detail == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null && detail == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(resolvedError ?: stringResource(Res.string.error_loading_vendor))
                        Button(onClick = { viewModel.retry(vendorId) }) {
                            Text(stringResource(Res.string.retry))
                        }
                    }
                }
                detail != null && isEditMode -> {
                    VendorEditContent(
                        detail = detail!!,
                        isSaving = isSaving,
                        onSave = { request ->
                            viewModel.updateVendor(vendorId, request)
                        },
                        onChangePlan = { plan ->
                            viewModel.changePlan(vendorId, plan)
                        },
                        onCancel = { isEditMode = false }
                    )
                }
                detail != null -> {
                    VendorTabbedContent(
                        vendorId = vendorId,
                        detail = detail!!,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

// ─── Edit Mode ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VendorEditContent(
    detail: VendorDetailDto,
    isSaving: Boolean,
    onSave: (UpdateVendorRequest) -> Unit,
    onChangePlan: (String) -> Unit,
    onCancel: () -> Unit
) {
    val vendor = detail.vendor

    // Editable fields initialized from current vendor data
    var name by remember(vendor) { mutableStateOf(vendor.name) }
    var address by remember(vendor) { mutableStateOf(vendor.address) }
    var contactPhone by remember(vendor) { mutableStateOf(vendor.contact_phone) }
    var walletPhone by remember(vendor) { mutableStateOf(vendor.wallet_phone ?: "") }
    var deliveryFee by remember(vendor) { mutableStateOf(vendor.default_delivery_fee.toString()) }

    // Business type dropdown
    val businessTypes = listOf("RESTAURANT", "CAFE", "PHARMACY", "BAKERY", "SUPERMARKET", "GROCERY", "RETAIL", "JUICE_BAR")
    var businessType by remember(vendor) { mutableStateOf(vendor.business_type) }
    var businessTypeExpanded by remember { mutableStateOf(false) }

    // Store type dropdown
    val storeTypes = listOf("RESTAURANT", "CAFE", "RETAIL", "GROCERY")
    var storeType by remember(vendor) { mutableStateOf(vendor.store_type ?: "RESTAURANT") }
    var storeTypeExpanded by remember { mutableStateOf(false) }

    // Channel toggles
    var enableTables by remember(vendor) { mutableStateOf(vendor.enable_tables) }
    var enableKds by remember(vendor) { mutableStateOf(vendor.enable_kds) }
    var enableDineIn by remember(vendor) { mutableStateOf(vendor.enable_dine_in) }
    var enableDelivery by remember(vendor) { mutableStateOf(vendor.enable_delivery) }
    var enableTakeaway by remember(vendor) { mutableStateOf(vendor.enable_takeaway) }
    var enableInStore by remember(vendor) { mutableStateOf(vendor.enable_in_store) }
    var enablePickupLater by remember(vendor) { mutableStateOf(vendor.enable_pickup_later) }

    // Tax & Stock
    var taxEnabled by remember(vendor) { mutableStateOf(vendor.tax_enabled) }
    var defaultTaxPercent by remember(vendor) { mutableStateOf(vendor.default_tax_percent.toString()) }
    val stockModes = listOf("NONE", "WARN", "ENFORCE")
    var stockMode by remember(vendor) { mutableStateOf(vendor.stock_mode) }
    var stockModeExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // ─── Vendor Info Section ─────────────────────
        EditSectionHeader(stringResource(Res.string.vendor_info))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(Res.string.vendor_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text(stringResource(Res.string.vendor_address)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = contactPhone,
            onValueChange = { contactPhone = it },
            label = { Text(stringResource(Res.string.vendor_phone)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )

        OutlinedTextField(
            value = walletPhone,
            onValueChange = { walletPhone = it },
            label = { Text(stringResource(Res.string.wallet_phone)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )

        OutlinedTextField(
            value = deliveryFee,
            onValueChange = { deliveryFee = it },
            label = { Text(stringResource(Res.string.delivery_fee)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        // Store Type Dropdown
        ExposedDropdownMenuBox(
            expanded = storeTypeExpanded,
            onExpandedChange = { storeTypeExpanded = it }
        ) {
            OutlinedTextField(
                value = storeType,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.store_type)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = storeTypeExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = storeTypeExpanded,
                onDismissRequest = { storeTypeExpanded = false }
            ) {
                storeTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type) },
                        onClick = {
                            storeType = type
                            storeTypeExpanded = false
                        }
                    )
                }
            }
        }

        // Business Type Dropdown
        ExposedDropdownMenuBox(
            expanded = businessTypeExpanded,
            onExpandedChange = { businessTypeExpanded = it }
        ) {
            OutlinedTextField(
                value = "${VendorTypeConfigs.iconForType(businessType)} $businessType",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.business_type)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = businessTypeExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = businessTypeExpanded,
                onDismissRequest = { businessTypeExpanded = false }
            ) {
                businessTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text("${VendorTypeConfigs.iconForType(type)} $type") },
                        onClick = {
                            businessType = type
                            businessTypeExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ─── Subscription Plan Section ───────────────
        EditSectionHeader("Subscription Plan")

        // Consolidated 2-plan catalog. Legacy vendor.plan_name values
        // ('STARTER' / 'BUSINESS') were migrated to PRO server-side, but
        // we still fall back to PRO if a stale row comes through.
        val plans = listOf(
            "PRO" to "Pro (699 EGP)",
            "ENTERPRISE" to "Enterprise (1,299 EGP)",
        )
        var selectedPlan by remember(vendor) {
            val raw = vendor.plan_name
            val normalized = if (raw in listOf("PRO", "ENTERPRISE")) raw!! else "PRO"
            mutableStateOf(normalized)
        }
        var planExpanded by remember { mutableStateOf(false) }
        var showPlanConfirm by remember { mutableStateOf(false) }
        var pendingPlan by remember { mutableStateOf("") }

        ExposedDropdownMenuBox(
            expanded = planExpanded,
            onExpandedChange = { planExpanded = it }
        ) {
            OutlinedTextField(
                value = plans.find { it.first == selectedPlan }?.second ?: selectedPlan,
                onValueChange = {},
                readOnly = true,
                label = { Text("Plan") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = planExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = planExpanded,
                onDismissRequest = { planExpanded = false }
            ) {
                plans.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (key == selectedPlan) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(label, fontWeight = if (key == selectedPlan) FontWeight.Bold else FontWeight.Normal)
                            }
                        },
                        onClick = {
                            planExpanded = false
                            if (key != selectedPlan) {
                                pendingPlan = key
                                showPlanConfirm = true
                            }
                        }
                    )
                }
            }
        }

        Text(
            "Changing plan will reset all feature flags to plan defaults. You can customize them after.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (showPlanConfirm) {
            AlertDialog(
                onDismissRequest = { showPlanConfirm = false },
                title = { Text("Change Plan") },
                text = {
                    Text("Change plan to ${plans.find { it.first == pendingPlan }?.second}?\n\nThis will reset all feature flags to the new plan's defaults. You can then customize individual features.")
                },
                confirmButton = {
                    Button(onClick = {
                        showPlanConfirm = false
                        selectedPlan = pendingPlan
                        onChangePlan(pendingPlan)
                    }) { Text("Confirm") }
                },
                dismissButton = {
                    TextButton(onClick = { showPlanConfirm = false }) { Text("Cancel") }
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        // ─── Channel Config Section ──────────────────
        EditSectionHeader(stringResource(Res.string.channel_config))

        EditSwitchRow(stringResource(Res.string.enable_tables), enableTables) { enableTables = it }
        EditSwitchRow(stringResource(Res.string.enable_kds), enableKds) { enableKds = it }
        EditSwitchRow(stringResource(Res.string.enable_dine_in), enableDineIn) { enableDineIn = it }
        EditSwitchRow(stringResource(Res.string.enable_delivery), enableDelivery) { enableDelivery = it }
        EditSwitchRow(stringResource(Res.string.enable_takeaway), enableTakeaway) { enableTakeaway = it }
        EditSwitchRow(stringResource(Res.string.enable_in_store), enableInStore) { enableInStore = it }
        EditSwitchRow(stringResource(Res.string.enable_pickup_later), enablePickupLater) { enablePickupLater = it }

        Spacer(Modifier.height(8.dp))

        // ─── Tax & Stock Section ─────────────────────
        EditSectionHeader(stringResource(Res.string.tax_and_stock))

        EditSwitchRow(stringResource(Res.string.tax_enabled), taxEnabled) { taxEnabled = it }

        if (taxEnabled) {
            OutlinedTextField(
                value = defaultTaxPercent,
                onValueChange = { defaultTaxPercent = it },
                label = { Text(stringResource(Res.string.tax_percent)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        ExposedDropdownMenuBox(
            expanded = stockModeExpanded,
            onExpandedChange = { stockModeExpanded = it }
        ) {
            OutlinedTextField(
                value = stockMode,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.stock_mode)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stockModeExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = stockModeExpanded,
                onDismissRequest = { stockModeExpanded = false }
            ) {
                stockModes.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode) },
                        onClick = {
                            stockMode = mode
                            stockModeExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ─── Feature Toggles Section ─────────────────
        Text("Feature Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        var enableSplitPayment by remember { mutableStateOf(vendor.enable_split_payment) }
        var enableCashDrawer by remember { mutableStateOf(vendor.enable_cash_drawer) }
        var enableReturns by remember { mutableStateOf(vendor.enable_returns) }
        var enableInstallments by remember { mutableStateOf(vendor.enable_installments) }
        var enableCustomerCredit by remember { mutableStateOf(vendor.enable_customer_credit) }
        var enableScheduledOrders by remember { mutableStateOf(vendor.enable_scheduled_orders) }
        var enableSuppliers by remember { mutableStateOf(vendor.enable_suppliers) }
        var enableDrugInteractions by remember { mutableStateOf(vendor.enable_drug_interactions) }
        var enablePrescriptions by remember { mutableStateOf(vendor.enable_prescriptions) }
        var enableAnalytics by remember { mutableStateOf(vendor.enable_analytics) }
        var enableAnnouncements by remember { mutableStateOf(vendor.enable_announcements) }
        var enableDigitalMenu by remember { mutableStateOf(vendor.enable_digital_menu) }
        var enableRecipe by remember { mutableStateOf(vendor.enable_recipe) }
        var enableStock by remember { mutableStateOf(vendor.enable_stock) }
        var enableAttendance by remember { mutableStateOf(vendor.enable_attendance) }
        var enableOvertime by remember { mutableStateOf(vendor.enable_overtime) }
        var enableSalary by remember { mutableStateOf(vendor.enable_salary) }
        var enableCustomers by remember { mutableStateOf(vendor.enable_customers) }
        var enableExport by remember { mutableStateOf(vendor.enable_export) }
        var enableDigitalReceipt by remember { mutableStateOf(vendor.enable_digital_receipt) }
        var enableWorkerQrcode by remember { mutableStateOf(vendor.enable_worker_qrcode) }
        var enableLoyalty by remember { mutableStateOf(vendor.enable_loyalty) }
        var enableManualDiscount by remember { mutableStateOf(vendor.enable_manual_discount) }
        var enableOffers by remember { mutableStateOf(vendor.enable_offers) }

        // Core Features
        EditSwitchRow("Stock Management", enableStock) { enableStock = it }
        EditSwitchRow("Analytics & Dashboard", enableAnalytics) { enableAnalytics = it }
        EditSwitchRow("Data Export", enableExport) { enableExport = it }
        EditSwitchRow("Customers", enableCustomers) { enableCustomers = it }
        EditSwitchRow("Announcements", enableAnnouncements) { enableAnnouncements = it }

        Spacer(Modifier.height(8.dp))
        Text("Payments", style = MaterialTheme.typography.labelMedium)
        EditSwitchRow("Split Payment", enableSplitPayment) { enableSplitPayment = it }
        EditSwitchRow("Cash Drawer", enableCashDrawer) { enableCashDrawer = it }
        EditSwitchRow("Customer Credit", enableCustomerCredit) { enableCustomerCredit = it }
        EditSwitchRow("Installments", enableInstallments) { enableInstallments = it }
        EditSwitchRow("Manual Discount", enableManualDiscount) { enableManualDiscount = it }
        EditSwitchRow("Digital Receipt", enableDigitalReceipt) { enableDigitalReceipt = it }

        Spacer(Modifier.height(8.dp))
        Text("Staff & HR", style = MaterialTheme.typography.labelMedium)
        EditSwitchRow("Attendance", enableAttendance) { enableAttendance = it }
        EditSwitchRow("Overtime", enableOvertime) { enableOvertime = it }
        EditSwitchRow("Salary", enableSalary) { enableSalary = it }
        EditSwitchRow("Worker QR Code", enableWorkerQrcode) { enableWorkerQrcode = it }

        Spacer(Modifier.height(8.dp))
        Text("Marketing", style = MaterialTheme.typography.labelMedium)
        EditSwitchRow("Loyalty Points", enableLoyalty) { enableLoyalty = it }
        EditSwitchRow("Offers", enableOffers) { enableOffers = it }
        EditSwitchRow("Digital Menu", enableDigitalMenu) { enableDigitalMenu = it }

        Spacer(Modifier.height(8.dp))
        Text("Operations", style = MaterialTheme.typography.labelMedium)
        EditSwitchRow("Returns & Exchange", enableReturns) { enableReturns = it }
        EditSwitchRow("Suppliers", enableSuppliers) { enableSuppliers = it }
        EditSwitchRow("Scheduled Orders", enableScheduledOrders) { enableScheduledOrders = it }
        EditSwitchRow("Recipe", enableRecipe) { enableRecipe = it }

        Spacer(Modifier.height(8.dp))
        Text("Pharmacy", style = MaterialTheme.typography.labelMedium)
        EditSwitchRow("Drug Interactions", enableDrugInteractions) { enableDrugInteractions = it }
        EditSwitchRow("Prescriptions", enablePrescriptions) { enablePrescriptions = it }

        Spacer(Modifier.height(16.dp))

        // ─── Social Links Section ──────────────────
        Spacer(Modifier.height(16.dp))

        // ─── Action Buttons ──────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(48.dp),
                enabled = !isSaving
            ) {
                Text(stringResource(Res.string.cancel_edit))
            }

            Button(
                onClick = {
                    val request = UpdateVendorRequest(
                        name = name.trim(),
                        address = address.trim(),
                        contact_phone = contactPhone.trim(),
                        wallet_phone = walletPhone.ifBlank { null },
                        default_delivery_fee = deliveryFee.toDoubleOrNull() ?: 0.0,
                        store_type = storeType,
                        business_type = businessType,
                        enable_tables = enableTables,
                        enable_kds = enableKds,
                        enable_dine_in = enableDineIn,
                        enable_delivery = enableDelivery,
                        enable_takeaway = enableTakeaway,
                        enable_in_store = enableInStore,
                        enable_pickup_later = enablePickupLater,
                        tax_enabled = taxEnabled,
                        default_tax_percent = defaultTaxPercent.toDoubleOrNull() ?: 0.0,
                        stock_mode = stockMode,
                        enable_split_payment = enableSplitPayment,
                        enable_cash_drawer = enableCashDrawer,
                        enable_returns = enableReturns,
                        enable_installments = enableInstallments,
                        enable_customer_credit = enableCustomerCredit,
                        enable_pre_orders = enableScheduledOrders,
                        enable_scheduled_orders = enableScheduledOrders,
                        enable_suppliers = enableSuppliers,
                        enable_drug_interactions = enableDrugInteractions,
                        enable_prescriptions = enablePrescriptions,
                        enable_analytics = enableAnalytics,
                        enable_announcements = enableAnnouncements,
                        enable_digital_menu = enableDigitalMenu,
                        enable_recipe = enableRecipe,
                        enable_stock = enableStock,
                        enable_attendance = enableAttendance,
                        enable_overtime = enableOvertime,
                        enable_salary = enableSalary,
                        enable_customers = enableCustomers,
                        enable_export = enableExport,
                        enable_digital_receipt = enableDigitalReceipt,
                        enable_worker_qrcode = enableWorkerQrcode,
                        enable_loyalty = enableLoyalty,
                        enable_manual_discount = enableManualDiscount,
                        enable_offers = enableOffers,
                    )
                    onSave(request)
                },
                modifier = Modifier.weight(1f).height(48.dp),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(Res.string.save_changes))
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun EditSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun EditSwitchRow(
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

// ─── View Mode (Read-Only) ─────────────────────────────────────

// ─── Tabbed Layout ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VendorTabbedContent(
    vendorId: String,
    detail: VendorDetailDto,
    viewModel: VendorDetailViewModel
) {
    val VENDOR_TABS = listOf(
        stringResource(Res.string.tab_overview), stringResource(Res.string.tab_revenue),
        stringResource(Res.string.tab_peak_times), stringResource(Res.string.tab_staff),
        stringResource(Res.string.tab_products), stringResource(Res.string.tab_customers),
        stringResource(Res.string.tab_stock), stringResource(Res.string.tab_offers),
        stringResource(Res.string.tab_alerts), stringResource(Res.string.tab_orders),
        stringResource(Res.string.tab_workers),
        // New management tabs — let the admin operate this vendor's catalog
        // and recipes directly without leaving the admin app.
        stringResource(Res.string.menu_tab),
        stringResource(Res.string.recipes_tab),
    )
    val selectedTab by viewModel.selectedTab.collectAsState()
    val tabLoading by viewModel.tabLoading.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Show date range picker for analytics tabs (1-8)
    val showDatePicker = selectedTab in 1..8

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 8.dp,
        ) {
            VENDOR_TABS.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { viewModel.selectTab(vendorId, index) },
                    text = { Text(title) }
                )
            }
        }

        if (showDatePicker) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DateRangeSelector(
                    selectedPeriod = selectedPeriod,
                    onPeriodChanged = { viewModel.changeDateRange(vendorId, it) }
                )
            }
        }

        if (tabLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        when (selectedTab) {
            0 -> VendorDetailContent(detail = detail, vendorId = vendorId, viewModel = viewModel)
            1 -> RevenueOrdersTab(viewModel)
            2 -> PeakTimesTab(viewModel)
            3 -> StaffPerformanceTab(viewModel)
            4 -> ProductsTab(viewModel)
            5 -> CustomersTab(viewModel)
            6 -> StockTab(viewModel)
            7 -> OffersDiscountsTab(viewModel)
            8 -> AlertsTab(viewModel)
            9 -> OrdersListTab(vendorId, viewModel)
            10 -> WorkersTab(viewModel)
            11 -> AdminMenuTab(vendorId, viewModel)
            12 -> AdminRecipesTab(vendorId, viewModel)
        }
    }
}

@Composable
private fun VendorDetailContent(
    detail: VendorDetailDto,
    vendorId: String = "",
    viewModel: VendorDetailViewModel? = null
) {
    val vendor = detail.vendor
    val stats = detail.stats
    val planUsage = detail.plan_usage

    // User management dialogs
    var showAddUserDialog by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf<VendorUserDto?>(null) }
    var showDeactivateDialog by remember { mutableStateOf<VendorUserDto?>(null) }
    var showSetPinDialog by remember { mutableStateOf<VendorUserDto?>(null) }
    var showDeletePermanentDialog by remember { mutableStateOf<VendorUserDto?>(null) }

    // The active impersonation session (if any) is held by the ViewModel.
    // We watch it so the dialog below can show whatever the most recent
    // /impersonate call returned.
    val impersonationSession by (viewModel?.impersonationSession
        ?: kotlinx.coroutines.flow.MutableStateFlow(null))
        .collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // ─── Header Card ─────────────────────────
        item {
            VendorHeaderCard(vendor)
        }

        // ─── "Open as Manager" CTA ───────────────
        // Single button that unlocks every manager-side action (menu,
        // recipes, stock, tables, offers, customers, orders, etc.) for the
        // selected vendor. Clicking mints a manager JWT and shows it in a
        // dialog with copy buttons.
        if (viewModel != null) {
            item {
                Button(
                    onClick = { viewModel.impersonateVendor(vendorId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.open_as_manager))
                }
            }
        }

        // ─── Info Card ───────────────────────────
        item {
            VendorInfoCard(vendor)
        }

        // ─── Subscription Card ──────────────────
        if (vendor.subscription_status != null) {
            item {
                VendorSubscriptionCard(vendor)
            }
        }

        // ─── Loyalty Card ───────────────────────
        item {
            VendorLoyaltyCard(vendor)
        }

        // ─── Discount Card ──────────────────────
        item {
            VendorDiscountCard(vendor)
        }

        // ─── Device Settings Card ───────────────
        item {
            VendorDeviceCard(vendor)
        }

        // ─── Users Section ───────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(stringResource(Res.string.users_count_format, detail.users.size))
                if (viewModel != null) {
                    TextButton(onClick = { showAddUserDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.add_user))
                    }
                }
            }
        }
        if (detail.users.isEmpty()) {
            item {
                Text(
                    stringResource(Res.string.no_users_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        } else {
            items(detail.users, key = { it.id }) { user ->
                UserRowWithActions(
                    user = user,
                    onResetPassword = { showResetPasswordDialog = user },
                    onSetPin = { showSetPinDialog = user },
                    onDeactivate = { showDeactivateDialog = user },
                    onDeletePermanent = { showDeletePermanentDialog = user },
                    showActions = viewModel != null
                )
            }
        }

        // ─── Order Stats ─────────────────────────
        item {
            SectionTitle(stringResource(Res.string.order_statistics))
        }
        item {
            OrderStatsCard(stats.orders)
        }

        // ─── Revenue Stats ───────────────────────
        item {
            SectionTitle(stringResource(Res.string.revenue))
        }
        item {
            RevenueCard(stats.revenue)
        }

        // ─── Tax ─────────────────────────────────
        item {
            TaxCard(stats.tax)
        }

        // ─── Plan Usage ──────────────────────────
        item {
            SectionTitle(stringResource(Res.string.plan_usage))
        }
        item {
            PlanUsageCard(planUsage)
        }

        // Bottom spacer
        item {
            Spacer(Modifier.height(16.dp))
        }
    }

    // ─── Add User Dialog ─────────────────────────────────
    if (showAddUserDialog && viewModel != null) {
        AddUserDialog(
            onDismiss = { showAddUserDialog = false },
            onConfirm = { name, phone, password, role, email ->
                viewModel.createUser(vendorId, name, phone, password, role, email)
                showAddUserDialog = false
            }
        )
    }

    // ─── Reset Password Dialog ──────────────────────────
    showResetPasswordDialog?.let { user ->
        if (viewModel != null) {
            ResetPasswordDialog(
                userName = user.name,
                onDismiss = { showResetPasswordDialog = null },
                onConfirm = { newPassword ->
                    viewModel.resetUserPassword(vendorId, user.id, newPassword)
                    showResetPasswordDialog = null
                }
            )
        }
    }

    // ─── Deactivate User Dialog ─────────────────────────
    showDeactivateDialog?.let { user ->
        if (viewModel != null) {
            AlertDialog(
                onDismissRequest = { showDeactivateDialog = null },
                title = { Text(stringResource(Res.string.deactivate_user)) },
                text = { Text(stringResource(Res.string.deactivate_user_confirm, user.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deactivateUser(vendorId, user.id)
                            showDeactivateDialog = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(Res.string.deactivate)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeactivateDialog = null }) { Text(stringResource(Res.string.cancel)) }
                }
            )
        }
    }

    // ─── Set Override PIN Dialog ────────────────────────
    showSetPinDialog?.let { user ->
        if (viewModel != null) {
            SetPinDialog(
                userName = user.name,
                onDismiss = { showSetPinDialog = null },
                onConfirm = { newPinOrNull ->
                    viewModel.setUserPin(vendorId, user.id, newPinOrNull)
                    showSetPinDialog = null
                },
            )
        }
    }

    // ─── Permanent Delete Dialog ────────────────────────
    showDeletePermanentDialog?.let { user ->
        if (viewModel != null) {
            AlertDialog(
                onDismissRequest = { showDeletePermanentDialog = null },
                title = { Text(stringResource(Res.string.delete_user_permanent_title)) },
                text = { Text(stringResource(Res.string.delete_user_permanent_warning, user.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.hardDeleteUser(vendorId, user.id)
                            showDeletePermanentDialog = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(Res.string.delete_user_permanent_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeletePermanentDialog = null }) { Text(stringResource(Res.string.cancel)) }
                }
            )
        }
    }

    // ─── Manager Impersonation Dialog ─────────────────────
    // Shown after the "Open as Manager" CTA succeeds. The dialog presents
    // the freshly-minted access + refresh tokens with copy buttons so the
    // operator can paste them into the manager app for full vendor access.
    impersonationSession?.let { session ->
        if (viewModel != null) {
            ImpersonationResultDialog(
                session = session,
                vendorName = vendor.name,
                onDismiss = { viewModel.dismissImpersonationSession() },
            )
        }
    }
}

/**
 * Result dialog for "Open as Manager". Displays both tokens with copy
 * buttons. The user pastes the access token into the manager app's "manual
 * session" entry (or whatever flow we add later) to operate as the vendor's
 * manager — that unlocks every manager-side endpoint: menu, recipes,
 * stock, tables, offers, customers, orders, suppliers, attendance, etc.
 */
@Composable
private fun ImpersonationResultDialog(
    session: AdminApiClient.ImpersonationSession,
    vendorName: String,
    onDismiss: () -> Unit,
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copiedFlash by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.impersonate_title, vendorName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(Res.string.impersonate_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${session.name} · ${session.role} · ${session.phone}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                TokenRow(
                    label = stringResource(Res.string.impersonate_access_token),
                    token = session.accessToken,
                    onCopy = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(session.accessToken))
                        copiedFlash = true
                    },
                )
                TokenRow(
                    label = stringResource(Res.string.impersonate_refresh_token),
                    token = session.refreshToken,
                    onCopy = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(session.refreshToken))
                        copiedFlash = true
                    },
                )
                if (copiedFlash) {
                    Text(
                        stringResource(Res.string.copied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
        },
    )
}

@Composable
private fun TokenRow(label: String, token: String, onCopy: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Show the token with a monospace-ish look so it's easy to
            // spot truncation visually. We deliberately don't render it in
            // a TextField — copy-by-button is faster than copy-by-select.
            Text(
                text = if (token.length > 60) token.take(56) + "…" else token,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                maxLines = 2,
            )
            OutlinedButton(
                onClick = onCopy,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.copy_token), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Set / clear the override PIN for a vendor user. Two terminal actions:
 *
 *  - "Save" with a 4-6 digit value     → set the PIN
 *  - "Clear" button                    → clear the PIN (sends `clear=true`)
 *
 * Validation matches the backend's PinService — purely numeric, 4-6 chars.
 */
@Composable
private fun SetPinDialog(
    userName: String,
    onDismiss: () -> Unit,
    onConfirm: (newPinOrNull: String?) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val trimmed = pin.filter { it.isDigit() }
    val isValid = trimmed.length in 4..6

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.set_pin_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.set_pin_description, userName))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text(stringResource(Res.string.new_pin)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (pin.isNotEmpty() && !isValid) {
                    Text(
                        stringResource(Res.string.pin_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = isValid,
            ) { Text(stringResource(Res.string.set_pin)) }
        },
        dismissButton = {
            Row {
                // "Clear" is a destructive-style secondary action — passes
                // null to the caller so the ViewModel sends `clear=true`.
                TextButton(
                    onClick = { onConfirm(null) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.clear_pin)) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            }
        },
    )
}

@Composable
private fun AddUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, password: String, role: String, email: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("CASHIER") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_new_user)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text(stringResource(Res.string.vendor_phone)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text(stringResource(Res.string.email)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.password)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                // Role selection
                Text(stringResource(Res.string.role), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MANAGER", "CASHIER", "DELIVERY", "KITCHEN").forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = { role = r },
                            label = { Text(r) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank() && password.length >= 6) {
                        onConfirm(name.trim(), phone.trim(), password, role, email.ifBlank { null })
                    }
                },
                enabled = name.isNotBlank() && phone.isNotBlank() && password.length >= 6
            ) { Text(stringResource(Res.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}

@Composable
private fun ResetPasswordDialog(
    userName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reset_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.reset_password_confirm, userName))
                OutlinedTextField(
                    value = newPassword, onValueChange = { newPassword = it },
                    label = { Text(stringResource(Res.string.new_password)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (newPassword.isNotBlank() && newPassword.length < 6) {
                    Text(
                        stringResource(Res.string.password_min_length),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newPassword) },
                enabled = newPassword.length >= 6
            ) { Text(stringResource(Res.string.reset)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}

@Composable
private fun UserRowWithActions(
    user: VendorUserDto,
    onResetPassword: () -> Unit,
    onSetPin: () -> Unit,
    onDeactivate: () -> Unit,
    onDeletePermanent: () -> Unit,
    showActions: Boolean = false,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = user.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    user.email?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Role badge
                    val roleColor = when (user.role.uppercase()) {
                        "MANAGER" -> MaterialTheme.colorScheme.primary
                        "CASHIER" -> MaterialTheme.colorScheme.secondary
                        "DELIVERY" -> MaterialTheme.colorScheme.tertiary
                        "KITCHEN" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Surface(
                        color = roleColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = user.role,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = roleColor
                        )
                    }

                    // Active status
                    val activeColor = if (user.active) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    Surface(
                        color = activeColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (user.active) stringResource(Res.string.active) else stringResource(Res.string.inactive),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = activeColor
                        )
                    }
                }
            }

            if (showActions) {
                Spacer(Modifier.height(8.dp))
                // Actions are wrapped in FlowRow so a narrow Vendor Detail
                // pane stacks the 4 buttons across multiple lines instead of
                // truncating the labels.
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (user.active) {
                        OutlinedButton(
                            onClick = onResetPassword,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.reset_password), style = MaterialTheme.typography.labelSmall)
                        }
                        // PIN is meaningful only for MANAGER users (the
                        // override-PIN system is manager-only) — gate the
                        // button so we don't expose it for cashiers and
                        // delivery drivers.
                        if (user.role.equals("MANAGER", ignoreCase = true)) {
                            OutlinedButton(
                                onClick = onSetPin,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(Res.string.set_pin), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        OutlinedButton(
                            onClick = onDeactivate,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.PersonOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.deactivate), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    // Permanent delete is the most destructive — coloured
                    // error and always at the end. Shown for both active and
                    // inactive users so admins can clean up stale rows
                    // without having to reactivate them first.
                    OutlinedButton(
                        onClick = onDeletePermanent,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.delete_user), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun VendorHeaderCard(vendor: VendorDetailInfo) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = vendor.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Status badge
                    val statusColor = if (vendor.is_suspended)
                        MaterialTheme.colorScheme.error
                    else
                        Color(0xFF4CAF50)
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (vendor.is_suspended) stringResource(Res.string.suspended) else stringResource(Res.string.active),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor
                        )
                    }

                    // Plan badge
                    if (vendor.plan_display_name != null) {
                        val planColor = when (vendor.plan_name?.uppercase()) {
                            "ENTERPRISE" -> MaterialTheme.colorScheme.tertiary
                            "PRO", "STARTER", "BUSINESS" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                        Surface(
                            color = planColor.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = vendor.plan_display_name,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = planColor
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "${VendorTypeConfigs.iconForType(vendor.business_type)} ${vendor.business_type}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                vendor.store_type?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (vendor.is_suspended && vendor.suspension_reason != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = stringResource(Res.string.suspension_reason, vendor.suspension_reason),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun VendorInfoCard(vendor: VendorDetailInfo) {
    val enabledText = stringResource(Res.string.enabled)
    val disabledText = stringResource(Res.string.disabled)
    val yesText = stringResource(Res.string.yes)
    val noText = stringResource(Res.string.no)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.contact_settings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            InfoRow(stringResource(Res.string.address), vendor.address)
            InfoRow(stringResource(Res.string.phone), vendor.contact_phone)
            vendor.wallet_phone?.let { InfoRow(stringResource(Res.string.wallet), it) }
            InfoRow(stringResource(Res.string.delivery_fee), "${formatDecimal(vendor.default_delivery_fee, 2)} EGP")

            // Digital Menu URL
            vendor.digital_menu_url?.let {
                Spacer(Modifier.height(4.dp))
                InfoRow(stringResource(Res.string.digital_menu), it)
            }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.enabled_channels),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (vendor.enable_dine_in) ChannelChip(stringResource(Res.string.channel_dine_in))
                if (vendor.enable_takeaway) ChannelChip(stringResource(Res.string.channel_takeaway))
                if (vendor.enable_delivery) ChannelChip(stringResource(Res.string.channel_delivery))
                if (vendor.enable_in_store) ChannelChip(stringResource(Res.string.channel_in_store))
                if (vendor.enable_pickup_later) ChannelChip(stringResource(Res.string.channel_pickup_later))
            }

            Spacer(Modifier.height(12.dp))
            InfoRow(stringResource(Res.string.tables), if (vendor.enable_tables) enabledText else disabledText)
            InfoRow(stringResource(Res.string.kds_label), if (vendor.enable_kds) enabledText else disabledText)
            InfoRow(stringResource(Res.string.tax), if (vendor.tax_enabled) "$enabledText (${vendor.default_tax_percent}%)" else disabledText)
            InfoRow(stringResource(Res.string.stock_mode), vendor.stock_mode)
        }
    }
}

@Composable
private fun VendorLoyaltyCard(vendor: VendorDetailInfo) {
    val enabledText = stringResource(Res.string.enabled)
    val disabledText = stringResource(Res.string.disabled)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.loyalty_settings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            InfoRow(stringResource(Res.string.loyalty_enabled), if (vendor.loyalty_enabled) enabledText else disabledText)
            if (vendor.loyalty_enabled) {
                InfoRow(stringResource(Res.string.earn_rate), "${vendor.points_earn_rate} pts/EGP")
                InfoRow(stringResource(Res.string.redeem_rate), "${vendor.points_redeem_rate} EGP/pt")
                InfoRow(stringResource(Res.string.min_points), vendor.min_points_redeem.toString())
            }
        }
    }
}

@Composable
private fun VendorDiscountCard(vendor: VendorDetailInfo) {
    val yesText = stringResource(Res.string.yes)
    val noText = stringResource(Res.string.no)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.discount_settings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            InfoRow(stringResource(Res.string.max_discount), "${vendor.max_manual_discount_percent}%")
            InfoRow(stringResource(Res.string.pin_required), if (vendor.manual_discount_requires_pin) yesText else noText)
        }
    }
}

@Composable
private fun VendorDeviceCard(vendor: VendorDetailInfo) {
    val enabledText = stringResource(Res.string.enabled)
    val disabledText = stringResource(Res.string.disabled)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.device_settings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            InfoRow(stringResource(Res.string.offline_mode), if (vendor.offline_mode_enabled) enabledText else disabledText)
            InfoRow(stringResource(Res.string.biometric_auth), if (vendor.biometric_required) enabledText else disabledText)
        }
    }
}

@Composable
private fun VendorSubscriptionCard(vendor: VendorDetailInfo) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.subscription_info),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            vendor.subscription_status?.let {
                val statusColor = when (it.uppercase()) {
                    "ACTIVE" -> Color(0xFF4CAF50)
                    "EXPIRED" -> MaterialTheme.colorScheme.error
                    "TRIAL" -> Color(0xFFFF9800)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }
            }

            vendor.subscription_started_at?.let {
                val dateStr = formatEpochMillis(it)
                InfoRow(stringResource(Res.string.subscription_started), dateStr)
            }
            vendor.subscription_expires_at?.let {
                val dateStr = formatEpochMillis(it)
                val isExpired = it < Clock.System.now().toEpochMilliseconds()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.subscription_expires),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isExpired) MaterialTheme.colorScheme.error else Color.Unspecified
                    )
                }
            }
        }
    }
}

private fun formatEpochMillis(epochMs: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMs)
    val tz = TimeZone.currentSystemDefault()
    val dt = instant.toLocalDateTime(tz)
    return "${dt.year}/${dt.monthNumber.toString().padStart(2, '0')}/${dt.dayOfMonth.toString().padStart(2, '0')}"
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ChannelChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun OrderStatsCard(orders: VendorOrderStatsDto) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(stringResource(Res.string.total), orders.total.toString())
                StatItem(stringResource(Res.string.today), orders.today.toString())
                StatItem(stringResource(Res.string.this_month), orders.this_month.toString())
            }

            if (orders.by_channel.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.by_channel),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    orders.by_channel.forEach { (channel, count) ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "$channel: $count",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            if (orders.by_status.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.by_status),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    orders.by_status.forEach { (status, count) ->
                        val color = when (status.uppercase()) {
                            "COMPLETED" -> Color(0xFF4CAF50)
                            "CANCELED" -> MaterialTheme.colorScheme.error
                            "REFUNDED" -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Surface(
                            color = color.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "$status: $count",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = color
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RevenueCard(revenue: VendorRevenueStatsDto) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(stringResource(Res.string.total), "${formatDecimal(revenue.total, 0)} EGP")
                StatItem(stringResource(Res.string.today), "${formatDecimal(revenue.today, 0)} EGP")
                StatItem(stringResource(Res.string.this_month), "${formatDecimal(revenue.this_month, 0)} EGP")
            }

            if (revenue.by_payment_method.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.by_payment_method),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                revenue.by_payment_method.forEach { (method, stats) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val icon = when (method.uppercase()) {
                                "CASH" -> Icons.Default.Payments
                                "CARD" -> Icons.Default.CreditCard
                                "WALLET" -> Icons.Default.AccountBalanceWallet
                                else -> Icons.Default.Payment
                            }
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = method,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "${stats.count} orders | ${formatDecimal(stats.amount, 0)} EGP",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (revenue.by_channel.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.revenue_by_channel),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                revenue.by_channel.forEach { (channel, amount) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = channel,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${formatDecimal(amount, 0)} EGP",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxCard(tax: VendorTaxStatsDto) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    stringResource(Res.string.tax_collected),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(Res.string.tax_collected_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${formatDecimal(tax.total_collected, 2)} EGP",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PlanUsageCard(planUsage: VendorPlanUsageDto) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = planUsage.plan.display_name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            UsageProgressRow(stringResource(Res.string.managers), planUsage.usage.managers, planUsage.plan.max_managers)
            UsageProgressRow(stringResource(Res.string.cashiers), planUsage.usage.cashiers, planUsage.plan.max_cashiers)
            UsageProgressRow(stringResource(Res.string.channel_delivery), planUsage.usage.delivery, planUsage.plan.max_delivery)
            UsageProgressRow(stringResource(Res.string.menu_items), planUsage.usage.menu_items, planUsage.plan.max_menu_items)
            UsageProgressRow(stringResource(Res.string.monthly_orders), planUsage.usage.monthly_orders, planUsage.plan.max_orders_per_month)
        }
    }
}

@Composable
private fun UsageProgressRow(label: String, used: Int, max: Int) {
    val fraction = if (max > 0) (used.toFloat() / max.toFloat()).coerceAtMost(1f) else 0f
    val overLimit = used > max && max > 0
    val progressColor = when {
        overLimit -> MaterialTheme.colorScheme.error
        fraction > 0.8f -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.primary
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "$used / $max",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = progressColor
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = progressColor,
            trackColor = progressColor.copy(alpha = 0.12f),
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Admin: Menu tab (Categories + Items CRUD)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun AdminMenuTab(vendorId: String, viewModel: VendorDetailViewModel) {
    val menu by viewModel.menu.collectAsState()
    val loading by viewModel.menuLoading.collectAsState()

    // Initial fetch when this tab first becomes visible
    LaunchedEffect(vendorId) { viewModel.loadMenu(vendorId) }

    var showAddCategory by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<AdminCategoryDto?>(null) }
    var addingItemToCategory by remember { mutableStateOf<AdminCategoryDto?>(null) }
    var editingItem by remember { mutableStateOf<Pair<AdminCategoryDto, AdminItemDto>?>(null) }
    var deletingCategory by remember { mutableStateOf<AdminCategoryDto?>(null) }
    var deletingItem by remember { mutableStateOf<AdminItemDto?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${menu.size} ${stringResource(Res.string.menu_tab).lowercase()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = { showAddCategory = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.add_category))
            }
        }

        if (loading && menu.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (menu.isEmpty()) {
            Text(
                stringResource(Res.string.menu_no_categories),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(menu, key = { it.id }) { category ->
                    AdminCategoryCard(
                        category = category,
                        onEditCategory = { editingCategory = category },
                        onDeleteCategory = { deletingCategory = category },
                        onAddItem = { addingItemToCategory = category },
                        onEditItem = { item -> editingItem = category to item },
                        onDeleteItem = { item -> deletingItem = item },
                    )
                }
            }
        }
    }

    // ─── Dialogs ────────────────────────────────────────────
    if (showAddCategory) {
        CategoryFormDialog(
            initial = null,
            onDismiss = { showAddCategory = false },
            onConfirm = { name, order ->
                viewModel.createCategory(vendorId, name, order)
                showAddCategory = false
            },
        )
    }
    editingCategory?.let { cat ->
        CategoryFormDialog(
            initial = cat,
            onDismiss = { editingCategory = null },
            onConfirm = { name, order ->
                viewModel.updateCategory(vendorId, cat.id, name, order)
                editingCategory = null
            },
        )
    }
    deletingCategory?.let { cat ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text(stringResource(Res.string.delete)) },
            text = { Text("${stringResource(Res.string.delete)}: ${cat.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCategory(vendorId, cat.id)
                        deletingCategory = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    addingItemToCategory?.let { cat ->
        ItemFormDialog(
            categories = menu,
            preselectedCategoryId = cat.id,
            initial = null,
            onDismiss = { addingItemToCategory = null },
            onConfirm = { req ->
                viewModel.createItem(vendorId, req)
                addingItemToCategory = null
            },
        )
    }
    editingItem?.let { (cat, item) ->
        ItemFormDialog(
            categories = menu,
            preselectedCategoryId = cat.id,
            initial = item,
            onDismiss = { editingItem = null },
            onConfirm = { req ->
                viewModel.updateItem(
                    vendorId,
                    item.id,
                    UpdateAdminItemRequest(
                        category_id = req.category_id,
                        name = req.name,
                        description = req.description,
                        price = req.price,
                        cost_price = req.cost_price,
                        sku = req.sku,
                        barcode = req.barcode,
                        image_url = req.image_url,
                        available = req.available,
                        stock_behavior = req.stock_behavior,
                    ),
                )
                editingItem = null
            },
        )
    }
    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text(stringResource(Res.string.delete)) },
            text = { Text("${stringResource(Res.string.delete)}: ${item.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(vendorId, item.id)
                        deletingItem = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

@Composable
private fun AdminCategoryCard(
    category: AdminCategoryDto,
    onEditCategory: () -> Unit,
    onDeleteCategory: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (AdminItemDto) -> Unit,
    onDeleteItem: (AdminItemDto) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                        )
                    }
                    Column {
                        Text(category.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${category.item_count} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row {
                    IconButton(onClick = onAddItem) {
                        Icon(Icons.Default.Add, contentDescription = "Add item", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onEditCategory) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit category", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDeleteCategory) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = "Delete category",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (expanded && category.items.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                category.items.forEach { item ->
                    AdminItemRow(item = item, onEdit = { onEditItem(item) }, onDelete = { onDeleteItem(item) })
                    if (item != category.items.last()) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun AdminItemRow(
    item: AdminItemDto,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (item.available != "true") {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            "Unavailable",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Text(
                "${item.price} EGP" + if (item.sku.isNotBlank()) "  ·  ${item.sku}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CategoryFormDialog(
    initial: AdminCategoryDto?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, displayOrder: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var orderText by remember { mutableStateOf(initial?.display_order ?: "0") }
    val title = if (initial == null) stringResource(Res.string.add_category) else stringResource(Res.string.edit_category)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.category_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = orderText,
                    onValueChange = { orderText = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(Res.string.display_order)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), orderText.toIntOrNull() ?: 0) },
                enabled = name.isNotBlank(),
            ) { Text(if (initial == null) stringResource(Res.string.create) else stringResource(Res.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun ItemFormDialog(
    categories: List<AdminCategoryDto>,
    preselectedCategoryId: String,
    initial: AdminItemDto?,
    onDismiss: () -> Unit,
    onConfirm: (CreateAdminItemRequest) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(initial?.price ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var costPrice by remember { mutableStateOf(initial?.cost_price ?: "") }
    var sku by remember { mutableStateOf(initial?.sku ?: "") }
    var barcode by remember { mutableStateOf(initial?.barcode ?: "") }
    var imageUrl by remember { mutableStateOf(initial?.image_url ?: "") }
    var available by remember { mutableStateOf((initial?.available ?: "true") == "true") }
    var stockBehavior by remember { mutableStateOf(initial?.stock_behavior ?: "NONE") }
    var categoryId by remember { mutableStateOf(initial?.category_id ?: preselectedCategoryId) }

    val priceValid = price.toDoubleOrNull() != null && price.toDouble() >= 0
    val canSave = name.isNotBlank() && priceValid && categoryId.isNotBlank()
    val title = if (initial == null) stringResource(Res.string.add_item) else stringResource(Res.string.edit_item)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Category picker — segmented row works well with the few
                // categories most vendors have (< 10).
                Text(stringResource(Res.string.category_name), style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categories.forEach { c ->
                        FilterChip(
                            selected = categoryId == c.id,
                            onClick = { categoryId = c.id },
                            label = { Text(c.name, maxLines = 1) },
                        )
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.item_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = price, onValueChange = { price = it },
                    label = { Text(stringResource(Res.string.item_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.item_description)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = costPrice, onValueChange = { costPrice = it },
                    label = { Text(stringResource(Res.string.item_cost_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sku, onValueChange = { sku = it },
                    label = { Text(stringResource(Res.string.item_sku)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = barcode, onValueChange = { barcode = it },
                    label = { Text(stringResource(Res.string.item_barcode)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = imageUrl, onValueChange = { imageUrl = it },
                    label = { Text("Image URL (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = available, onCheckedChange = { available = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.item_available))
                }
                Text(stringResource(Res.string.stock_behavior), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "NONE" to stringResource(Res.string.stock_none),
                        "DIRECT" to stringResource(Res.string.stock_direct),
                        "RECIPE" to stringResource(Res.string.stock_recipe),
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = stockBehavior == code,
                            onClick = { stockBehavior = code },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onConfirm(
                        CreateAdminItemRequest(
                            category_id = categoryId,
                            name = name.trim(),
                            description = description.ifBlank { null },
                            price = price.toDoubleOrNull() ?: 0.0,
                            cost_price = costPrice.toDoubleOrNull(),
                            sku = sku.ifBlank { null },
                            barcode = barcode.ifBlank { null },
                            image_url = imageUrl.ifBlank { null },
                            available = available,
                            stock_behavior = stockBehavior,
                        ),
                    )
                },
            ) { Text(if (initial == null) stringResource(Res.string.create) else stringResource(Res.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────
// Admin: Recipes tab (list + delete)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun AdminRecipesTab(vendorId: String, viewModel: VendorDetailViewModel) {
    val recipes by viewModel.recipes.collectAsState()
    val loading by viewModel.recipesLoading.collectAsState()

    LaunchedEffect(vendorId) { viewModel.loadRecipes(vendorId) }

    var deletingRecipe by remember { mutableStateOf<AdminRecipeDto?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${recipes.size} ${stringResource(Res.string.recipes_tab).lowercase()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (recipes.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showDeleteAll = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.delete_all_recipes))
                }
            }
        }

        if (loading && recipes.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (recipes.isEmpty()) {
            Text(
                stringResource(Res.string.menu_no_recipes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(recipes, key = { it.id }) { r ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(Res.string.recipe_ingredient_count, r.ingredient_count),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { deletingRecipe = r }) {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deletingRecipe?.let { r ->
        AlertDialog(
            onDismissRequest = { deletingRecipe = null },
            title = { Text(stringResource(Res.string.delete)) },
            text = { Text("${stringResource(Res.string.delete)}: ${r.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRecipe(vendorId, r.id)
                        deletingRecipe = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingRecipe = null }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text(stringResource(Res.string.delete_all_recipes)) },
            text = { Text(stringResource(Res.string.delete_all_recipes_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllRecipes(vendorId)
                        showDeleteAll = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.delete_all_recipes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}
