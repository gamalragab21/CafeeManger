package net.marllex.waselak.cashier.prescriptions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.Prescription
import net.marllex.waselak.core.model.PrescriptionItem
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescriptionsScreen(
    viewModel: PrescriptionsViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.prescriptions)) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showCreateDialog) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.new_prescription))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Status filter chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = uiState.statusFilter == null, onClick = { viewModel.onStatusFilterChange(null) }, label = { Text(stringResource(Res.string.all)) })
                FilterChip(selected = uiState.statusFilter == "PENDING", onClick = { viewModel.onStatusFilterChange("PENDING") }, label = { Text(stringResource(Res.string.pending)) })
                FilterChip(selected = uiState.statusFilter == "DISPENSED", onClick = { viewModel.onStatusFilterChange("DISPENSED") }, label = { Text(stringResource(Res.string.dispensed)) })
                FilterChip(selected = uiState.statusFilter == "CANCELLED", onClick = { viewModel.onStatusFilterChange("CANCELLED") }, label = { Text(stringResource(Res.string.status_canceled)) })
            }

            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.error != null && uiState.prescriptions.isEmpty() -> ErrorView(message = uiState.error!!, onRetry = viewModel::load)
                uiState.filteredPrescriptions.isEmpty() -> EmptyView(stringResource(Res.string.no_prescriptions_found))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(uiState.filteredPrescriptions, key = { it.id }) { prescription ->
                        PrescriptionCard(
                            prescription = prescription,
                            onDispense = { viewModel.showDispenseDialog(prescription) },
                            onCancel = { viewModel.cancelPrescription(prescription.id) },
                            onSelect = { viewModel.selectPrescription(prescription) },
                        )
                    }
                }
            }
        }
    }

    // Create Prescription Dialog
    if (uiState.showCreateDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCreateDialog,
            title = { Text(stringResource(Res.string.new_prescription)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uiState.patientName, onValueChange = viewModel::onPatientNameChange, label = { Text(stringResource(Res.string.patient_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = uiState.patientPhone, onValueChange = viewModel::onPatientPhoneChange, label = { Text(stringResource(Res.string.patient_phone)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = uiState.doctorName, onValueChange = viewModel::onDoctorNameChange, label = { Text(stringResource(Res.string.doctor_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = uiState.diagnosis, onValueChange = viewModel::onDiagnosisChange, label = { Text(stringResource(Res.string.diagnosis)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = uiState.notes, onValueChange = viewModel::onNotesChange, label = { Text(stringResource(Res.string.notes)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::createPrescription,
                    enabled = !uiState.isSaving && uiState.patientName.isNotBlank(),
                ) { Text(stringResource(Res.string.create)) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissCreateDialog) { Text(stringResource(Res.string.cancel)) } },
        )
    }

    // Dispense Confirmation Dialog
    if (uiState.showDispenseDialog) {
        val prescription = uiState.dispensePrescription
        AlertDialog(
            onDismissRequest = viewModel::dismissDispenseDialog,
            title = { Text(stringResource(Res.string.dispense_prescription)) },
            text = {
                Column {
                    Text(stringResource(Res.string.patient_label, prescription?.patientName ?: ""))
                    Text(stringResource(Res.string.items_to_dispense, prescription?.items?.count { it.isPending } ?: 0))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(Res.string.dispense_confirm_message), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::dispensePrescription,
                    enabled = !uiState.isSaving,
                ) { Text(stringResource(Res.string.dispense)) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDispenseDialog) { Text(stringResource(Res.string.cancel)) } },
        )
    }

    // Detail bottom sheet
    uiState.selectedPrescription?.let { prescription ->
        PrescriptionDetailSheet(
            prescription = prescription,
            onDismiss = { viewModel.selectPrescription(null) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrescriptionCard(
    prescription: Prescription,
    onDispense: () -> Unit,
    onCancel: () -> Unit,
    onSelect: () -> Unit,
) {
    val statusColor = when {
        prescription.isPending -> Color(0xFFFF9800)
        prescription.isDispensed -> Color(0xFF4CAF50)
        prescription.isPartiallyDispensed -> Color(0xFF2196F3)
        prescription.isCancelled -> Color(0xFF9E9E9E)
        prescription.isExpired -> Color(0xFFF44336)
        else -> Color.Gray
    }

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(prescription.patientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    prescription.doctorName?.let {
                        Text(stringResource(Res.string.doctor_prefix, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text(prescription.status, style = MaterialTheme.typography.labelSmall) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = statusColor.copy(alpha = 0.15f)),
                )
            }
            prescription.diagnosis?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.items_dispensed_count, prescription.dispensedItemCount, prescription.itemCount), style = MaterialTheme.typography.bodySmall)

            if (prescription.canDispense) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onDispense) { Text(stringResource(Res.string.dispense)) }
                    TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrescriptionDetailSheet(prescription: Prescription, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(stringResource(Res.string.prescription_details), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            // Patient info
            Text(stringResource(Res.string.patient), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(prescription.patientName, style = MaterialTheme.typography.titleMedium)
            prescription.patientPhone?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(12.dp))

            // Doctor
            prescription.doctorName?.let {
                Text(stringResource(Res.string.doctor), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.doctor_prefix, it), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }

            // Diagnosis
            prescription.diagnosis?.let {
                Text(stringResource(Res.string.diagnosis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(it, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }

            // Items
            if (prescription.items.isNotEmpty()) {
                Text(stringResource(Res.string.items_count, prescription.items.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                prescription.items.forEach { item ->
                    PrescriptionItemRow(item)
                    Spacer(Modifier.height(4.dp))
                }
            }

            prescription.notes?.let {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.notes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PrescriptionItemRow(item: PrescriptionItem) {
    val statusColor = when {
        item.isDispensed -> Color(0xFF4CAF50)
        item.isSubstituted -> Color(0xFF2196F3)
        item.isUnavailable -> Color(0xFFF44336)
        else -> Color(0xFFFF9800)
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${item.quantity}x ${item.itemName ?: item.itemId}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                item.dosage?.let { Text(stringResource(Res.string.dosage_label, it), style = MaterialTheme.typography.bodySmall) }
                item.frequency?.let { Text(stringResource(Res.string.frequency_label, it), style = MaterialTheme.typography.bodySmall) }
                item.duration?.let { Text(stringResource(Res.string.duration_label, it), style = MaterialTheme.typography.bodySmall) }
                item.instructions?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(item.status, style = MaterialTheme.typography.labelSmall, color = statusColor)
        }
    }
}
