package net.marllex.cafeemanger.feature.cashier.attendance

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Handle biometric prompt when action is pending
    LaunchedEffect(uiState.pendingAction) {
        if (uiState.pendingAction != null) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                val biometricManager = BiometricManager.from(context)
                val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

                if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                    val executor = ContextCompat.getMainExecutor(context)
                    val callback = object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            viewModel.onBiometricSuccess()
                        }
                        override fun onAuthenticationFailed() {
                            // Don't cancel yet, user can retry
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                viewModel.onBiometricFailed()
                            } else {
                                viewModel.onBiometricFailed()
                            }
                        }
                    }
                    val prompt = BiometricPrompt(activity, executor, callback)
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(context.getString(R.string.authenticate_fingerprint))
                        .setSubtitle(context.getString(R.string.fingerprint_required))
                        .setNegativeButtonText(context.getString(android.R.string.cancel))
                        .build()
                    prompt.authenticate(promptInfo)
                } else {
                    // Biometric not available, proceed without it
                    viewModel.onBiometricSuccess()
                }
            } else {
                // Not a FragmentActivity, proceed without biometric
                viewModel.onBiometricSuccess()
            }
        }
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
            val text = when (msg) {
                "auth_failed" -> context.getString(R.string.auth_failed)
                else -> msg
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
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
                            onCheckIn = { viewModel.requestCheckIn(worker.id, worker.fullName) },
                            onCheckOut = {
                                todayRecord?.let {
                                    viewModel.requestCheckOut(it.id, worker.fullName)
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
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
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
                        onClick = onCheckIn,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Fingerprint, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.check_in))
                    }
                }
                isPresent && !isCheckedOut -> {
                    OutlinedButton(
                        onClick = onCheckOut,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Fingerprint, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.check_out))
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
