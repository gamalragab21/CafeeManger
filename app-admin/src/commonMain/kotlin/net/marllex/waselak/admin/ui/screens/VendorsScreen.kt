package net.marllex.waselak.admin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.admin.network.VendorDto
import net.marllex.waselak.admin.viewmodel.VendorsViewModel
import net.marllex.waselak.core.model.VendorTypeConfigs
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

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

    val filteredVendors = remember(vendors, searchQuery) {
        if (searchQuery.isBlank()) vendors
        else vendors.filter { it.name.contains(searchQuery, ignoreCase = true) }
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
            Text(
                text = stringResource(Res.string.vendors),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(Res.string.search_vendors)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

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
                        text = if (searchQuery.isNotBlank()) stringResource(Res.string.no_search_match)
                        else stringResource(Res.string.no_vendors),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
