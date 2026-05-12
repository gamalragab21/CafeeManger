package net.marllex.waselak.feature.manager.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
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
            net.marllex.waselak.core.ui.components.WaselakTopAppBar(
                title = "Worker Badge",
                onNavigateBack = onNavigateBack,
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
                DesktopWorkerQrCodeContent(
                    worker = uiState.worker!!,
                    qrCodeImage = uiState.qrCodeImage,
                    onDownload = viewModel::downloadQrCode,
                    onRegenerate = viewModel::showRegenerateDialog,
                    modifier = Modifier.padding(padding),
                )
            }
        }

        // Regenerate confirmation dialog
        if (uiState.showRegenerateDialog) {
            DesktopRegenerateQrDialog(
                onConfirm = viewModel::regenerateQrCode,
                onDismiss = viewModel::dismissRegenerateDialog,
            )
        }
    }
}

@Composable
private fun DesktopWorkerQrCodeContent(
    worker: Worker,
    qrCodeImage: ImageBitmap?,
    onDownload: () -> Unit,
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
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
            ) {
                DesktopBadgeCardInternal(worker, qrCodeImage, cafeDark, cafeGold)
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(0.9f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Save QR Code
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = cafeDark),
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = cafeGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save QR Code", fontWeight = FontWeight.Bold)
                }

                // Regenerate
                OutlinedButton(
                    onClick = onRegenerate,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
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
}

@Composable
private fun DesktopBadgeCardInternal(
    worker: Worker,
    qrCodeImage: ImageBitmap?,
    cafeDark: Color,
    cafeGold: Color,
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

        // QR Code Section
        Surface(
            modifier = Modifier.size(170.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                if (qrCodeImage != null) {
                    Image(
                        bitmap = qrCodeImage,
                        contentDescription = "Worker QR Code",
                        modifier = Modifier.fillMaxSize(),
                        filterQuality = FilterQuality.None,
                    )
                } else {
                    CircularProgressIndicator(color = cafeGold, strokeWidth = 2.dp)
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

        // ID Subtext
        Text(
            text = "ID: ${worker.workerId}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DesktopRegenerateQrDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        },
        title = {
            Text(
                "Regenerate QR",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Are you sure you want to regenerate this QR code?",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "Old QR codes will stop working",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Regenerate QR", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
        },
        shape = RoundedCornerShape(24.dp),
    )
}
