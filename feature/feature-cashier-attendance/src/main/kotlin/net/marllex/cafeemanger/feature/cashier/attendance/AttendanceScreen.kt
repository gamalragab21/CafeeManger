package net.marllex.cafeemanger.feature.cashier.attendance

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import net.marllex.cafeemanger.feature.cashier.attendance.components.PinEntryDialog
import net.marllex.cafeemanger.feature.cashier.attendance.components.QrScannerDialog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Show PIN dialog
    if (uiState.showPinDialog && uiState.selectedWorker != null) {
        PinEntryDialog(
            workerName = uiState.selectedWorker!!.fullName,
            onPinEntered = { pin ->
                when (uiState.authAction) {
                    AttendanceViewModel.AuthAction.CheckIn -> {
                        viewModel.checkInWithPin(uiState.selectedWorker!!.id, pin)
                    }
                    AttendanceViewModel.AuthAction.CheckOut -> {
                        uiState.selectedAttendanceId?.let { attendanceId ->
                            viewModel.checkOutWithPin(attendanceId, pin)
                        }
                    }
                    null -> {}
                }
            },
            onDismiss = viewModel::dismissPinDialog,
        )
    }

    // Show QR scanner
    if (uiState.showQrScanner) {
        QrScannerDialog(
            title = when (uiState.authAction) {
                AttendanceViewModel.AuthAction.CheckIn -> stringResource(R.string.scan_qr_to_check_in)
                AttendanceViewModel.AuthAction.CheckOut -> stringResource(R.string.scan_qr_to_check_out)
                null -> stringResource(R.string.scan_qr_code)
            },
            onQrCodeScanned = { qrData ->
                when (uiState.authAction) {
                    AttendanceViewModel.AuthAction.CheckIn -> {
                        viewModel.checkInWithQr(qrData)
                    }
                    AttendanceViewModel.AuthAction.CheckOut -> {
                        viewModel.checkOutWithQr(qrData)
                    }
                    null -> {}
                }
            },
            onDismiss = viewModel::dismissQrScanner,
        )
    }

    // Show QR error dialog
    if (uiState.showQrErrorDialog) {
        QrErrorDialog(
            errorMessage = uiState.qrErrorMessage ?: stringResource(R.string.qr_scan_failed),
            onRetry = viewModel::retryQrScan,
            onDismiss = viewModel::dismissQrErrorDialog,
        )
    }

    // Show PIN error dialog
    if (uiState.showPinErrorDialog) {
        PinErrorDialog(
            errorMessage = uiState.pinErrorMessage ?: stringResource(R.string.pin_auth_failed),
            onRetry = viewModel::retryPinEntry,
            onDismiss = viewModel::dismissPinErrorDialog,
        )
    }

    // Show toast messages
    LaunchedEffect(uiState.successMessage, uiState.error) {
        uiState.successMessage?.let { msg ->
            val text = when (msg) {
                "check_in_success" -> context.getString(R.string.check_in_success)
                "check_out_success" -> context.getString(R.string.check_out_success)
                else -> msg
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
        uiState.error?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.attendance_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            else -> {
                val filteredWorkers = remember(uiState.workers, uiState.searchQuery) {
                    if (uiState.searchQuery.isBlank()) uiState.workers
                    else uiState.workers.filter {
                        it.fullName.contains(uiState.searchQuery, ignoreCase = true) ||
                        it.workerId.contains(uiState.searchQuery, ignoreCase = true)
                    }
                }

                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Today's summary card
                    item {
                        val presentCount = uiState.todaySummary.count { it.presentToday }
                        val absentCount = uiState.todaySummary.count { !it.presentToday }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = stringResource(R.string.today_attendance),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = presentCount.toString(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = stringResource(R.string.present),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = absentCount.toString(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Text(
                                            text = stringResource(R.string.absent),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Quick action buttons for QR scanning
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = viewModel::showQrScannerForCheckIn,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.check_in_with_qr))
                            }
                            OutlinedButton(
                                onClick = viewModel::showQrScannerForCheckOut,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.check_out_with_qr))
                            }
                        }
                    }

                    // Search bar
                    item {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = { Text(stringResource(R.string.search_worker)) },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                        )
                    }

                    // Title
                    item {
                        Text(
                            text = stringResource(R.string.select_worker_to_check_in),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (filteredWorkers.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    Icons.Filled.PersonOff, null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.no_workers_available),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    items(filteredWorkers, key = { it.id }) { worker ->
                        val summary = uiState.todaySummary.find { it.workerId == worker.id }
                        val isPresent = summary?.presentToday ?: false
                        val todayRecord = uiState.todayRecords.find { it.workerId == worker.id }
                        val isCheckedOut = todayRecord?.isCheckedOut ?: false

                        WorkerAttendanceCard(
                            workerName = worker.fullName,
                            workerId = worker.workerId,
                            role = worker.role,
                            isPresent = isPresent,
                            isCheckedOut = isCheckedOut,
                            checkInTime = todayRecord?.checkIn,
                            checkOutTime = todayRecord?.checkOut,
                            onCheckInWithPin = { viewModel.showPinDialogForCheckIn(worker) },
                            onCheckOutWithPin = {
                                todayRecord?.let {
                                    viewModel.showPinDialogForCheckOut(worker, it.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkerAttendanceCard(
    workerName: String,
    workerId: String,
    role: String,
    isPresent: Boolean,
    isCheckedOut: Boolean,
    checkInTime: Long?,
    checkOutTime: Long?,
    onCheckInWithPin: () -> Unit,
    onCheckOutWithPin: () -> Unit,
) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPresent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = workerId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = role,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (isPresent) stringResource(R.string.present) else stringResource(R.string.absent),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    modifier = Modifier.height(28.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isPresent)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                    ),
                )
            }

            // Show check-in/out times
            if (checkInTime != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "In: ${timeFormatter.format(Date(checkInTime))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (checkOutTime != null) {
                        Text(
                            text = "Out: ${timeFormatter.format(Date(checkOutTime))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action button
            when {
                !isPresent -> {
                    Button(
                        onClick = onCheckInWithPin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Pin, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.check_in_with_pin))
                    }
                }
                isPresent && !isCheckedOut -> {
                    OutlinedButton(
                        onClick = onCheckOutWithPin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Pin, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.check_out_with_pin))
                    }
                }
                else -> {
                    // Already checked in and out
                    Text(
                        text = stringResource(R.string.checked_out),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}


@Composable
private fun QrErrorDialog(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.qr_scan_failed),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Show common error explanations
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.possible_reasons),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        val reasons = listOf(
                            stringResource(R.string.qr_error_invalid),
                            stringResource(R.string.qr_error_expired),
                            stringResource(R.string.qr_error_worker_not_found),
                            stringResource(R.string.qr_error_already_checked_in)
                        )
                        
                        reasons.forEach { reason ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.try_again))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}


@Composable
private fun PinErrorDialog(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.pin_auth_failed),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Show common error explanations
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.possible_reasons),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        val reasons = listOf(
                            stringResource(R.string.pin_error_incorrect),
                            stringResource(R.string.pin_error_no_pin_set),
                            stringResource(R.string.pin_error_too_many_attempts),
                            stringResource(R.string.pin_error_worker_not_found)
                        )
                        
                        reasons.forEach { reason ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Filled.Pin,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.try_again))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
