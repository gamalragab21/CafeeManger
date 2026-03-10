package net.marllex.waselak.admin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import net.marllex.waselak.admin.network.*
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
    val message by viewModel.message.collectAsState()

    var isEditMode by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vendorId) {
        viewModel.loadVendorDetail(vendorId)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
            if (it.contains("successfully")) {
                isEditMode = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(detail?.vendor?.name ?: "Vendor Details") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditMode) {
                            isEditMode = false
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        Text(error ?: "Error loading vendor details")
                        Button(onClick = { viewModel.retry(vendorId) }) {
                            Text("Retry")
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
                    VendorDetailContent(detail = detail!!)
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

@Composable
private fun VendorDetailContent(detail: VendorDetailDto) {
    val vendor = detail.vendor
    val stats = detail.stats
    val planUsage = detail.plan_usage

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

        // ─── Users Section ───────────────────────
        item {
            SectionTitle("Users (${detail.users.size})")
        }
        if (detail.users.isEmpty()) {
            item {
                Text(
                    "No users found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        } else {
            items(detail.users, key = { it.id }) { user ->
                UserRow(user)
            }
        }

        // ─── Order Stats ─────────────────────────
        item {
            SectionTitle("Order Statistics")
        }
        item {
            OrderStatsCard(stats.orders)
        }

        // ─── Revenue Stats ───────────────────────
        item {
            SectionTitle("Revenue")
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
            SectionTitle("Plan Usage")
        }
        item {
            PlanUsageCard(planUsage)
        }

        // Bottom spacer
        item {
            Spacer(Modifier.height(16.dp))
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
                            text = if (vendor.is_suspended) "Suspended" else "Active",
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
                        text = "Reason: ${vendor.suspension_reason}",
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Contact & Settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            InfoRow("Address", vendor.address)
            InfoRow("Phone", vendor.contact_phone)
            vendor.wallet_phone?.let { InfoRow("Wallet", it) }
            InfoRow("Delivery Fee", "%.2f EGP".format(vendor.default_delivery_fee))

            Spacer(Modifier.height(12.dp))
            Text(
                "Enabled Channels",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (vendor.enable_dine_in) ChannelChip("Dine-In")
                if (vendor.enable_takeaway) ChannelChip("Takeaway")
                if (vendor.enable_delivery) ChannelChip("Delivery")
                if (vendor.enable_in_store) ChannelChip("In-Store")
                if (vendor.enable_pickup_later) ChannelChip("Pickup Later")
            }

            Spacer(Modifier.height(12.dp))
            InfoRow("Tables", if (vendor.enable_tables) "Enabled" else "Disabled")
            InfoRow("Tax", if (vendor.tax_enabled) "Enabled (${vendor.default_tax_percent}%)" else "Disabled")
            InfoRow("Stock Mode", vendor.stock_mode)
        }
    }
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
private fun UserRow(user: VendorUserDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                val activeColor = if (user.active)
                    Color(0xFF4CAF50)
                else
                    MaterialTheme.colorScheme.error
                Surface(
                    color = activeColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (user.active) "Active" else "Inactive",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = activeColor
                    )
                }
            }
        }
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
                StatItem("Total", orders.total.toString())
                StatItem("Today", orders.today.toString())
                StatItem("This Month", orders.this_month.toString())
            }

            if (orders.by_channel.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "By Channel",
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
                    "By Status",
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
                StatItem("Total", "%.0f EGP".format(revenue.total))
                StatItem("Today", "%.0f EGP".format(revenue.today))
                StatItem("This Month", "%.0f EGP".format(revenue.this_month))
            }

            if (revenue.by_payment_method.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "By Payment Method",
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
                            text = "${stats.count} orders | %.0f EGP".format(stats.amount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (revenue.by_channel.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Revenue By Channel",
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
                            text = "%.0f EGP".format(amount),
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
                    "Tax Collected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Total tax from all completed orders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "%.2f EGP".format(tax.total_collected),
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

            UsageProgressRow("Managers", planUsage.usage.managers, planUsage.plan.max_managers)
            UsageProgressRow("Cashiers", planUsage.usage.cashiers, planUsage.plan.max_cashiers)
            UsageProgressRow("Delivery", planUsage.usage.delivery, planUsage.plan.max_delivery)
            UsageProgressRow("Menu Items", planUsage.usage.menu_items, planUsage.plan.max_menu_items)
            UsageProgressRow("Monthly Orders", planUsage.usage.monthly_orders, planUsage.plan.max_orders_per_month)
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
