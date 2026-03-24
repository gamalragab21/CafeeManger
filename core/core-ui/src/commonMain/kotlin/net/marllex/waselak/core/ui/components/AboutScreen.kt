package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class UpdateInfo(
    val hasUpdate: Boolean = false,
    val latestVersion: String = "",
    val updateStatus: String = "UP_TO_DATE",
    val releaseNotes: String? = null,
    val downloadUrl: String? = null,
    val downloadFilename: String? = null,
    val baseUrl: String? = null,
    val facebookUrl: String? = null,
    val landingPageUrl: String? = null,
    val instagramUrl: String? = null,
    val whatsappNumber: String? = null,
)

enum class DownloadState { IDLE, DOWNLOADING, DONE, ERROR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    appName: String,
    versionName: String,
    versionCode: Int,
    onCheckUpdate: suspend () -> UpdateInfo,
    onDownload: (suspend (url: String, onProgress: (Float) -> Unit) -> String?)? = null,
    onNavigateBack: (() -> Unit)? = null,
    vendorName: String? = null,
) {
    val scope = rememberCoroutineScope()
    val platformActions = net.marllex.waselak.core.ui.platform.rememberPlatformActions()
    var isChecking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isChecking = true
        try { updateInfo = onCheckUpdate() } catch (e: Exception) { error = e.message }
        isChecking = false
    }

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.about_and_updates),
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── App Identity Section ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Logo with shadow
                Card(
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.size(100.dp),
                ) {
                    Image(
                        painter = painterResource(Res.drawable.waslek_logo),
                        contentDescription = appName,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(4.dp))

                vendorName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Text(
                    "v$versionName ($versionCode)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Content Cards ──
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {

                // ── Update Status ──
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                        when {
                            isChecking -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(Res.string.checking_for_updates), style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            error != null -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(Res.string.update_check_failed), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    TextButton(onClick = {
                                        scope.launch {
                                            error = null; isChecking = true
                                            try { updateInfo = onCheckUpdate() } catch (e: Exception) { error = e.message }
                                            isChecking = false
                                        }
                                    }) { Text(stringResource(Res.string.retry)) }
                                }
                            }

                            updateInfo?.hasUpdate == true -> {
                                val info = updateInfo!!
                                val isMandatory = info.updateStatus == "MANDATORY"
                                val accentColor = if (isMandatory) Color(0xFFE53935) else Color(0xFF2196F3)

                                // Update banner
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isMandatory) Icons.Default.Warning else Icons.Default.NewReleases,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (isMandatory) stringResource(Res.string.mandatory_update_title)
                                            else stringResource(Res.string.update_available),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor,
                                        )
                                        Text("v${info.latestVersion}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                // Release notes
                                info.releaseNotes?.let { notes ->
                                    Spacer(Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            notes,
                                            modifier = Modifier.padding(12.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // Download with progress
                                var downloadState by remember { mutableStateOf(DownloadState.IDLE) }
                                var downloadProgress by remember { mutableFloatStateOf(0f) }
                                var downloadError by remember { mutableStateOf<String?>(null) }
                                var downloadedPath by remember { mutableStateOf<String?>(null) }

                                when (downloadState) {
                                    DownloadState.IDLE -> {
                                        val downloadLink = info.downloadUrl ?: ""
                                        Button(
                                            onClick = {
                                                if (downloadLink.isNotBlank()) {
                                                    val fullUrl = if (downloadLink.startsWith("http")) downloadLink
                                                        else "${info.baseUrl?.trimEnd('/')}/$downloadLink"
                                                    if (onDownload != null) {
                                                        downloadState = DownloadState.DOWNLOADING
                                                        downloadProgress = 0f
                                                        scope.launch {
                                                            try {
                                                                val path = onDownload(fullUrl) { downloadProgress = it }
                                                                downloadedPath = path
                                                                downloadState = DownloadState.DONE
                                                            } catch (e: Exception) {
                                                                downloadError = e.message
                                                                downloadState = DownloadState.ERROR
                                                            }
                                                        }
                                                    } else {
                                                        platformActions.openUrl(fullUrl)
                                                        downloadState = DownloadState.DONE
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(44.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                            shape = RoundedCornerShape(12.dp),
                                            enabled = downloadLink.isNotBlank(),
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(Res.string.download_update))
                                        }
                                    }
                                    DownloadState.DOWNLOADING -> {
                                        Column(Modifier.fillMaxWidth()) {
                                            LinearProgressIndicator(
                                                progress = { downloadProgress },
                                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                color = accentColor,
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                "${(downloadProgress * 100).toInt()}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = accentColor,
                                                modifier = Modifier.align(Alignment.End),
                                            )
                                        }
                                    }
                                    DownloadState.DONE -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                                            Spacer(Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(stringResource(Res.string.download_update) + " ✓", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
                                                downloadedPath?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                            }
                                        }
                                    }
                                    DownloadState.ERROR -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                            Spacer(Modifier.width(8.dp))
                                            Text(downloadError ?: "Error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                            TextButton(onClick = { downloadState = DownloadState.IDLE }) { Text(stringResource(Res.string.retry)) }
                                        }
                                    }
                                }
                            }

                            else -> {
                                // Up to date
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(Res.string.up_to_date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Color(0xFF4CAF50))
                                        Text(stringResource(Res.string.up_to_date_message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            isChecking = true
                                            try { updateInfo = onCheckUpdate() } catch (e: Exception) { error = e.message }
                                            isChecking = false
                                        }
                                    }) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Social Links ──
                val fb = updateInfo?.facebookUrl
                val lp = updateInfo?.landingPageUrl
                val ig = updateInfo?.instagramUrl
                val wa = updateInfo?.whatsappNumber
                val hasSocial = !fb.isNullOrBlank() || !lp.isNullOrBlank() || !ig.isNullOrBlank() || !wa.isNullOrBlank()

                if (hasSocial) {
                    Spacer(Modifier.height(12.dp))
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(Res.string.connect_with_us),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )

                            // Social buttons in a flow row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (!lp.isNullOrBlank()) {
                                    SocialChip(Icons.Default.Language, stringResource(Res.string.website), Color(0xFF1976D2), Modifier.weight(1f)) { platformActions.openUrl(lp) }
                                }
                                if (!fb.isNullOrBlank()) {
                                    SocialChip(Icons.Default.ThumbUp, "Facebook", Color(0xFF1877F2), Modifier.weight(1f)) { platformActions.openUrl(fb) }
                                }
                            }
                            if (!ig.isNullOrBlank() || !wa.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (!ig.isNullOrBlank()) {
                                        SocialChip(Icons.Default.CameraAlt, "Instagram", Color(0xFFE4405F), Modifier.weight(1f)) { platformActions.openUrl(ig) }
                                    }
                                    if (!wa.isNullOrBlank()) {
                                        SocialChip(Icons.Default.Chat, "WhatsApp", Color(0xFF25D366), Modifier.weight(1f)) {
                                            platformActions.openUrl("https://wa.me/${wa.replace("+", "").replace(" ", "")}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Footer ──
                Spacer(Modifier.height(32.dp))
                Text(
                    stringResource(Res.string.powered_by),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SocialChip(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun MandatoryUpdateScreen(
    appName: String,
    currentVersion: String,
    latestVersion: String,
    releaseNotes: String? = null,
    downloadUrl: String?,
    onDownload: ((String) -> Unit)? = null,
) {
    val platformActions = net.marllex.waselak.core.ui.platform.rememberPlatformActions()
    val handleDownload: (String) -> Unit = onDownload ?: { url -> platformActions.openUrl(url) }

    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(32.dp).widthIn(max = 400.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Logo
                Card(
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.size(80.dp),
                ) {
                    Image(
                        painter = painterResource(Res.drawable.waslek_logo),
                        contentDescription = appName,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit,
                    )
                }

                Spacer(Modifier.height(20.dp))

                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(40.dp))

                Spacer(Modifier.height(12.dp))

                Text(
                    stringResource(Res.string.mandatory_update_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE53935),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.mandatory_update_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "v$currentVersion → v$latestVersion",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                releaseNotes?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { handleDownload(downloadUrl ?: "") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !downloadUrl.isNullOrBlank(),
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.download_update), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
