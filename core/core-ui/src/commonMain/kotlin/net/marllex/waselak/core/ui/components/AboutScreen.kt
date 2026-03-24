package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import org.jetbrains.compose.resources.painterResource
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.waslek_logo
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Update check result — passed from the app layer (which has API access).
 */
data class UpdateInfo(
    val hasUpdate: Boolean = false,
    val latestVersion: String = "",
    val updateStatus: String = "UP_TO_DATE", // UP_TO_DATE, OPTIONAL, MANDATORY
    val releaseNotes: String? = null,
    val downloadUrl: String? = null,
)

/**
 * Reusable About & Updates screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    appName: String,
    versionName: String,
    versionCode: Int,
    onCheckUpdate: suspend () -> UpdateInfo,
    onDownload: (String) -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Auto-check on first load
    LaunchedEffect(Unit) {
        isChecking = true
        try {
            updateInfo = onCheckUpdate()
        } catch (e: Exception) {
            error = e.message
        }
        isChecking = false
    }

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = "About",
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            // App Icon
            // App Logo
            Image(
                painter = painterResource(Res.drawable.waslek_logo),
                contentDescription = appName,
                modifier = Modifier.size(100.dp),
            )

            Spacer(Modifier.height(16.dp))

            // App Name
            Text(
                appName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(8.dp))

            // Version
            Text(
                "v$versionName ($versionCode)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))

            // Update Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when {
                        isChecking -> {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Checking for updates...", style = MaterialTheme.typography.bodyMedium)
                        }

                        error != null -> {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Could not check for updates", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = {
                                scope.launch {
                                    error = null
                                    isChecking = true
                                    try { updateInfo = onCheckUpdate() } catch (e: Exception) { error = e.message }
                                    isChecking = false
                                }
                            }) { Text("Retry") }
                        }

                        updateInfo?.hasUpdate == true -> {
                            val info = updateInfo!!
                            val isMandatory = info.updateStatus == "MANDATORY"
                            val statusColor = if (isMandatory) Color(0xFFE53935) else Color(0xFFFF9800)

                            Icon(
                                if (isMandatory) Icons.Default.Warning else Icons.Default.NewReleases,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (isMandatory) "Update Required!" else "Update Available",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "v${info.latestVersion}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            // Release notes
                            info.releaseNotes?.let { notes ->
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Spacer(Modifier.height(20.dp))

                            // Download button
                            info.downloadUrl?.let { url ->
                                Button(
                                    onClick = { onDownload(url) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = statusColor),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isMandatory) "Update Now" else "Download Update")
                                }
                            }
                        }

                        else -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("You're up to date!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            Spacer(Modifier.height(4.dp))
                            Text("v$versionName is the latest version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Check again button
            if (!isChecking && error == null) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isChecking = true
                            try { updateInfo = onCheckUpdate() } catch (e: Exception) { error = e.message }
                            isChecking = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Check for Updates")
                }
            }

            Spacer(Modifier.height(32.dp))

            // App info
            Text("© 2024-2026 Waselak", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Full-screen mandatory update blocker.
 * Shows when the app version is below the minimum required.
 */
@Composable
fun MandatoryUpdateScreen(
    appName: String,
    currentVersion: String,
    latestVersion: String,
    releaseNotes: String? = null,
    downloadUrl: String?,
    onDownload: (String) -> Unit = {},
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("Update Required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text("$appName v$currentVersion is no longer supported.\nPlease update to v$latestVersion to continue.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

                releaseNotes?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(24.dp))

                downloadUrl?.let { url ->
                    Button(
                        onClick = { onDownload(url) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Update Now", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
