package net.marllex.waselak.feature.manager.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import net.marllex.waselak.core.model.Worker
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun WorkerQrCodeScreen(
    onNavigateBack: () -> Unit,
) {
    val viewModel: WorkerQrCodeViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = SnackbarHostState()

    LaunchedEffect(uiState.error, uiState.successMessage) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Worker Badge") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> {
                LoadingIndicator()
            }
            uiState.error != null && uiState.worker == null -> {
                ErrorView(
                    message = uiState.error!!,
                    onRetry = { /* Retry handled by ViewModel init */ }
                )
            }
            uiState.worker != null -> {
                IosWorkerQrCodeContent(
                    worker = uiState.worker!!,
                    onRegenerate = viewModel::showRegenerateDialog,
                    modifier = Modifier.padding(padding),
                )
            }
        }

        if (uiState.showRegenerateDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissRegenerateDialog,
                title = { Text("Regenerate QR", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure? Old QR codes will stop working.") },
                confirmButton = {
                    Button(
                        onClick = viewModel::regenerateQrCode,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Regenerate")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissRegenerateDialog) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun IosWorkerQrCodeContent(
    worker: Worker,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cafeDark = MaterialTheme.colorScheme.onSurface
    val cafeGold = MaterialTheme.colorScheme.primary
    val cafeSoftBg = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(cafeSoftBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(min = 280.dp, max = 400.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Badge Card
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Branding Header
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = cafeDark,
                    ) {
                        Text(
                            text = "STAFF IDENTIFICATION",
                            modifier = Modifier.padding(vertical = 14.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            letterSpacing = 2.sp,
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Profile Placeholder
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, cafeGold),
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp),
                            tint = cafeDark.copy(alpha = 0.2f),
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // QR Code Placeholder
                    Surface(
                        modifier = Modifier.size(170.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.QrCode,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = cafeDark.copy(alpha = 0.3f),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "QR v${worker.qrCodeVersion}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Worker Details
                    Text(
                        text = worker.fullName.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = cafeDark,
                    )
                    Text(
                        text = worker.role,
                        style = MaterialTheme.typography.labelLarge,
                        color = cafeGold,
                    )

                    Spacer(modifier = Modifier.weight(1.5f))

                    Text(
                        text = "ID: ${worker.workerId}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Note about QR availability
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Use the Android app to view and download the full QR badge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Regenerate Button
            OutlinedButton(
                onClick = onRegenerate,
                modifier = Modifier.fillMaxWidth(0.9f).height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Regenerate QR")
            }
        }
    }
}
