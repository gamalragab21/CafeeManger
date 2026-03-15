package net.marllex.waselak.admin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.admin.viewmodel.AdminNotificationsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminNotificationsScreen(
    viewModel: AdminNotificationsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar for success/error
    val successMsg = stringResource(Res.string.notification_sent_success)
    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            snackbarHostState.showSnackbar(successMsg)
            viewModel.clearSuccess()
        }
    }
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            snackbarHostState.showSnackbar(uiState.error!!)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.send_notification)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Notification Type ──────────────────────────
            Text(stringResource(Res.string.notification_type), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.type == "ADMIN_ANNOUNCEMENT",
                    onClick = { viewModel.onTypeChange("ADMIN_ANNOUNCEMENT") },
                    label = { Text(stringResource(Res.string.admin_announcement)) },
                    leadingIcon = if (uiState.type == "ADMIN_ANNOUNCEMENT") {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null,
                )
                FilterChip(
                    selected = uiState.type == "SYSTEM_UPDATE",
                    onClick = { viewModel.onTypeChange("SYSTEM_UPDATE") },
                    label = { Text(stringResource(Res.string.system_update)) },
                    leadingIcon = if (uiState.type == "SYSTEM_UPDATE") {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null,
                )
            }

            // ── Title & Body ───────────────────────────────
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(Res.string.notification_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.body,
                onValueChange = viewModel::onBodyChange,
                label = { Text(stringResource(Res.string.notification_body)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── SYSTEM_UPDATE extras ───────────────────────
            if (uiState.type == "SYSTEM_UPDATE") {
                HorizontalDivider()
                Text(stringResource(Res.string.system_update), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = uiState.actionUrl,
                    onValueChange = viewModel::onActionUrlChange,
                    label = { Text(stringResource(Res.string.download_link)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Link, null) },
                )

                Text(stringResource(Res.string.target_platform), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val platforms = listOf(null to stringResource(Res.string.all_platforms), "ANDROID" to "Android", "DESKTOP" to "Desktop", "IOS" to "iOS")
                    platforms.forEach { (value, label) ->
                        FilterChip(
                            selected = uiState.platform == value,
                            onClick = { viewModel.onPlatformChange(value) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // ── Priority ───────────────────────────────────
            HorizontalDivider()
            Text(stringResource(Res.string.priority), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("NORMAL", "HIGH", "URGENT").forEach { p ->
                    FilterChip(
                        selected = uiState.priority == p,
                        onClick = { viewModel.onPriorityChange(p) },
                        label = { Text(p) },
                    )
                }
            }

            // ── Target Vendors ─────────────────────────────
            HorizontalDivider()
            Text(stringResource(Res.string.select_vendors), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            FilterChip(
                selected = uiState.allVendors,
                onClick = { viewModel.toggleAllVendors(true) },
                label = { Text(stringResource(Res.string.all_vendors)) },
                leadingIcon = if (uiState.allVendors) {
                    { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                } else null,
            )

            if (uiState.isLoadingVendors) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                // Vendor chips (multi-select)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    uiState.vendors.forEach { vendor ->
                        FilterChip(
                            selected = vendor.id in uiState.selectedVendorIds,
                            onClick = { viewModel.toggleVendor(vendor.id) },
                            label = { Text(vendor.name) },
                            leadingIcon = if (vendor.id in uiState.selectedVendorIds) {
                                { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                            } else null,
                        )
                    }
                }
            }

            // ── Send Button ────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::showConfirm,
                enabled = uiState.canSend,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (uiState.isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Send, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.send_notification))
                }
            }
        }
    }

    // ── Confirmation Dialog ────────────────────────────
    if (uiState.showConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(stringResource(Res.string.send_notification)) },
            text = {
                Text(stringResource(Res.string.send_to_vendors_count, uiState.targetCount))
            },
            confirmButton = {
                TextButton(onClick = viewModel::send) {
                    Text(stringResource(Res.string.send_notification))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}
