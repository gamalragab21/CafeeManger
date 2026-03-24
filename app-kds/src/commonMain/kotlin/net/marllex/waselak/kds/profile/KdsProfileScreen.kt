package net.marllex.waselak.kds.profile
import net.marllex.waselak.core.ui.components.WaselakTopAppBar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.ui.components.SignOutButton
import net.marllex.waselak.core.ui.components.UploadLogsCard
import net.marllex.waselak.core.ui.platform.rememberPlatformActions
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KdsProfileScreen(
    viewModel: KdsProfileViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToAbout: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val apiClient = koinInject<WaselakApiClient>()
    val logScope = rememberCoroutineScope()
    var isUploadingLogs by remember { mutableStateOf(false) }
    val platformActions = rememberPlatformActions()

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.profile),
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // User Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Column {
                            Text(
                                uiState.userName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                uiState.userRole,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    HorizontalDivider()

                    // Info rows
                    ProfileInfoRow(
                        icon = Icons.Default.Phone,
                        label = stringResource(Res.string.phone),
                        value = uiState.userPhone,
                    )
                    uiState.userEmail?.let { email ->
                        ProfileInfoRow(
                            icon = Icons.Default.Email,
                            label = stringResource(Res.string.email),
                            value = email,
                        )
                    }
                    ProfileInfoRow(
                        icon = Icons.Default.Work,
                        label = stringResource(Res.string.role),
                        value = uiState.userRole,
                    )
                }
            }

            // Upload Logs Card
            UploadLogsCard(
                isUploading = isUploadingLogs,
                onUploadLogs = {
                    logScope.launch {
                        isUploadingLogs = true
                        try {
                            val bytes = AppLogger.readLogFileBytes()
                            if (bytes.isNotEmpty()) {
                                apiClient.uploadLogFile(bytes, AppLogger.getLogFileName())
                            }
                        } catch (_: Exception) {
                        } finally {
                            isUploadingLogs = false
                        }
                    }
                },
                onShareLogs = {
                    val bytes = AppLogger.readLogFileBytes()
                    if (bytes.isNotEmpty()) {
                        platformActions.shareFile(bytes, AppLogger.getLogFileName(), "text/plain")
                    }
                },
                onClearLogs = {
                    AppLogger.clearLogs()
                },
            )

            // About & Updates
            OutlinedButton(
                onClick = onNavigateToAbout,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.about_and_updates))
            }

            // Sign Out
            SignOutButton(
                onSignOut = {
                    viewModel.logout()
                    onLogout()
                },
            )
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
