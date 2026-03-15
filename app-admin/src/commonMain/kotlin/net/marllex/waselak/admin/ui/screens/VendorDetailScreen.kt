package net.marllex.waselak.admin.ui.screens

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
                        stock_mode = stockMode
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
                    onDeactivate = { showDeactivateDialog = user },
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
    onDeactivate: () -> Unit,
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

            if (showActions && user.active) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onResetPassword,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.reset_password), style = MaterialTheme.typography.labelSmall)
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
                            "BUSINESS" -> MaterialTheme.colorScheme.primary
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
