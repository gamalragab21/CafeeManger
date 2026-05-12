package net.marllex.waselak.feature.cashier.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import net.marllex.waselak.core.ui.components.FeatureNotAvailableView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.components.ProfileAvatar
import net.marllex.waselak.core.ui.platform.rememberPlatformActions
import net.marllex.waselak.feature.cashier.attendance.components.PinEntryDialog
import net.marllex.waselak.feature.cashier.attendance.components.QrScannerDialog
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.cashier.attendance.generated.resources.Res
import net.marllex.waselak.feature.cashier.attendance.generated.resources.*
import org.koin.compose.viewmodel.koinViewModel
import net.marllex.waselak.core.common.extensions.formatEpochMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    viewModel: AttendanceViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val platformActions = rememberPlatformActions()

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
                AttendanceViewModel.AuthAction.CheckIn -> stringResource(Res.string.scan_qr_to_check_in)
                AttendanceViewModel.AuthAction.CheckOut -> stringResource(Res.string.scan_qr_to_check_out)
                null -> stringResource(Res.string.scan_qr_code)
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
            errorMessage = uiState.qrErrorMessage ?: stringResource(Res.string.qr_scan_failed),
            onRetry = viewModel::retryQrScan,
            onDismiss = viewModel::dismissQrErrorDialog,
        )
    }

    // Show PIN error dialog
    if (uiState.showPinErrorDialog) {
        PinErrorDialog(
            errorMessage = uiState.pinErrorMessage ?: stringResource(Res.string.pin_auth_failed),
            onRetry = viewModel::retryPinEntry,
            onDismiss = viewModel::dismissPinErrorDialog,
        )
    }

    // Show toast messages
    LaunchedEffect(uiState.successMessage, uiState.error) {
        uiState.successMessage?.let { msg ->
            platformActions.showToast(msg)
            viewModel.clearMessages()
        }
        uiState.error?.let { msg ->
            platformActions.showToast(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.attendance_title),
                isLoading = uiState.isLoading,
                onRefresh = viewModel::loadData,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                uiState.showFeatureNotAvailable -> FeatureNotAvailableView(
                    message = uiState.featureNotAvailableMessage,
                )
                uiState.isLoading && uiState.workers.isEmpty() -> LoadingIndicator()
                else -> {
                    val filteredWorkers = remember(uiState.workers, uiState.searchQuery) {
                        if (uiState.searchQuery.isBlank()) uiState.workers
                        else uiState.workers.filter {
                            it.fullName.contains(uiState.searchQuery, ignoreCase = true) ||
                            it.workerId.contains(uiState.searchQuery, ignoreCase = true)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Offline mode banner
                    if (uiState.isOffline) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.CloudOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(Res.string.offline_mode),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                        Text(
                                            text = stringResource(Res.string.offline_mode_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Pending sync / syncing indicator
                    if (uiState.pendingCount > 0 || uiState.isSyncing) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (uiState.isSyncing)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (uiState.isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Text(
                                            text = stringResource(Res.string.sync_in_progress),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Sync,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = stringResource(Res.string.pending_sync, uiState.pendingCount),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Today's summary card
                    item {
                        val presentCount = uiState.todaySummary.count { it.presentToday }
                        val awayCount = uiState.todaySummary.count { !it.presentToday && it.attendedToday }
                        val absentCount = uiState.todaySummary.count { !it.presentToday && !it.attendedToday }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = stringResource(Res.string.today_attendance),
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
                                            text = stringResource(Res.string.present),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = awayCount.toString(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary,
                                        )
                                        Text(
                                            text = stringResource(Res.string.away),
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
                                            text = stringResource(Res.string.absent),
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
                                enabled = !uiState.isOffline,
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(Res.string.check_in_with_qr))
                            }
                            OutlinedButton(
                                onClick = viewModel::showQrScannerForCheckOut,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !uiState.isOffline,
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(Res.string.check_out_with_qr))
                            }
                        }
                    }

                    // Search bar
                    item {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = { Text(stringResource(Res.string.search_worker)) },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                        )
                    }

                    // Title
                    item {
                        Text(
                            text = stringResource(Res.string.select_worker_to_check_in),
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
                                    text = stringResource(Res.string.no_workers_available),
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
                        val attendedToday = summary?.attendedToday ?: false
                        // Get the LATEST attendance record for this worker today
                        // (worker may have multiple records if they logged in/out multiple times)
                        val workerRecords = uiState.todayRecords
                            .filter { it.workerId == worker.id }
                            .sortedByDescending { it.checkIn }
                        val latestRecord = workerRecords.firstOrNull()
                        val isCheckedOut = latestRecord?.isCheckedOut ?: false

                        WorkerAttendanceCard(
                            workerName = worker.fullName,
                            workerId = worker.workerId,
                            role = worker.role,
                            photoUrl = worker.photoUrl,
                            isPresent = isPresent,
                            attendedToday = attendedToday,
                            isCheckedOut = isCheckedOut,
                            checkInTime = latestRecord?.checkIn,
                            checkOutTime = latestRecord?.checkOut,
                            onCheckInWithPin = { viewModel.showPinDialogForCheckIn(worker) },
                            onCheckOutWithPin = {
                                latestRecord?.let {
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
}

@Composable
private fun WorkerAttendanceCard(
    workerName: String,
    workerId: String,
    role: String,
    photoUrl: String? = null,
    isPresent: Boolean,
    attendedToday: Boolean,
    isCheckedOut: Boolean,
    checkInTime: Long?,
    checkOutTime: Long?,
    onCheckInWithPin: () -> Unit,
    onCheckOutWithPin: () -> Unit,
) {
    // Three states:
    // 1. Present (isPresent=true): green — currently working (checked in, not checked out)
    // 2. Away/Left (!isPresent && attendedToday): orange — attended today but left (checked out)
    // 3. Absent (!isPresent && !attendedToday): red — no attendance record today
    val isAway = !isPresent && attendedToday

    val statusColor = when {
        isPresent -> MaterialTheme.colorScheme.primary
        isAway -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    val statusLabel = when {
        isPresent -> stringResource(Res.string.present)
        isAway -> stringResource(Res.string.away)
        else -> stringResource(Res.string.absent)
    }

    val statusContainerColor = when {
        isPresent -> MaterialTheme.colorScheme.primaryContainer
        isAway -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Worker avatar with status indicator
                Box {
                    ProfileAvatar(
                        photoUrl = photoUrl,
                        size = 44.dp,
                        contentDescription = workerName,
                    )
                    // Status dot overlay
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                            .align(Alignment.BottomEnd)
                    )
                }
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
                            statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    modifier = Modifier.height(28.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = statusContainerColor,
                    ),
                )
            }

            // Show check-in/out times
            if (checkInTime != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(Res.string.check_in_time_label, checkInTime.formatEpochMs("hh:mm a")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (checkOutTime != null) {
                        Text(
                            text = stringResource(Res.string.check_out_time_label, checkOutTime.formatEpochMs("hh:mm a")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action button
            when {
                // Absent: no attendance today → check in
                !isPresent && !attendedToday -> {
                    Button(
                        onClick = onCheckInWithPin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Pin, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.check_in_with_pin))
                    }
                }
                // Present: currently working → can check out
                isPresent && !isCheckedOut -> {
                    OutlinedButton(
                        onClick = onCheckOutWithPin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Pin, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.check_out_with_pin))
                    }
                }
                // Away/Left: attended today but left → show checked out message
                else -> {
                    Text(
                        text = stringResource(Res.string.checked_out),
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
                text = stringResource(Res.string.qr_scan_failed),
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
                            text = stringResource(Res.string.possible_reasons),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        val reasons = listOf(
                            stringResource(Res.string.qr_error_invalid),
                            stringResource(Res.string.qr_error_expired),
                            stringResource(Res.string.qr_error_worker_not_found),
                            stringResource(Res.string.qr_error_already_checked_in)
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
                Text(stringResource(Res.string.try_again))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(Res.string.cancel))
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
                text = stringResource(Res.string.pin_auth_failed),
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

                // Detect whether this looks like a connectivity issue. When the
                // error is the standard "no internet / connection" copy from
                // userFriendlyMessage(), or a generic "unexpected error",
                // showing the PIN-specific "Possible Reasons" list mislead
                // cashiers into thinking the PIN was wrong (it usually wasn't —
                // the request just timed out on slow WiFi). For those cases we
                // hide the reasons list entirely.
                val lowered = errorMessage.lowercase()
                val looksLikeNetwork = "اتصال" in errorMessage ||
                    "إنترنت" in errorMessage ||
                    "غير متوقع" in errorMessage ||
                    "internet" in lowered ||
                    "network" in lowered ||
                    "timeout" in lowered ||
                    "unexpected" in lowered ||
                    "session" in lowered ||
                    "جلسة" in errorMessage

                if (!looksLikeNetwork) {
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
                                text = stringResource(Res.string.possible_reasons),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )

                            val reasons = listOf(
                                stringResource(Res.string.pin_error_incorrect),
                                stringResource(Res.string.pin_error_no_pin_set),
                                stringResource(Res.string.pin_error_too_many_attempts),
                                stringResource(Res.string.pin_error_worker_not_found)
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
                Text(stringResource(Res.string.try_again))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(Res.string.cancel))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
