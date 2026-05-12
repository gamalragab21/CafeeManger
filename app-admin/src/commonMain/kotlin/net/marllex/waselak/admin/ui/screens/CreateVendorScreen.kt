package net.marllex.waselak.admin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import net.marllex.waselak.admin.network.CreateVendorRequest
import net.marllex.waselak.admin.util.LocalWindowSizeClass
import net.marllex.waselak.admin.util.UiMessage
import net.marllex.waselak.admin.util.WindowWidthSizeClass
import net.marllex.waselak.admin.util.resolve
import net.marllex.waselak.admin.viewmodel.VendorsViewModel
import net.marllex.waselak.core.model.DomainFeatures
import net.marllex.waselak.core.model.VendorTypeConfigs
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateVendorScreen(
    onBack: () -> Unit,
    viewModel: VendorsViewModel = koinViewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val resolvedMessage = message?.resolve()

    val snackbarHostState = remember { SnackbarHostState() }

    // Vendor Info
    var vendorName by remember { mutableStateOf("") }
    var vendorAddress by remember { mutableStateOf("") }
    var vendorPhone by remember { mutableStateOf("") }
    var walletPhone by remember { mutableStateOf("") }

    // Business Type (drives auto-config of channels, tax, stock)
    val allBusinessTypes = VendorTypeConfigs.allTypes
    var businessType by remember { mutableStateOf("RESTAURANT") }
    var businessTypeExpanded by remember { mutableStateOf(false) }

    // Manager Info
    var managerName by remember { mutableStateOf("") }
    var managerPhone by remember { mutableStateOf("") }
    var managerEmail by remember { mutableStateOf("") }
    var managerPassword by remember { mutableStateOf("") }

    // Plan — consolidated to 2 plans (STARTER + BUSINESS merged into PRO).
    // Default to PRO since that's the entry-level offering now.
    val planOptions = listOf("PRO", "ENTERPRISE")
    var selectedPlan by remember { mutableStateOf("PRO") }
    var planExpanded by remember { mutableStateOf(false) }

    // Channel Config — initialized from domain features, user can override
    var enableTables by remember { mutableStateOf(true) }
    var enableDineIn by remember { mutableStateOf(true) }
    var enableDelivery by remember { mutableStateOf(true) }
    var enableTakeaway by remember { mutableStateOf(true) }
    var enableInStore by remember { mutableStateOf(false) }
    var enablePickupLater by remember { mutableStateOf(false) }

    // Tax & Stock
    var taxEnabled by remember { mutableStateOf(false) }
    var defaultTaxPercent by remember { mutableStateOf("0") }
    val stockModes = listOf("NONE", "WARN", "ENFORCE")
    var stockMode by remember { mutableStateOf("NONE") }
    var stockModeExpanded by remember { mutableStateOf(false) }

    // Auto-configure when business type changes
    LaunchedEffect(businessType) {
        val features = DomainFeatures.forType(businessType)
        enableTables = features.hasTables
        enableDineIn = features.hasDineIn
        enableDelivery = features.hasDelivery
        enableTakeaway = features.hasTakeaway
        enableInStore = features.hasInStore
        enablePickupLater = features.hasPickupLater
        taxEnabled = features.defaultTaxEnabled
        defaultTaxPercent = features.defaultTaxPercent.toString()
        stockMode = features.defaultStockMode
    }

    var formError by remember { mutableStateOf<String?>(null) }

    val requiredFieldsError = stringResource(Res.string.required_fields)

    val widthClass = LocalWindowSizeClass.current

    LaunchedEffect(message) {
        message?.let { msg ->
            resolvedMessage?.let { snackbarHostState.showSnackbar(it) }
            viewModel.clearMessage()
            if (msg.isSuccess) {
                onBack()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.create_vendor)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .then(
                        if (widthClass == WindowWidthSizeClass.EXPANDED)
                            Modifier.widthIn(max = 900.dp)
                        else Modifier.fillMaxWidth()
                    )
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            // --- Vendor Info Section ---
            SectionHeader(stringResource(Res.string.vendor_info))

            OutlinedTextField(
                value = vendorName,
                onValueChange = { vendorName = it },
                label = { Text(stringResource(Res.string.vendor_name) + " *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vendorAddress,
                onValueChange = { vendorAddress = it },
                label = { Text(stringResource(Res.string.vendor_address) + " *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vendorPhone,
                onValueChange = { vendorPhone = it },
                label = { Text(stringResource(Res.string.vendor_phone) + " *") },
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

            // Business Type Dropdown (all 8 types with icons)
            ExposedDropdownMenuBox(
                expanded = businessTypeExpanded,
                onExpandedChange = { businessTypeExpanded = it }
            ) {
                val currentConfig = VendorTypeConfigs.forType(businessType)
                OutlinedTextField(
                    value = "${currentConfig.icon} ${currentConfig.displayNameEn} — ${currentConfig.displayNameAr}",
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
                    allBusinessTypes.forEach { config ->
                        DropdownMenuItem(
                            text = { Text("${config.icon} ${config.displayNameEn} — ${config.displayNameAr}") },
                            onClick = {
                                businessType = config.type
                                businessTypeExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // --- Manager Info Section ---
            SectionHeader(stringResource(Res.string.manager_info))

            OutlinedTextField(
                value = managerName,
                onValueChange = { managerName = it },
                label = { Text(stringResource(Res.string.manager_name) + " *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = managerPhone,
                onValueChange = { managerPhone = it },
                label = { Text(stringResource(Res.string.manager_phone) + " *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            OutlinedTextField(
                value = managerEmail,
                onValueChange = { managerEmail = it },
                label = { Text(stringResource(Res.string.manager_email)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            OutlinedTextField(
                value = managerPassword,
                onValueChange = { managerPassword = it },
                label = { Text(stringResource(Res.string.manager_password) + " *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(Modifier.height(8.dp))

            // --- Plan Section ---
            SectionHeader(stringResource(Res.string.select_plan))

            ExposedDropdownMenuBox(
                expanded = planExpanded,
                onExpandedChange = { planExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedPlan,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.select_plan)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = planExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = planExpanded,
                    onDismissRequest = { planExpanded = false }
                ) {
                    planOptions.forEach { plan ->
                        DropdownMenuItem(
                            text = { Text(plan) },
                            onClick = {
                                selectedPlan = plan
                                planExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // --- Channel Config Section ---
            SectionHeader(stringResource(Res.string.channel_config))

            SwitchRow(stringResource(Res.string.enable_tables), enableTables) { enableTables = it }
            SwitchRow(stringResource(Res.string.enable_dine_in), enableDineIn) { enableDineIn = it }
            SwitchRow(stringResource(Res.string.enable_delivery), enableDelivery) { enableDelivery = it }
            SwitchRow(stringResource(Res.string.enable_takeaway), enableTakeaway) { enableTakeaway = it }
            SwitchRow(stringResource(Res.string.enable_in_store), enableInStore) { enableInStore = it }
            SwitchRow(stringResource(Res.string.enable_pickup_later), enablePickupLater) { enablePickupLater = it }

            Spacer(Modifier.height(8.dp))

            // --- Tax & Stock Section ---
            SectionHeader(stringResource(Res.string.tax_and_stock))

            SwitchRow(stringResource(Res.string.tax_enabled), taxEnabled) { taxEnabled = it }

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

            if (formError != null) {
                Text(
                    text = formError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Submit Button
            Button(
                onClick = {
                    // Validate required fields
                    when {
                        vendorName.isBlank() || vendorAddress.isBlank() ||
                                vendorPhone.isBlank() || managerName.isBlank() ||
                                managerPhone.isBlank() || managerPassword.isBlank() -> {
                            formError = requiredFieldsError
                        }
                        managerPassword.length < 6 -> {
                            formError = null // will be handled by snackbar
                        }
                        else -> {
                            formError = null
                            val request = CreateVendorRequest(
                                vendor_name = vendorName.trim(),
                                vendor_address = vendorAddress.trim(),
                                vendor_phone = vendorPhone.trim(),
                                wallet_phone = walletPhone.ifBlank { null },
                                store_type = businessType,
                                business_type = businessType,
                                manager_name = managerName.trim(),
                                manager_phone = managerPhone.trim(),
                                manager_email = managerEmail.ifBlank { null },
                                manager_password = managerPassword,
                                plan = selectedPlan,
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
                            viewModel.createVendor(request)
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(Res.string.create))
                }
            }

            Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
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
private fun SwitchRow(
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
