package net.marllex.waselak.admin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FilterList
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.admin.network.VendorDto
import net.marllex.waselak.admin.util.CsvColumn
import net.marllex.waselak.admin.util.FileSaver
import net.marllex.waselak.admin.util.buildCsvString
import net.marllex.waselak.admin.util.LocalWindowSizeClass
import net.marllex.waselak.admin.util.WindowWidthSizeClass
import net.marllex.waselak.admin.viewmodel.VendorsViewModel
import net.marllex.waselak.core.model.VendorTypeConfigs
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

enum class VendorStatusFilter { ALL, ACTIVE, SUSPENDED }
enum class VendorSortOption { NAME, CREATED, USERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorsScreen(
    onCreateVendor: () -> Unit,
    onVendorClick: (String) -> Unit = {},
    viewModel: VendorsViewModel = koinViewModel()
) {
    val vendors by viewModel.vendors.collectAsState()
    val plans by viewModel.plans.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var vendorToDelete by remember { mutableStateOf<VendorDto?>(null) }
    var showFilters by remember { mutableStateOf(false) }

    // Filter states
    var statusFilter by remember { mutableStateOf(VendorStatusFilter.ALL) }
    var planFilter by remember { mutableStateOf<String?>(null) } // null = all plans
    var typeFilter by remember { mutableStateOf<String?>(null) } // null = all types
    var sortOption by remember { mutableStateOf(VendorSortOption.NAME) }
    var sortAscending by remember { mutableStateOf(true) }

    val widthClass = LocalWindowSizeClass.current

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadVendors()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Distinct plan names and business types from actual data
    val availablePlanNames = remember(vendors) {
        vendors.mapNotNull { it.plan_name }.distinct().sorted()
    }
    val availableBusinessTypes = remember(vendors) {
        vendors.map { it.business_type }.distinct().sorted()
    }

    val filteredVendors = remember(vendors, searchQuery, statusFilter, planFilter, typeFilter, sortOption, sortAscending) {
        var result = vendors

        // Search filter
        if (searchQuery.isNotBlank()) {
            result = result.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.address.contains(searchQuery, ignoreCase = true) ||
                        it.contact_phone.contains(searchQuery, ignoreCase = true)
            }
        }

        // Status filter
        result = when (statusFilter) {
            VendorStatusFilter.ALL -> result
            VendorStatusFilter.ACTIVE -> result.filter { !it.is_suspended }
            VendorStatusFilter.SUSPENDED -> result.filter { it.is_suspended }
        }

        // Plan filter
        if (planFilter != null) {
            result = result.filter { it.plan_name == planFilter }
        }

        // Business type filter
        if (typeFilter != null) {
            result = result.filter { it.business_type == typeFilter }
        }

        // Sort
        result = when (sortOption) {
            VendorSortOption.NAME -> if (sortAscending) result.sortedBy { it.name.lowercase() }
            else result.sortedByDescending { it.name.lowercase() }
            VendorSortOption.CREATED -> if (sortAscending) result.sortedBy { it.created_at }
            else result.sortedByDescending { it.created_at }
            VendorSortOption.USERS -> if (sortAscending) result.sortedBy { it.users_count }
            else result.sortedByDescending { it.users_count }
        }

        result
    }

    val activeFilterCount = remember(statusFilter, planFilter, typeFilter) {
        var count = 0
        if (statusFilter != VendorStatusFilter.ALL) count++
        if (planFilter != null) count++
        if (typeFilter != null) count++
        count
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateVendor) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.create_vendor))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Header with title and count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.vendors),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (vendors.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.vendors_count, filteredVendors.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Search + filter toggle row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(Res.string.search_vendors)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                // Filter toggle button
                FilledTonalIconButton(
                    onClick = { showFilters = !showFilters }
                ) {
                    BadgedBox(
                        badge = {
                            if (activeFilterCount > 0) {
                                Badge { Text("$activeFilterCount") }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Outlined.FilterList,
                            contentDescription = if (showFilters) stringResource(Res.string.hide_filters)
                            else stringResource(Res.string.show_filters)
                        )
                    }
                }

                // Export CSV button
                val coroutineScope = rememberCoroutineScope()
                FilledTonalIconButton(
                    onClick = {
                        if (filteredVendors.isEmpty()) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("No data to export") }
                            return@FilledTonalIconButton
                        }
                        val csv = buildCsvString(filteredVendors, vendorCsvColumns())
                        val success = FileSaver.saveCsv(csv, "vendors_export.csv")
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                if (success) "CSV exported successfully" else "Export cancelled"
                            )
                        }
                    }
                ) {
                    Icon(
                        Icons.Outlined.FileDownload,
                        contentDescription = stringResource(Res.string.export_csv)
                    )
                }
            }

            // Collapsible filter section
            AnimatedVisibility(
                visible = showFilters,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Status filter chips
                    Text(
                        text = stringResource(Res.string.filter_by_status),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = statusFilter == VendorStatusFilter.ALL,
                                onClick = { statusFilter = VendorStatusFilter.ALL },
                                label = { Text(stringResource(Res.string.all_statuses)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = statusFilter == VendorStatusFilter.ACTIVE,
                                onClick = { statusFilter = VendorStatusFilter.ACTIVE },
                                label = { Text(stringResource(Res.string.active)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = statusFilter == VendorStatusFilter.SUSPENDED,
                                onClick = { statusFilter = VendorStatusFilter.SUSPENDED },
                                label = { Text(stringResource(Res.string.suspended)) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Plan filter chips
                    if (availablePlanNames.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.filter_by_plan),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = planFilter == null,
                                    onClick = { planFilter = null },
                                    label = { Text(stringResource(Res.string.all_plans)) }
                                )
                            }
                            items(availablePlanNames) { plan ->
                                FilterChip(
                                    selected = planFilter == plan,
                                    onClick = { planFilter = if (planFilter == plan) null else plan },
                                    label = { Text(plan) }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }

                    // Business type filter chips
                    if (availableBusinessTypes.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.filter_by_type),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = typeFilter == null,
                                    onClick = { typeFilter = null },
                                    label = { Text(stringResource(Res.string.all_types)) }
                                )
                            }
                            items(availableBusinessTypes) { type ->
                                FilterChip(
                                    selected = typeFilter == type,
                                    onClick = { typeFilter = if (typeFilter == type) null else type },
                                    label = { Text("${VendorTypeConfigs.iconForType(type)} $type") }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }

                    // Sort options
                    Text(
                        text = stringResource(Res.string.sort_by),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = sortOption == VendorSortOption.NAME,
                                onClick = {
                                    if (sortOption == VendorSortOption.NAME) sortAscending = !sortAscending
                                    else { sortOption = VendorSortOption.NAME; sortAscending = true }
                                },
                                label = {
                                    Text(
                                        stringResource(Res.string.sort_name) +
                                                if (sortOption == VendorSortOption.NAME) (if (sortAscending) " ↑" else " ↓") else ""
                                    )
                                }
                            )
                        }
                        item {
                            FilterChip(
                                selected = sortOption == VendorSortOption.CREATED,
                                onClick = {
                                    if (sortOption == VendorSortOption.CREATED) sortAscending = !sortAscending
                                    else { sortOption = VendorSortOption.CREATED; sortAscending = false }
                                },
                                label = {
                                    Text(
                                        stringResource(Res.string.sort_created) +
                                                if (sortOption == VendorSortOption.CREATED) (if (sortAscending) " ↑" else " ↓") else ""
                                    )
                                }
                            )
                        }
                        item {
                            FilterChip(
                                selected = sortOption == VendorSortOption.USERS,
                                onClick = {
                                    if (sortOption == VendorSortOption.USERS) sortAscending = !sortAscending
                                    else { sortOption = VendorSortOption.USERS; sortAscending = false }
                                },
                                label = {
                                    Text(
                                        stringResource(Res.string.sort_users) +
                                                if (sortOption == VendorSortOption.USERS) (if (sortAscending) " ↑" else " ↓") else ""
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isLoading && vendors.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredVendors.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank() || activeFilterCount > 0)
                            stringResource(Res.string.no_search_match)
                        else stringResource(Res.string.no_vendors),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val columns = when (widthClass) {
                    WindowWidthSizeClass.COMPACT -> 1
                    WindowWidthSizeClass.MEDIUM -> 2
                    WindowWidthSizeClass.EXPANDED -> 3
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filteredVendors, key = { it.id }) { vendor ->
                        VendorCard(
                            vendor = vendor,
                            planNames = plans.map { it.name },
                            onClick = { onVendorClick(vendor.id) },
                            onSuspendToggle = { suspended ->
                                viewModel.suspendVendor(vendor.id, suspended, null)
                            },
                            onDelete = { vendorToDelete = vendor },
                            onPlanChange = { plan ->
                                viewModel.changeVendorPlan(vendor.id, plan)
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    vendorToDelete?.let { vendor ->
        AlertDialog(
            onDismissRequest = { vendorToDelete = null },
            title = { Text(stringResource(Res.string.confirm_delete)) },
            text = {
                Text(stringResource(Res.string.confirm_delete_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteVendor(vendor.id)
                        vendorToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { vendorToDelete = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VendorCard(
    vendor: VendorDto,
    planNames: List<String>,
    onClick: () -> Unit,
    onSuspendToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onPlanChange: (String) -> Unit
) {
    var planDropdownExpanded by remember { mutableStateOf(false) }

    val activeText = stringResource(Res.string.active)
    val suspendedText = stringResource(Res.string.suspended)
    val noPlanText = stringResource(Res.string.no_plan)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${VendorTypeConfigs.iconForType(vendor.business_type)} ${vendor.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = vendor.business_type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Plan badge
                    val planDisplay = vendor.plan_display_name ?: vendor.plan_name ?: noPlanText
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
                            text = planDisplay,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = planColor
                        )
                    }

                    // Status badge
                    val statusColor = if (vendor.is_suspended)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (vendor.is_suspended) suspendedText else activeText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "${vendor.address} | ${vendor.contact_phone}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "${vendor.users_count} ${stringResource(Res.string.users)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Suspend / Activate button
                if (vendor.is_suspended) {
                    OutlinedButton(
                        onClick = { onSuspendToggle(false) }
                    ) {
                        Text(stringResource(Res.string.activate_vendor))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSuspendToggle(true) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(Res.string.suspend_vendor))
                    }
                }

                // Plan change dropdown
                Box {
                    OutlinedButton(onClick = { planDropdownExpanded = true }) {
                        Text(stringResource(Res.string.change_plan))
                    }
                    DropdownMenu(
                        expanded = planDropdownExpanded,
                        onDismissRequest = { planDropdownExpanded = false }
                    ) {
                        planNames.forEach { plan ->
                            DropdownMenuItem(
                                text = { Text(plan) },
                                onClick = {
                                    onPlanChange(plan)
                                    planDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Delete button
                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.delete_vendor))
                }
            }
        }
    }
}

private fun vendorCsvColumns(): List<CsvColumn<VendorDto>> = listOf(
    CsvColumn("Name") { it.name },
    CsvColumn("Address") { it.address },
    CsvColumn("Phone") { it.contact_phone },
    CsvColumn("Business Type") { it.business_type },
    CsvColumn("Status") { if (it.is_suspended) "Suspended" else "Active" },
    CsvColumn("Plan") { it.plan_display_name ?: it.plan_name ?: "No Plan" },
    CsvColumn("Subscription") { it.subscription_status ?: "" },
    CsvColumn("Users") { it.users_count.toString() },
    CsvColumn("Tables") { if (it.enable_tables) "Yes" else "No" },
    CsvColumn("Dine-In") { if (it.enable_dine_in) "Yes" else "No" },
    CsvColumn("Delivery") { if (it.enable_delivery) "Yes" else "No" },
    CsvColumn("Takeaway") { if (it.enable_takeaway) "Yes" else "No" },
    CsvColumn("Tax Enabled") { if (it.tax_enabled) "Yes" else "No" },
    CsvColumn("Tax %") { it.default_tax_percent.toString() },
    CsvColumn("Stock Mode") { it.stock_mode },
)
